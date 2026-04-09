"""Enerji hesaplama ve akilli uyari endpoint'leri.

Kombi calisma suresinden gaz tuketimi, maliyet, verimlilik hesaplar.
Runtime verisi InfluxDB'den, konfigurayon PostgreSQL'den okunur.
"""

import asyncio
import logging
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from pydantic import BaseModel, Field
from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.algorithms.alerts import (
    AlertEngine,
    BoilerStatus,
    HeatingConfig as AlertHeatingConfig,
    SensorData,
    WeatherData,
    alert_engine,
)
from app.algorithms.energy import energy_calculator
from app.config import settings
from app.models.database import HeatingConfig, Room, User, get_session
from app.models.schemas import ApiResponse
from app.routers.auth import get_current_user

logger = logging.getLogger("tonbil.energy")
router = APIRouter()


# ---------------------------------------------------------------------------
# Pydantic modelleri
# ---------------------------------------------------------------------------


class FlowTempUpdate(BaseModel):
    """Radyator su sicakligi guncelleme."""

    flow_temp: float = Field(ge=30.0, le=85.0, description="Akis sicakligi (C)")


class EstimateRequest(BaseModel):
    """Isitma tahmini parametreleri."""

    current_temp: float = Field(ge=-10.0, le=40.0)
    target_temp: float = Field(ge=5.0, le=35.0)
    outdoor_temp: float = Field(ge=-30.0, le=50.0)
    flow_temp: float = Field(default=60.0, ge=30.0, le=85.0)
    floor_area: float = Field(default=100.0, ge=10.0, le=500.0)


# ---------------------------------------------------------------------------
# Yardimci fonksiyonlar
# ---------------------------------------------------------------------------


async def _get_config(session: AsyncSession) -> HeatingConfig:
    """Isitma konfigurasyonunu getir."""
    result = await session.execute(select(HeatingConfig).limit(1))
    config = result.scalar_one_or_none()
    if not config:
        raise HTTPException(status_code=404, detail="Isitma konfigurasyonu bulunamadi")
    return config


def _get_influx_query_api(request: Request):
    """InfluxDB query API'sini al."""
    mqtt_service = request.app.state.mqtt
    if mqtt_service is None:
        return None
    try:
        return mqtt_service._influx.query_api()
    except Exception as e:
        logger.warning("InfluxDB query API alinamadi: %s", e)
        return None


async def _query_daily_runtime(
    query_api, bucket: str, date_str: str
) -> list[float]:
    """InfluxDB'den belirli bir gun icin saatlik calisma surelerini sorgula.

    duration_sec field'ini (OFF kayitlarinda) saatlik toplayarak hesaplar.

    Returns:
        24 elemanli liste, her eleman o saatteki runtime (saniye).
    """
    hourly_runtimes = [0.0] * 24

    if query_api is None:
        return hourly_runtimes

    # duration_sec OFF kayitlarinda yaziliyor — saatlik topla
    flux_query = f'''
        from(bucket: "{bucket}")
          |> range(start: {date_str}T00:00:00Z, stop: {date_str}T23:59:59Z)
          |> filter(fn: (r) => r["_measurement"] == "relay_state")
          |> filter(fn: (r) => r["_field"] == "duration_sec")
          |> aggregateWindow(every: 1h, fn: sum, createEmpty: true)
          |> fill(value: 0.0)
    '''

    try:
        loop = asyncio.get_running_loop()
        tables = await loop.run_in_executor(None, lambda: query_api.query(flux_query))
        for table in tables:
            for record in table.records:
                hour = record.get_time().hour
                value = record.get_value()
                if value is not None and 0 <= hour < 24:
                    hourly_runtimes[hour] = min(float(value), 3600.0)
    except Exception as e:
        logger.warning("InfluxDB runtime sorgusu basarisiz: %s", e)

    return hourly_runtimes


async def _query_total_runtime_today(query_api, bucket: str) -> float:
    """Bugunun toplam calisma suresini sorgula (saniye)."""
    if query_api is None:
        return 0.0

    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    # duration_sec, OFF kayitlarinda yaziliyor (state tag = "off")
    flux_query = f'''
        from(bucket: "{bucket}")
          |> range(start: {today}T00:00:00Z)
          |> filter(fn: (r) => r["_measurement"] == "relay_state")
          |> filter(fn: (r) => r["_field"] == "duration_sec")
          |> sum()
    '''

    try:
        loop = asyncio.get_running_loop()
        tables = await loop.run_in_executor(None, lambda: query_api.query(flux_query))
        for table in tables:
            for record in table.records:
                value = record.get_value()
                if value is not None:
                    return float(value)
    except Exception as e:
        logger.warning("InfluxDB toplam runtime sorgusu basarisiz: %s", e)

    return 0.0


