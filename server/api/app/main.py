"""Tonbil Termostat API - Ana uygulama modulu."""

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.config import settings
from app.models.database import init_db, close_db, async_session
from app.models.fcm import FCMToken  # noqa: F401 — Base.metadata icin import
from app.models.notification_prefs import NotificationPreferences  # noqa: F401
from app.mqtt.subscriber import MQTTService
from app.routers import auth, config, devices, energy, notifications, provisioning, rooms, sensors, weather
from app.services.heating import heating_service
from app.websocket.manager import ws_manager

logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("tonbil.api")

# Global MQTT servisi
mqtt_service: MQTTService | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Uygulama baslatma ve kapatma islemleri."""
    global mqtt_service

    logger.info("Tonbil API baslatiliyor...")

    # Veritabani baglantisi
    await init_db()
    logger.info("PostgreSQL baglantisi kuruldu")

    # MQTT servisi
    mqtt_service = MQTTService()
    mqtt_task = asyncio.create_task(mqtt_service.start())
    logger.info("MQTT servisi baslatildi")

    # Redis baglantisi (opsiyonel)
    if settings.redis_enabled:
        try:
            import redis.asyncio as aioredis
            app.state.redis = aioredis.from_url(
                settings.redis_url,
                decode_responses=True,
                socket_connect_timeout=5,
            )
            await app.state.redis.ping()
            logger.info("Redis baglantisi kuruldu")
        except Exception as e:
            logger.warning("Redis baglanamadi, cache devre disi: %s", e)
            app.state.redis = None
    else:
        app.state.redis = None
        logger.info("Redis devre disi (REDIS_ENABLED=false)")

    # MQTT servisini app state'e koy (router'lar kullansin)
    app.state.mqtt = mqtt_service

    # Heating service'e MQTT referansini ver (relay komutu gonderebilsin)
    heating_service.set_mqtt_service(mqtt_service)

    # DB'den kayitli konum ve config'i yukle (app.state'e)
    try:
        from sqlalchemy import text
        async with async_session() as session:
            result = await session.execute(text(
                "SELECT weather_lat, weather_lon, weather_city, weather_district FROM heating_config LIMIT 1"
            ))
            row = result.fetchone()
            if row and row[0]:
                app.state.weather_lat = row[0]
                app.state.weather_lon = row[1]
                city = row[3] + ", " + row[2] if row[3] else row[2]
                app.state.weather_city = city or settings.weather_city
                logger.info("Konum DB'den yuklendi: %s (%.4f, %.4f)", app.state.weather_city, row[0], row[1])
            else:
                app.state.weather_lat = settings.weather_lat
                app.state.weather_lon = settings.weather_lon
                app.state.weather_city = settings.weather_city
    except Exception as e:
        logger.warning("Konum DB'den yuklenemedi, varsayilan kullaniliyor: %s", e)
        app.state.weather_lat = settings.weather_lat
        app.state.weather_lon = settings.weather_lon
        app.state.weather_city = settings.weather_city

    # Server restart: tum cihazlari offline isaretle — ESP telemetrisi ile geri gelecekler
    try:
        from sqlalchemy import update as sql_update
        async with async_session() as session:
            from app.models.database import Device
            await session.execute(sql_update(Device).values(is_online=False))
            await session.commit()
        logger.info("Server restart: tum cihazlar offline olarak isaretlendi, telemetri bekleniyor")
    except Exception as e:
        logger.warning("Cihaz reset hatasi: %s", e)

    # Periyodik isitma degerlendirme zamanlayicisi (30sn)
    heating_service.start_periodic_evaluation()
    logger.info("Periyodik isitma degerlendirme baslatildi")

    # Hava durumu periyodik loglama baslat (InfluxDB'ye kaydet)
    async def weather_logger_task():
        import httpx
        while True:
            try:
                await asyncio.sleep(900)  # 15 dakikada bir
                async with httpx.AsyncClient(timeout=10) as client:
                    lat = getattr(app.state, 'weather_lat', settings.weather_lat)
                    lon = getattr(app.state, 'weather_lon', settings.weather_lon)
                    resp = await client.get("https://api.open-meteo.com/v1/forecast", params={
                        "latitude": lat, "longitude": lon,
                        "current": "temperature_2m,relative_humidity_2m,wind_speed_10m,pressure_msl,weather_code",
                    })
                    if resp.status_code == 200:
                        wd = resp.json().get("current", {})
                        from influxdb_client import Point
                        point = (
                            Point("weather_data")
                            .tag("source", "open-meteo")
                            .field("temperature", float(wd.get("temperature_2m", 0)))
                            .field("humidity", float(wd.get("relative_humidity_2m", 0)))
                            .field("pressure", float(wd.get("pressure_msl", 0)))
                            .field("wind_speed", float(wd.get("wind_speed_10m", 0)))
                            .field("weather_code", int(wd.get("weather_code", 0)))
                        )
                        mqtt_service._influx_write.write(
                            bucket=settings.influxdb_bucket,
                            org=settings.influxdb_org,
                            record=point,
                        )
                        logger.debug("Hava durumu InfluxDB'ye kaydedildi")
            except Exception as e:
                logger.debug("Hava durumu loglama hatasi: %s", e)

    asyncio.create_task(weather_logger_task())

    yield

    # Kapatma
    logger.info("Tonbil API kapatiliyor...")
    if mqtt_service:
        mqtt_service.stop()
        mqtt_task.cancel()
        try:
            await mqtt_task
        except asyncio.CancelledError:
            pass

    if app.state.redis:
        await app.state.redis.aclose()

    await close_db()
    logger.info("Tonbil API kapatildi")


app = FastAPI(
    title="Tonbil Termostat API",
    description="Akilli ev isitma kontrol sistemi",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/api/docs" if settings.api_debug else None,
    redoc_url="/api/redoc" if settings.api_debug else None,
)

# CORS
origins = [o.strip() for o in settings.cors_origins.split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# --- Router'lar ---
app.include_router(auth.router, prefix="/api/auth", tags=["auth"])
app.include_router(devices.router, prefix="/api/devices", tags=["devices"])
app.include_router(rooms.router, prefix="/api/rooms", tags=["rooms"])
app.include_router(sensors.router, prefix="/api/sensors", tags=["sensors"])
app.include_router(weather.router, prefix="/api/weather", tags=["weather"])
app.include_router(config.router, prefix="/api/config", tags=["config"])
app.include_router(provisioning.router, prefix="/api/provisioning", tags=["provisioning"])
app.include_router(energy.router, prefix="/api/energy", tags=["energy"])
app.include_router(notifications.router, prefix="/api/notifications", tags=["notifications"])


# --- Health Check ---
@app.get("/health")
@app.get("/api/health")
async def health_check():
    return {"status": "ok", "service": "tonbil-api"}


# --- WebSocket ---
@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    """Panel icin real-time veri akisi."""
    # Auth: Cookie veya query param'dan token dogrula
    token = websocket.cookies.get("tonbil_token") or websocket.query_params.get("token")
    if not token:
        await websocket.close(code=4001, reason="Token gerekli")
        return
    try:
        from app.routers.auth import verify_token
        verify_token(token)
    except Exception:
        await websocket.close(code=4001, reason="Gecersiz token")
        return

    await ws_manager.connect(websocket)
    try:
        while True:
            try:
                data = await asyncio.wait_for(websocket.receive_text(), timeout=45)
                # Client heartbeat/pong mesajlarını sessizce kabul et
                stripped = data.strip()
                if stripped in ('{"type":"pong"}', '{"type":"heartbeat"}', 'pong'):
                    logger.debug("WS heartbeat alindi")
                    continue
                logger.debug("WS mesaj alindi: %s", stripped[:100])
            except asyncio.TimeoutError:
                # 45sn mesaj yok — ping gönder, bağlantıyı kontrol et
                logger.info("WS timeout, ping gonderiliyor")
                try:
                    await websocket.send_json({"type": "ping"})
                except Exception as e:
                    logger.info("WS ping gonderilemedi, kapatiliyor: %s", e)
                    break
    except WebSocketDisconnect as e:
        logger.info("WS disconnect: code=%s reason=%s", e.code, e.reason)
    except Exception as e:
        logger.info("WS hata ile kapandi: %s (%s)", e, type(e).__name__)
    finally:
        await ws_manager.disconnect(websocket)


# --- Global exception handler ---
@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    logger.error("Beklenmeyen hata: %s", exc, exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"success": False, "error": "Internal server error"},
    )
