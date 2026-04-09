"""Hava durumu endpoint'leri - Open-Meteo API proxy + Redis cache + bolgesel konum."""

import json
import logging
from datetime import datetime
from typing import Optional

from fastapi import APIRouter, Depends, Query, Request
from httpx import AsyncClient
from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.database import User, get_session
from app.models.schemas import ApiResponse
from app.routers.auth import get_current_user

logger = logging.getLogger("tonbil.weather")
router = APIRouter()

OPEN_METEO_BASE = "https://api.open-meteo.com/v1/forecast"
GEOCODING_BASE = "https://geocoding-api.open-meteo.com/v1/search"
CACHE_TTL = 900  # 15 dakika

# WMO hava kodu aciklamalari (Turkce)
WMO_CODES: dict[int, str] = {
    0: "Acik", 1: "Genellikle acik", 2: "Parcali bulutlu", 3: "Kapali",
    45: "Sisli", 48: "Buzlu sis",
    51: "Hafif ciseleme", 53: "Orta ciseleme", 55: "Yogun ciseleme",
    56: "Dondurucu hafif ciseleme", 57: "Dondurucu yogun ciseleme",
    61: "Hafif yagmur", 63: "Orta yagmur", 65: "Siddetli yagmur",
    66: "Dondurucu hafif yagmur", 67: "Dondurucu siddetli yagmur",
    71: "Hafif kar", 73: "Orta kar", 75: "Yogun kar", 77: "Kar taneleri",
    80: "Hafif sagnak", 81: "Orta sagnak", 82: "Siddetli sagnak",
    85: "Hafif kar sagnagi", 86: "Siddetli kar sagnagi",
    95: "Gok gurultusu", 96: "Dolu ile firtina", 99: "Siddetli dolu",
}

WMO_ICONS: dict[int, str] = {
    0: "clear-day", 1: "partly-cloudy-day", 2: "partly-cloudy-day", 3: "cloudy",
    45: "fog", 48: "fog",
    51: "drizzle", 53: "drizzle", 55: "drizzle", 56: "drizzle", 57: "drizzle",
    61: "rain", 63: "rain", 65: "rain", 66: "rain", 67: "rain",
    71: "snow", 73: "snow", 75: "snow", 77: "snow",
    80: "rain", 81: "rain", 82: "rain", 85: "snow", 86: "snow",
    95: "thunderstorm", 96: "thunderstorm", 99: "thunderstorm",
}


async def _get_cached(redis, key: str) -> dict | None:
    if not redis:
        return None
    try:
        data = await redis.get(key)
        if data:
            return json.loads(data)
    except Exception as e:
        logger.warning("Redis cache okuma hatasi: %s", e)
    return None


def _json_serializer(obj):
    if isinstance(obj, datetime):
        return obj.isoformat()
    raise TypeError(f"Object of type {type(obj)} is not JSON serializable")


async def _set_cached(redis, key: str, data: dict, ttl: int = CACHE_TTL):
    if not redis:
        return
    try:
        await redis.set(key, json.dumps(data, default=_json_serializer), ex=ttl)
    except Exception as e:
        logger.warning("Redis cache yazma hatasi: %s", e)


def _get_location(request: Request) -> tuple[float, float, str]:
    """Konum bilgisini al - oncelik: DB config > env vars."""
    lat = getattr(request.app.state, 'weather_lat', None) or settings.weather_lat
    lon = getattr(request.app.state, 'weather_lon', None) or settings.weather_lon
    city = getattr(request.app.state, 'weather_city', None) or settings.weather_city
    return lat, lon, city


# ==========================================================================
# KONUM ARAMA (Geocoding) — Ulke / Sehir / Ilce / Mahalle
# ==========================================================================

