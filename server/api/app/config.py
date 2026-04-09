"""Uygulama konfigurasyonu - environment variable'lardan okunur."""

import os

from pydantic_settings import BaseSettings


def _build_database_url() -> str:
    """DATABASE_URL yoksa POSTGRES_* env var'larindan olustur."""
    if os.environ.get("DATABASE_URL"):
        return os.environ["DATABASE_URL"]
    user = os.environ.get("POSTGRES_USER", "tonbil")
    password = os.environ.get("POSTGRES_PASSWORD", "tonbil_secret_2024")
    db = os.environ.get("POSTGRES_DB", "tonbil")
    host = os.environ.get("POSTGRES_HOST", "postgres")
    port = os.environ.get("POSTGRES_PORT", "5432")
    return f"postgresql+asyncpg://{user}:{password}@{host}:{port}/{db}"


class Settings(BaseSettings):
    # PostgreSQL
    database_url: str = _build_database_url()

    # InfluxDB
    influxdb_url: str = "http://influxdb:8086"
    influxdb_token: str = "tonbil-influx-admin-token-change-me"
    influxdb_org: str = "tonbil"
    influxdb_bucket: str = "sensors"

    # MQTT
    mqtt_host: str = "mosquitto"
    mqtt_port: int = 1883
    mqtt_user: str = "api_server"
    mqtt_password: str = "tonbil_api_2024"

    # MQTT external (returned to ESP32 devices during provisioning)
    mqtt_external_host: str = "192.168.1.9"
    mqtt_external_port: int = 1883

    # Redis
    redis_url: str = "redis://redis:6379/0"
    redis_enabled: bool = True

    # Auth
    jwt_secret: str = "change-me-in-production-use-openssl-rand-hex-32"
    jwt_algorithm: str = "HS256"
    jwt_expire_hours: int = 24

    # Hava durumu
    weather_lat: float = 39.93
    weather_lon: float = 32.86
    weather_city: str = "Ankara"

    # FCM (Firebase Cloud Messaging) - push notification
    fcm_server_key: str = ""
    fcm_enabled: bool = False

    # CORS
    cors_origins: str = "http://localhost:3000,http://localhost:80,http://localhost,https://temp.tonbilx.com"

    # Debug
    api_debug: bool = False
    log_level: str = "INFO"

    model_config = {"env_file": ".env", "extra": "ignore"}


settings = Settings()