async def _query_relay_cycles_today(
    query_api, bucket: str
) -> tuple[int, float, float]:
    """Bugunun relay dongu sayisini ve ortalama suresini sorgula."""
    if query_api is None:
        return (0, 0.0, 0.0)

    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    # ON kayitlarini say (state tag = "on")
    flux_query = f'''
        from(bucket: "{bucket}")
          |> range(start: {today}T00:00:00Z)
          |> filter(fn: (r) => r["_measurement"] == "relay_state")
          |> filter(fn: (r) => r["_field"] == "state")
          |> filter(fn: (r) => r["state"] == "on")
          |> count()
    '''

    cycle_count = 0
    try:
        loop = asyncio.get_running_loop()
        tables = await loop.run_in_executor(None, lambda: query_api.query(flux_query))
        for table in tables:
            for record in table.records:
                value = record.get_value()
                if value is not None:
                    cycle_count = int(value)
    except Exception as e:
        logger.warning("InfluxDB dongu sayisi sorgusu basarisiz: %s", e)

    avg_duration = 0.0
    if cycle_count > 0:
        total_runtime = await _query_total_runtime_today(query_api, bucket)
        avg_duration = total_runtime / cycle_count

    return (cycle_count, avg_duration, avg_duration)


async def _get_latest_sensor_data(
    query_api, bucket: str, session: AsyncSession
) -> list[SensorData]:
    """Son sensor verilerini InfluxDB + PostgreSQL'den al."""
    sensors: list[SensorData] = []

    # Oda isimlerini al
    rooms_result = await session.execute(select(Room))
    rooms = {r.id: r.name for r in rooms_result.scalars().all()}

    if query_api is None:
        return sensors

    flux_query = f'''
        from(bucket: "{bucket}")
          |> range(start: -30m)
          |> filter(fn: (r) => r["_measurement"] == "sensor_data")
          |> filter(fn: (r) => r["_field"] == "temperature" or r["_field"] == "humidity")
          |> last()
          |> pivot(rowKey: ["_time", "device_id"], columnKey: ["_field"], valueColumn: "_value")
    '''

    try:
        loop = asyncio.get_running_loop()
        tables = await loop.run_in_executor(None, lambda: query_api.query(flux_query))
        for table in tables:
            for record in table.records:
                device_id = record.values.get("device_id", "")
                temp = record.values.get("temperature")
                hum = record.values.get("humidity")

                from app.models.database import Device

                dev_result = await session.execute(
                    select(Device).where(Device.device_id == device_id)
                )
                device = dev_result.scalar_one_or_none()
                room_name = "Bilinmeyen"
                last_seen = None
                if device:
                    room_name = rooms.get(device.room_id, device.name or device_id)
                    last_seen = device.last_seen

                sensors.append(SensorData(
                    room_name=room_name,
                    temperature=float(temp) if temp is not None else None,
                    humidity=float(hum) if hum is not None else None,
                    last_seen=last_seen,
                ))
    except Exception as e:
        logger.warning("InfluxDB sensor sorgusu basarisiz: %s", e)

    # Fallback: MQTT service'deki son bilinen sicakliklar
    if not sensors:
        from app.services.heating import heating_service

        for device_id, temp in heating_service._temps.items():
            from app.models.database import Device

            dev_result = await session.execute(
                select(Device).where(Device.device_id == device_id)
            )
            device = dev_result.scalar_one_or_none()
            room_name = "Bilinmeyen"
            if device:
                room_name = rooms.get(device.room_id, device.name or device_id)

            sensors.append(SensorData(
                room_name=room_name,
                temperature=temp,
            ))

    return sensors


async def _get_weather_data(request: Request) -> WeatherData:
    """Redis cache'den veya weather router'dan hava durumu al."""
    redis = getattr(request.app.state, "redis", None)
    weather = WeatherData()

    if redis:
        import json

        try:
            lat = getattr(request.app.state, "weather_lat", settings.weather_lat)
            lon = getattr(request.app.state, "weather_lon", settings.weather_lon)
            cache_key = f"weather:current:{lat:.2f}:{lon:.2f}"
            cached = await redis.get(cache_key)
            if cached:
                data = json.loads(cached)
                weather = WeatherData(
                    outdoor_temp=data.get("temperature", 10.0),
                    outdoor_humidity=data.get("humidity", 50.0),
                    description=data.get("description", ""),
                )
        except Exception as e:
            logger.warning("Weather cache okunamadi: %s", e)

        # Forecast'ten min/max al
        try:
            forecast_key = f"weather:forecast:{lat:.2f}:{lon:.2f}"
            forecast_cached = await redis.get(forecast_key)
            if forecast_cached:
                forecast_data = json.loads(forecast_cached)
                hourly = forecast_data.get("hourly", [])
                # Yarinki tahmin (24-48 saat arasi)
                tomorrow_temps = [
                    h.get("temperature", 0)
                    for h in hourly[24:48]
                    if h.get("temperature") is not None
                ]
                if tomorrow_temps:
                    weather = WeatherData(
                        outdoor_temp=weather.outdoor_temp,
                        outdoor_humidity=weather.outdoor_humidity,
                        description=weather.description,
                        forecast_min_temp=min(tomorrow_temps),
                        forecast_max_temp=max(tomorrow_temps),
                    )
        except Exception:
            pass

    return weather