@router.get("/search-location")
async def search_location(
    q: str = Query(..., min_length=2, description="Aranacak konum (sehir, ilce, mahalle)"),
    country: Optional[str] = Query(None, description="Ulke kodu (TR, DE, US...)"),
    _user: User = Depends(get_current_user),
):
    """Konum ara — Open-Meteo Geocoding API.

    Ornek: /api/weather/search-location?q=Kadikoy&country=TR
    Donus: sehir listesi (isim, bolge, ulke, koordinat)
    """
    try:
        params = {
            "name": q,
            "count": 10,
            "language": "tr",
            "format": "json",
        }
        if country:
            params["country"] = country.upper()

        async with AsyncClient(timeout=10.0) as client:
            response = await client.get(GEOCODING_BASE, params=params)
            response.raise_for_status()
            data = response.json()

        results = []
        for r in data.get("results", []):
            # Tam adres olustur: mahalle/ilce, sehir, bolge, ulke
            parts = []
            if r.get("name"):
                parts.append(r["name"])
            if r.get("admin3") and r["admin3"] != r.get("name"):
                parts.append(r["admin3"])  # ilce
            if r.get("admin2") and r["admin2"] not in parts:
                parts.append(r["admin2"])  # sehir/il
            if r.get("admin1") and r["admin1"] not in parts:
                parts.append(r["admin1"])  # bolge

            results.append({
                "name": r.get("name", ""),
                "district": r.get("admin3", ""),       # ilce
                "city": r.get("admin2", "") or r.get("admin1", ""),  # sehir
                "region": r.get("admin1", ""),           # bolge
                "country": r.get("country", ""),
                "country_code": r.get("country_code", ""),
                "latitude": r.get("latitude"),
                "longitude": r.get("longitude"),
                "elevation": r.get("elevation"),
                "population": r.get("population"),
                "full_address": ", ".join(parts) + (f", {r.get('country', '')}" if r.get('country') else ""),
            })

        return ApiResponse(success=True, data=results)

    except Exception as e:
        logger.error("Konum arama hatasi: %s", e)
        return ApiResponse(success=False, error="Konum aranamadi")


@router.put("/location")
async def set_location(
    request: Request,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Hava durumu konumunu kaydet.

    Body: {latitude, longitude, city, district, country}
    """
    body = await request.json()
    lat = body.get("latitude")
    lon = body.get("longitude")
    city = body.get("city", "")
    district = body.get("district", "")
    country = body.get("country", "TR")

    if lat is None or lon is None:
        return ApiResponse(success=False, error="latitude ve longitude gerekli")

    # Tam konum ismi olustur
    location_name = district
    if city and city != district:
        location_name = f"{district}, {city}" if district else city
    if not location_name:
        location_name = f"{lat:.2f}, {lon:.2f}"

    # Veritabanina kaydet (heating_config tablosuna ekliyoruz)
    try:
        await session.execute(text("""
            UPDATE heating_config SET
                weather_lat = :lat,
                weather_lon = :lon,
                weather_city = :city,
                weather_district = :district,
                weather_country = :country
            WHERE id = 1
        """), {"lat": lat, "lon": lon, "city": city, "district": district, "country": country})
        await session.commit()
    except Exception:
        # Kolonlar yoksa ekle (mevcut DB icin migration)
        try:
            await session.execute(text("""
                ALTER TABLE heating_config
                ADD COLUMN IF NOT EXISTS weather_lat FLOAT,
                ADD COLUMN IF NOT EXISTS weather_lon FLOAT,
                ADD COLUMN IF NOT EXISTS weather_city VARCHAR DEFAULT '',
                ADD COLUMN IF NOT EXISTS weather_district VARCHAR DEFAULT '',
                ADD COLUMN IF NOT EXISTS weather_country VARCHAR DEFAULT 'TR'
            """))
            await session.commit()
            await session.execute(text("""
                UPDATE heating_config SET
                    weather_lat = :lat, weather_lon = :lon,
                    weather_city = :city, weather_district = :district,
                    weather_country = :country
                WHERE id = 1
            """), {"lat": lat, "lon": lon, "city": city, "district": district, "country": country})
            await session.commit()
        except Exception as e2:
            logger.error("Konum kaydetme hatasi: %s", e2)
            return ApiResponse(success=False, error="Konum kaydedilemedi")

    # Runtime state guncelle
    request.app.state.weather_lat = lat
    request.app.state.weather_lon = lon
    request.app.state.weather_city = location_name

    # Cache temizle
    redis = request.app.state.redis
    if redis:
        try:
            await redis.delete("weather:current", "weather:forecast")
        except Exception:
            pass

    logger.info("Hava durumu konumu guncellendi: %s (%.4f, %.4f)", location_name, lat, lon)

    return ApiResponse(success=True, data={
        "location": location_name,
        "latitude": lat,
        "longitude": lon,
        "message": f"Konum '{location_name}' olarak ayarlandi",
    })


@router.get("/location")
async def get_location(
    request: Request,
    _user: User = Depends(get_current_user),
):
    """Mevcut hava durumu konumunu getir."""
    lat, lon, city = _get_location(request)
    return ApiResponse(success=True, data={
        "latitude": lat,
        "longitude": lon,
        "city": city,
    })


# ==========================================================================
# ANLIK HAVA DURUMU
# ==========================================================================

@router.get("/current")
async def get_current_weather(
    request: Request,
    _user: User = Depends(get_current_user),
):
    """Anlik hava durumu bilgisi (nem, basinc, sicaklik, ruzgar)."""
    lat, lon, city = _get_location(request)
    redis = request.app.state.redis
    cache_key = f"weather:current:{lat:.2f}:{lon:.2f}"

    cached = await _get_cached(redis, cache_key)
    if cached:
        return ApiResponse(success=True, data=cached)

    try:
        async with AsyncClient(timeout=10.0) as client:
            response = await client.get(
                OPEN_METEO_BASE,
                params={
                    "latitude": lat,
                    "longitude": lon,
                    "current": "temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code,apparent_temperature,pressure_msl,wind_direction_10m",
                    "timezone": "auto",
                },
            )
            response.raise_for_status()
            api_data = response.json()

        current = api_data.get("current", {})
        weather_code = current.get("weather_code", 0)

        result = {
            "temperature": current.get("temperature_2m", 0),
            "humidity": current.get("relative_humidity_2m"),
            "pressure": current.get("pressure_msl"),
            "wind_speed": current.get("wind_speed_10m"),
            "wind_direction": current.get("wind_direction_10m"),
            "weather_code": weather_code,
            "description": WMO_CODES.get(weather_code, "Bilinmiyor"),
            "icon": WMO_ICONS.get(weather_code, "clear-day"),
            "feels_like": current.get("apparent_temperature"),
            "city": city,
        }

        await _set_cached(redis, cache_key, result)
        return ApiResponse(success=True, data=result)

    except Exception as e:
        logger.error("Hava durumu API hatasi: %s", e)
        return ApiResponse(success=False, error="Hava durumu bilgisi alinamadi")


# ==========================================================================
# 72 SAATLIK TAHMIN
# ==========================================================================

@router.get("/forecast")
async def get_weather_forecast(
    request: Request,
    _user: User = Depends(get_current_user),
):
    """72 saatlik saatlik tahmin (sicaklik, nem, basinc, ruzgar)."""
    lat, lon, city = _get_location(request)
    redis = request.app.state.redis
    cache_key = f"weather:forecast:{lat:.2f}:{lon:.2f}"

    cached = await _get_cached(redis, cache_key)
    if cached:
        return ApiResponse(success=True, data=cached)

    try:
        async with AsyncClient(timeout=10.0) as client:
            response = await client.get(
                OPEN_METEO_BASE,
                params={
                    "latitude": lat,
                    "longitude": lon,
                    "hourly": "temperature_2m,relative_humidity_2m,weather_code,pressure_msl,wind_speed_10m,apparent_temperature",
                    "forecast_hours": 72,
                    "timezone": "auto",
                },
            )
            response.raise_for_status()
            api_data = response.json()

        hourly = api_data.get("hourly", {})
        times = hourly.get("time", [])
        temps = hourly.get("temperature_2m", [])
        humidities = hourly.get("relative_humidity_2m", [])
        codes = hourly.get("weather_code", [])
        pressures = hourly.get("pressure_msl", [])
        winds = hourly.get("wind_speed_10m", [])
        feels = hourly.get("apparent_temperature", [])

        forecast_points = []
        for i in range(len(times)):
            forecast_points.append({
                "time": times[i],
                "temperature": temps[i] if i < len(temps) else 0,
                "humidity": humidities[i] if i < len(humidities) else None,
                "pressure": pressures[i] if i < len(pressures) else None,
                "wind_speed": winds[i] if i < len(winds) else None,
                "feels_like": feels[i] if i < len(feels) else None,
                "weather_code": codes[i] if i < len(codes) else None,
                "description": WMO_CODES.get(codes[i], "Bilinmiyor") if i < len(codes) else None,
            })

        result = {
            "city": city,
            "latitude": lat,
            "longitude": lon,
            "hourly": forecast_points,
        }

        await _set_cached(redis, cache_key, result, ttl=CACHE_TTL)
        return ApiResponse(success=True, data=result)

    except Exception as e:
        logger.error("Hava tahmini API hatasi: %s", e)
        return ApiResponse(success=False, error="Hava tahmini alinamadi")