# ---------------------------------------------------------------------------
# Endpoint'ler
# ---------------------------------------------------------------------------


@router.get("/current")
async def get_current_energy(
    request: Request,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Anlik enerji durumu.

    Bugunun calisma suresi, gaz tuketimi, maliyet ve verimlilik bilgisi.
    """
    config = await _get_config(session)
    flow_temp = getattr(config, "flow_temp", 60.0) or 60.0
    gas_price = config.gas_price_per_m3 or energy_calculator.DEFAULT_GAS_PRICE

    query_api = _get_influx_query_api(request)
    total_runtime_sec = await _query_total_runtime_today(
        query_api, settings.influxdb_bucket
    )

    # MQTT servisinden anlik relay durumu
    mqtt_service = request.app.state.mqtt
    relay_on = mqtt_service.relay_state if mqtt_service else False
    relay_on_since = mqtt_service._last_relay_change if mqtt_service else None

    # Eger relay aciksa, son degisiklikten bu yana gecen sureyi ekle
    if relay_on and relay_on_since:
        additional_sec = (
            datetime.now(timezone.utc) - relay_on_since
        ).total_seconds()
        total_runtime_sec += additional_sec

    stats = energy_calculator.calculate_hourly_stats(
        runtime_seconds=total_runtime_sec,
        flow_temp=flow_temp,
        gas_price=gas_price,
    )

    analysis = energy_calculator.analyze_efficiency(flow_temp)

    return ApiResponse(
        success=True,
        data={
            "today": {
                "runtime_minutes": stats.runtime_minutes,
                "gas_m3": stats.gas_m3,
                "thermal_kwh": stats.thermal_kwh,
                "thermal_kcal": stats.thermal_kcal,
                "cost_tl": stats.cost_tl,
            },
            "relay": {
                "state": relay_on,
                "on_since": relay_on_since.isoformat() if relay_on_since else None,
            },
            "flow_temp": flow_temp,
            "efficiency": {
                "current_pct": analysis.current_efficiency_pct,
                "is_condensing": analysis.is_condensing,
                "optimal_flow_temp": analysis.optimal_flow_temp,
                "potential_savings_pct": analysis.potential_savings_pct,
            },
            "gas_price_per_m3": gas_price,
        },
    )


@router.get("/daily")
async def get_daily_energy(
    request: Request,
    days: int = Query(default=7, ge=1, le=90, description="Son kac gun"),
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Gunluk enerji ozeti.

    Her gun icin calisma suresi, gaz tuketimi, maliyet ve verimlilik.
    """
    config = await _get_config(session)
    flow_temp = getattr(config, "flow_temp", 60.0) or 60.0
    gas_price = config.gas_price_per_m3 or energy_calculator.DEFAULT_GAS_PRICE

    query_api = _get_influx_query_api(request)
    daily_summaries = []

    for i in range(days):
        target_date = datetime.now(timezone.utc) - timedelta(days=i)
        date_str = target_date.strftime("%Y-%m-%d")

        hourly_runtimes = await _query_daily_runtime(
            query_api, settings.influxdb_bucket, date_str
        )

        summary = energy_calculator.calculate_daily_summary(
            hourly_runtimes=hourly_runtimes,
            flow_temp=flow_temp,
            gas_price=gas_price,
            date_str=date_str,
        )

        daily_summaries.append({
            "date": summary.date,
            "runtime_minutes": summary.total_runtime_minutes,
            "gas_m3": summary.total_gas_m3,
            "thermal_kwh": summary.total_thermal_kwh,
            "thermal_kcal": summary.total_thermal_kcal,
            "cost_tl": summary.total_cost_tl,
            "efficiency_pct": summary.avg_efficiency_pct,
            "duty_cycle_pct": summary.duty_cycle_pct,
        })

    # Toplam ozet
    total_gas = sum(d["gas_m3"] for d in daily_summaries)
    total_cost = sum(d["cost_tl"] for d in daily_summaries)
    total_runtime = sum(d["runtime_minutes"] for d in daily_summaries)
    avg_daily_cost = total_cost / days if days > 0 else 0

    return ApiResponse(
        success=True,
        data={
            "days": daily_summaries,
            "summary": {
                "total_gas_m3": round(total_gas, 3),
                "total_cost_tl": round(total_cost, 2),
                "total_runtime_minutes": round(total_runtime, 1),
                "avg_daily_cost_tl": round(avg_daily_cost, 2),
                "period_days": days,
            },
        },
    )


@router.get("/monthly")
async def get_monthly_energy(
    request: Request,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Aylik enerji ozeti.

    Son 30 gunun verisini gunluk toplar ve aylik ozet cikarir.
    """
    config = await _get_config(session)
    flow_temp = getattr(config, "flow_temp", 60.0) or 60.0
    gas_price = config.gas_price_per_m3 or energy_calculator.DEFAULT_GAS_PRICE

    query_api = _get_influx_query_api(request)

    total_gas = 0.0
    total_cost = 0.0
    total_runtime_sec = 0.0
    daily_costs: list[float] = []

    for i in range(30):
        target_date = datetime.now(timezone.utc) - timedelta(days=i)
        date_str = target_date.strftime("%Y-%m-%d")

        hourly_runtimes = await _query_daily_runtime(
            query_api, settings.influxdb_bucket, date_str
        )

        day_runtime_sec = sum(hourly_runtimes)
        day_gas = energy_calculator.calculate_gas_consumption(
            day_runtime_sec / 3600.0, flow_temp
        )
        day_cost = energy_calculator.calculate_cost(day_gas, gas_price)

        total_runtime_sec += day_runtime_sec
        total_gas += day_gas
        total_cost += day_cost
        daily_costs.append(day_cost)

    total_kwh, total_kcal = energy_calculator.calculate_thermal_output(
        total_gas, flow_temp
    )

    return ApiResponse(
        success=True,
        data={
            "period": "son_30_gun",
            "total_runtime_hours": round(total_runtime_sec / 3600.0, 1),
            "total_gas_m3": round(total_gas, 2),
            "total_thermal_kwh": round(total_kwh, 1),
            "total_thermal_kcal": round(total_kcal, 0),
            "total_cost_tl": round(total_cost, 2),
            "avg_daily_cost_tl": round(total_cost / 30, 2),
            "max_daily_cost_tl": round(max(daily_costs) if daily_costs else 0, 2),
            "min_daily_cost_tl": round(min(daily_costs) if daily_costs else 0, 2),
            "efficiency_pct": round(
                energy_calculator.interpolate_efficiency(flow_temp) * 100, 1
            ),
            "flow_temp": flow_temp,
            "gas_price_per_m3": gas_price,
        },
    )


@router.get("/estimate")
async def get_heating_estimate(
    request: Request,
    current_temp: float = Query(ge=-10.0, le=40.0, description="Mevcut sicaklik"),
    target_temp: float = Query(ge=5.0, le=35.0, description="Hedef sicaklik"),
    outdoor_temp: float = Query(ge=-30.0, le=50.0, description="Dis sicaklik"),
    flow_temp: float = Query(default=60.0, ge=30.0, le=85.0, description="Akis sicakligi"),
    floor_area: float = Query(default=100.0, ge=10.0, le=500.0, description="Bina alani m2"),
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Isitma suresi ve maliyet tahmini.

    Mevcut sicakliktan hedef sicakliga ulasmak icin gereken sure,
    gaz tuketimi ve maliyeti tahmin eder.
    """
    config = await _get_config(session)
    gas_price = config.gas_price_per_m3 or energy_calculator.DEFAULT_GAS_PRICE

    estimate = energy_calculator.estimate_heating_duration(
        current_temp=current_temp,
        target_temp=target_temp,
        outdoor_temp=outdoor_temp,
        floor_area=floor_area,
        flow_temp=flow_temp,
    )

    # Farkli akis sicakliklarinin karsilastirmasi
    comparisons = energy_calculator.compare_flow_temps(
        runtime_hours=estimate.estimated_minutes / 60.0 if estimate.estimated_minutes < float("inf") else 1.0,
        flow_temps=[45.0, 50.0, 55.0, 60.0, 65.0, 70.0],
        gas_price=gas_price,
    )

    return ApiResponse(
        success=True,
        data={
            "estimate": {
                "minutes": estimate.estimated_minutes,
                "gas_m3": estimate.estimated_gas_m3,
                "cost_tl": estimate.estimated_cost_tl,
                "kwh": estimate.estimated_kwh,
                "confidence": estimate.confidence,
            },
            "params": {
                "current_temp": current_temp,
                "target_temp": target_temp,
                "outdoor_temp": outdoor_temp,
                "flow_temp": flow_temp,
                "floor_area": floor_area,
            },
            "flow_temp_comparison": comparisons,
        },
    )


@router.get("/efficiency")
async def get_efficiency_analysis(
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Verimlilik analizi.

    Mevcut akis sicakliginda verimlilik, optimal sicaklik onerisi
    ve tasarruf potansiyeli.
    """
    config = await _get_config(session)
    flow_temp = getattr(config, "flow_temp", 60.0) or 60.0

    analysis = energy_calculator.analyze_efficiency(flow_temp)

    # Tum akis sicakliklari icin verim tablosu
    efficiency_table = []
    for temp in sorted(energy_calculator.EFFICIENCY.keys()):
        eff = energy_calculator.EFFICIENCY[temp]
        gas_rate = energy_calculator.GAS_FLOW_RATES[temp]
        efficiency_table.append({
            "flow_temp": temp,
            "efficiency_pct": round(eff * 100, 1),
            "gas_rate_m3h": gas_rate,
            "is_condensing": temp <= 55,
            "is_current": abs(temp - flow_temp) < 3,
        })

    return ApiResponse(
        success=True,
        data={
            "current": {
                "flow_temp": analysis.current_flow_temp,
                "efficiency_pct": analysis.current_efficiency_pct,
                "is_condensing": analysis.is_condensing,
            },
            "optimal": {
                "flow_temp": analysis.optimal_flow_temp,
                "efficiency_pct": analysis.optimal_efficiency_pct,
            },
            "potential_savings_pct": analysis.potential_savings_pct,
            "recommendation": analysis.recommendation,
            "efficiency_table": efficiency_table,
            "boiler": {
                "brand": energy_calculator.BOILER_BRAND,
                "model": energy_calculator.BOILER_MODEL,
                "max_kw": energy_calculator.BOILER_MAX_KW,
                "min_kw": energy_calculator.BOILER_MIN_KW,
            },
        },
    )


@router.get("/alerts")
async def get_alerts(
    request: Request,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Akilli uyarilar.

    Sicaklik, nem, enerji, kombi ve tahmin uyarilarini dondurur.
    """
    config = await _get_config(session)
    flow_temp = getattr(config, "flow_temp", 60.0) or 60.0
    gas_price = config.gas_price_per_m3 or energy_calculator.DEFAULT_GAS_PRICE

    query_api = _get_influx_query_api(request)
    bucket = settings.influxdb_bucket

    # Sensor verileri
    sensors = await _get_latest_sensor_data(query_api, bucket, session)

    # Hava durumu
    weather = await _get_weather_data(request)

    # Kombi durumu
    mqtt_service = request.app.state.mqtt
    relay_on = mqtt_service.relay_state if mqtt_service else False
    relay_on_since = mqtt_service._last_relay_change if mqtt_service else None

    total_runtime = await _query_total_runtime_today(query_api, bucket)
    cycles, last_dur, avg_dur = await _query_relay_cycles_today(query_api, bucket)

    boiler = BoilerStatus(
        relay_on=relay_on,
        relay_on_since=relay_on_since,
        total_runtime_today_sec=total_runtime,
        cycle_count_today=cycles,
        last_cycle_duration_sec=last_dur,
        avg_cycle_duration_sec=avg_dur,
    )

    # Konfigurayon
    alert_config = AlertHeatingConfig(
        target_temp=config.target_temp,
        flow_temp=flow_temp,
        gas_price=gas_price,
        floor_area=config.floor_area_m2 or 100.0,
        mode=config.mode,
    )

    alerts = alert_engine.evaluate(sensors, alert_config, weather, boiler)

    return ApiResponse(
        success=True,
        data={
            "alerts": alerts,
            "count": len(alerts),
            "critical_count": sum(1 for a in alerts if a["severity"] == "critical"),
            "warning_count": sum(1 for a in alerts if a["severity"] == "warning"),
        },
    )


@router.put("/flow-temp")
async def update_flow_temp(
    body: FlowTempUpdate,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Radyator su sicakligini kaydet.

    Not: Bu deger fiziksel olarak kombi panelinden ayarlanir.
    Burada sadece hesaplamalar icin kaydedilir.
    """
    config = await _get_config(session)

    old_flow_temp = getattr(config, "flow_temp", 60.0) or 60.0

    # flow_temp kolonunu guncelle
    try:
        await session.execute(
            text(
                "UPDATE heating_config SET flow_temp = :flow_temp WHERE id = :id"
            ),
            {"flow_temp": body.flow_temp, "id": config.id},
        )

        # Audit log
        from app.models.database import AuditLog

        session.add(
            AuditLog(
                action="heating_config.flow_temp.update",
                old_value=str(old_flow_temp),
                new_value=str(body.flow_temp),
            )
        )

        await session.commit()
    except Exception:
        # Kolon yoksa ekle
        try:
            await session.rollback()
            await session.execute(
                text(
                    "ALTER TABLE heating_config ADD COLUMN IF NOT EXISTS "
                    "flow_temp FLOAT DEFAULT 60.0"
                )
            )
            await session.execute(
                text(
                    "UPDATE heating_config SET flow_temp = :flow_temp WHERE id = :id"
                ),
                {"flow_temp": body.flow_temp, "id": config.id},
            )
            await session.commit()
        except Exception as e:
            logger.error("Flow temp guncelleme hatasi: %s", e)
            raise HTTPException(
                status_code=500, detail="Flow temp guncellenemedi"
            ) from e

    # Verimlilik analizi
    analysis = energy_calculator.analyze_efficiency(body.flow_temp)

    # Eski ve yeni maliyet karsilastirmasi (ornek: gunluk 8 saat calisma)
    old_gas = energy_calculator.calculate_gas_consumption(8.0, old_flow_temp)
    new_gas = energy_calculator.calculate_gas_consumption(8.0, body.flow_temp)
    gas_price = config.gas_price_per_m3 or energy_calculator.DEFAULT_GAS_PRICE
    old_cost = energy_calculator.calculate_cost(old_gas, gas_price)
    new_cost = energy_calculator.calculate_cost(new_gas, gas_price)

    logger.info(
        "Flow temp guncellendi: %.0f -> %.0f C", old_flow_temp, body.flow_temp
    )

    return ApiResponse(
        success=True,
        data={
            "flow_temp": body.flow_temp,
            "efficiency_pct": analysis.current_efficiency_pct,
            "is_condensing": analysis.is_condensing,
            "recommendation": analysis.recommendation,
            "cost_comparison": {
                "old_flow_temp": old_flow_temp,
                "new_flow_temp": body.flow_temp,
                "old_daily_cost_8h": old_cost,
                "new_daily_cost_8h": new_cost,
                "savings_tl": round(old_cost - new_cost, 2),
                "savings_pct": round(
                    ((old_cost - new_cost) / old_cost * 100) if old_cost > 0 else 0,
                    1,
                ),
            },
        },
    )


@router.get("/predict")
async def predict_temperature(
    request: Request,
    hours: float = Query(default=4.0, ge=0.5, le=24.0, description="Kac saat sonra"),
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Gelecek sicaklik tahmini.

    Mevcut sicaklik, dis hava ve kombi durumuna gore t saat
    sonraki ic sicakligi tahmin eder.
    """
    config = await _get_config(session)
    flow_temp = getattr(config, "flow_temp", 60.0) or 60.0
    floor_area = config.floor_area_m2 or 100.0

    # Mevcut ic sicaklik
    query_api = _get_influx_query_api(request)
    sensors = await _get_latest_sensor_data(
        query_api, settings.influxdb_bucket, session
    )
    valid_temps = [s.temperature for s in sensors if s.temperature is not None]
    if not valid_temps:
        return ApiResponse(
            success=False, error="Sensor verisi bulunamadi, tahmin yapilamadi"
        )

    avg_indoor = sum(valid_temps) / len(valid_temps)

    # Dis sicaklik
    weather = await _get_weather_data(request)
    outdoor = weather.outdoor_temp

    # Kombi durumu
    mqtt_service = request.app.state.mqtt
    relay_on = mqtt_service.relay_state if mqtt_service else False

    # Tahmin
    predicted = energy_calculator.predict_temperature(
        indoor_temp=avg_indoor,
        outdoor_temp=outdoor,
        hours_ahead=hours,
        heating_on=relay_on,
        flow_temp=flow_temp,
        floor_area=floor_area,
    )

    # Soguma hizi
    cooling_rate = energy_calculator.estimate_cooling_rate(avg_indoor, outdoor)

    # Saatlik tahmin grafigu icin veri noktalari
    prediction_points = []
    for h in range(int(hours * 2) + 1):
        t = h * 0.5
        temp_on = energy_calculator.predict_temperature(
            avg_indoor, outdoor, t, True, flow_temp, floor_area
        )
        temp_off = energy_calculator.predict_temperature(
            avg_indoor, outdoor, t, False, flow_temp, floor_area
        )
        prediction_points.append({
            "hours": t,
            "heating_on": temp_on,
            "heating_off": temp_off,
        })

    return ApiResponse(
        success=True,
        data={
            "current_temp": round(avg_indoor, 1),
            "outdoor_temp": outdoor,
            "predicted_temp": predicted,
            "hours_ahead": hours,
            "heating_on": relay_on,
            "cooling_rate_c_per_hour": cooling_rate,
            "prediction_curve": prediction_points,
        },
    )


@router.get("/thermal-analysis")
async def get_thermal_analysis(
    request: Request,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Kapsamli termal analiz.

    Ic/dis sicaklik karsilastirmasi, isi kaybi modeli, enerji kazanim/kayip
    dengesi, nem konforu ve oneri sistemi.
    """
    config = await _get_config(session)
    flow_temp = getattr(config, "flow_temp", 60.0) or 60.0
    gas_price = config.gas_price_per_m3 or energy_calculator.DEFAULT_GAS_PRICE
    floor_area = config.floor_area_m2 or 100.0
    target_temp = config.target_temp

    query_api = _get_influx_query_api(request)
    bucket = settings.influxdb_bucket

    # --- Sensor verileri ---
    sensors = await _get_latest_sensor_data(query_api, bucket, session)
    valid_temps = [s.temperature for s in sensors if s.temperature is not None]
    valid_hums = [s.humidity for s in sensors if s.humidity is not None]

    avg_indoor = sum(valid_temps) / len(valid_temps) if valid_temps else None
    avg_humidity = sum(valid_hums) / len(valid_hums) if valid_hums else None

    # Oda bazli detay
    room_details = []
    for s in sensors:
        room_details.append({
            "room": s.room_name,
            "temperature": round(s.temperature, 1) if s.temperature else None,
            "humidity": round(s.humidity, 1) if s.humidity else None,
        })

    # --- Hava durumu ---
    weather = await _get_weather_data(request)
    outdoor_temp = weather.outdoor_temp
    outdoor_humidity = weather.outdoor_humidity

    # --- Relay durumu ---
    mqtt_service = request.app.state.mqtt
    relay_on = mqtt_service.relay_state if mqtt_service else False
    total_runtime = await _query_total_runtime_today(query_api, bucket)

    # --- Hesaplamalar ---

    # 1. Isi kaybi modeli
    heat_loss_coeff_w_per_c = floor_area * 1.8  # W/°C
    if avg_indoor is not None:
        delta_t = avg_indoor - outdoor_temp
        heat_loss_watts = heat_loss_coeff_w_per_c * max(delta_t, 0)
        heat_loss_kw = heat_loss_watts / 1000.0
        heat_loss_kcal_per_hour = heat_loss_kw * 860.0
    else:
        delta_t = 0
        heat_loss_watts = 0
        heat_loss_kw = 0
        heat_loss_kcal_per_hour = 0

    # 2. Kombi isi kazanimi
    gas_rate = energy_calculator.interpolate_gas_rate(flow_temp)
    efficiency = energy_calculator.interpolate_efficiency(flow_temp)
    boiler_output_kw = gas_rate * energy_calculator.GAS_CALORIFIC_KWH * efficiency
    boiler_output_kcal_h = boiler_output_kw * 860.0

    # 3. Net isi dengesi
    net_heating_kw = boiler_output_kw - heat_loss_kw if relay_on else -heat_loss_kw
    net_heating_kcal_h = net_heating_kw * 860.0

    # 4. Soguma hizi
    cooling_rate = energy_calculator.estimate_cooling_rate(
        avg_indoor or 20, outdoor_temp
    )

    # 5. Denge sicakligi (kombi surekli calisirsa)
    if heat_loss_coeff_w_per_c > 0:
        equilibrium_temp = outdoor_temp + (boiler_output_kw * 1000 / heat_loss_coeff_w_per_c)
    else:
        equilibrium_temp = outdoor_temp

    # 6. Hedef sicakliga ulasma suresi (eger altindaysak)
    if avg_indoor is not None and avg_indoor < target_temp:
        estimate = energy_calculator.estimate_heating_duration(
            avg_indoor, target_temp, outdoor_temp, floor_area, flow_temp
        )
        time_to_target = {
            "minutes": estimate.estimated_minutes,
            "gas_m3": estimate.estimated_gas_m3,
            "cost_tl": estimate.estimated_cost_tl,
            "kwh": estimate.estimated_kwh,
            "confidence": estimate.confidence,
        }
    else:
        time_to_target = None

    # 7. Nem konfor analizi
    humidity_status = "unknown"
    humidity_advice = ""
    if avg_humidity is not None:
        if avg_humidity < 30:
            humidity_status = "too_dry"
            humidity_advice = "Hava cok kuru. Nemlendirici kullanmayi deneyin."
        elif avg_humidity < 40:
            humidity_status = "dry"
            humidity_advice = "Hava biraz kuru ama kabul edilebilir."
        elif avg_humidity <= 60:
            humidity_status = "comfortable"
            humidity_advice = "Nem seviyesi ideal aralikta."
        elif avg_humidity <= 70:
            humidity_status = "humid"
            humidity_advice = "Nem biraz yuksek. Havalandirma onerilir."
        else:
            humidity_status = "too_humid"
            humidity_advice = "Nem cok yuksek! Kuf riski var. Havalandirin."

    # 8. Enerji oneriler
    recommendations = []

    # Verimlilik onerisi
    eff_analysis = energy_calculator.analyze_efficiency(flow_temp)
    if eff_analysis.potential_savings_pct > 3:
        recommendations.append({
            "type": "efficiency",
            "priority": "high",
            "title": "Radyator su sicakligini dusurun",
            "message": f"Su sicakliginizi {flow_temp:.0f}°C'den {eff_analysis.optimal_flow_temp:.0f}°C'ye "
                       f"dusurerek %{eff_analysis.potential_savings_pct:.0f} tasarruf edebilirsiniz.",
            "estimated_savings_pct": eff_analysis.potential_savings_pct,
        })

    # Soguk hava uyarisi
    if weather.forecast_min_temp is not None and weather.forecast_min_temp < 0:
        recommendations.append({
            "type": "forecast",
            "priority": "medium",
            "title": "Yarin don riski",
            "message": f"Yarin minimum sicaklik {weather.forecast_min_temp:.1f}°C bekleniyor. "
                       "On isitma planlamasi yapabilirsiniz.",
        })

    # Duty cycle uyarisi
    runtime_hours_today = total_runtime / 3600.0
    if runtime_hours_today > 16:
        recommendations.append({
            "type": "energy",
            "priority": "high",
            "title": "Yuksek calisma suresi",
            "message": f"Kombi bugun {runtime_hours_today:.1f} saat calisti. "
                       "Yalitim kontrolu yapmaniz onerilir.",
        })

    # Hedef sicaklik onerisi
    if avg_indoor is not None and target_temp - avg_indoor > 3:
        recommendations.append({
            "type": "comfort",
            "priority": "medium",
            "title": "Ic sicaklik hedefin altinda",
            "message": f"Ic sicaklik ({avg_indoor:.1f}°C) hedefin ({target_temp:.1f}°C) "
                       f"{target_temp - avg_indoor:.1f}°C altinda.",
        })

    # Dis sicaklik ile karsilastirma bazli oneri
    if avg_indoor is not None and outdoor_temp > avg_indoor:
        recommendations.append({
            "type": "ventilation",
            "priority": "info",
            "title": "Pencere acarak sogutun",
            "message": f"Dis hava ({outdoor_temp:.1f}°C) ic sicakliktan ({avg_indoor:.1f}°C) yuksek. "
                       "Pencere acarak dogal sogutma yapabilirsiniz.",
        })

    return ApiResponse(
        success=True,
        data={
            "indoor": {
                "avg_temp": round(avg_indoor, 1) if avg_indoor else None,
                "avg_humidity": round(avg_humidity, 1) if avg_humidity else None,
                "rooms": room_details,
                "target_temp": target_temp,
            },
            "outdoor": {
                "temp": outdoor_temp,
                "humidity": outdoor_humidity,
                "description": weather.description,
                "forecast_min": weather.forecast_min_temp,
                "forecast_max": weather.forecast_max_temp,
            },
            "thermal_balance": {
                "delta_t": round(delta_t, 1),
                "heat_loss_kw": round(heat_loss_kw, 2),
                "heat_loss_kcal_h": round(heat_loss_kcal_per_hour, 0),
                "boiler_output_kw": round(boiler_output_kw, 2),
                "boiler_output_kcal_h": round(boiler_output_kcal_h, 0),
                "net_heating_kw": round(net_heating_kw, 2),
                "net_heating_kcal_h": round(net_heating_kcal_h, 0),
                "relay_on": relay_on,
                "equilibrium_temp": round(equilibrium_temp, 1),
                "cooling_rate_c_h": cooling_rate,
            },
            "energy_today": {
                "runtime_hours": round(runtime_hours_today, 1),
                "gas_m3": round(
                    energy_calculator.calculate_gas_consumption(runtime_hours_today, flow_temp), 2
                ),
                "cost_tl": round(
                    energy_calculator.calculate_cost(
                        energy_calculator.calculate_gas_consumption(runtime_hours_today, flow_temp),
                        gas_price,
                    ), 2
                ),
            },
            "time_to_target": time_to_target,
            "humidity_analysis": {
                "status": humidity_status,
                "advice": humidity_advice,
                "indoor_pct": round(avg_humidity, 1) if avg_humidity else None,
                "outdoor_pct": outdoor_humidity,
            },
            "efficiency": {
                "current_pct": eff_analysis.current_efficiency_pct,
                "is_condensing": eff_analysis.is_condensing,
                "flow_temp": flow_temp,
            },
            "recommendations": recommendations,
        },
    )
