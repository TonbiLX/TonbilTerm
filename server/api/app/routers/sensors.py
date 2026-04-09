"""Sensor veri endpoint'leri - InfluxDB'den zaman serisi okuma."""

import asyncio
import logging
import re

from fastapi import APIRouter, Depends, HTTPException, Query
from influxdb_client import InfluxDBClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.database import Device, Room, User, get_session
from app.models.schemas import ApiResponse, SensorHistoryPoint, SensorReading
from app.routers.auth import get_current_user

logger = logging.getLogger("tonbil.sensors")
router = APIRouter()

# Device ID format validation (alphanumeric, dash, underscore)
_DEVICE_ID_RE = re.compile(r"^[a-zA-Z0-9_-]+$")


def _get_influx_client() -> InfluxDBClient:
    return InfluxDBClient(
        url=settings.influxdb_url,
        token=settings.influxdb_token,
        org=settings.influxdb_org,
    )


def _query_influx_sync(flux_query: str) -> list:
    """InfluxDB sorgusunu sync olarak calistir (thread'de calistirilacak)."""
    client = _get_influx_client()
    try:
        query_api = client.query_api()
        return query_api.query(flux_query, org=settings.influxdb_org)
    finally:
        client.close()


@router.get("/current")
async def get_current_readings(
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Tum sensorlerden en son okumalari dondur."""
    # Sensor cihazlarini al
    result = await session.execute(
        select(Device, Room.name.label("room_name"))
        .outerjoin(Room, Device.room_id == Room.id)
        .where(Device.type.in_(["sensor", "combo"]))
    )
    rows = result.all()

    if not rows:
        return ApiResponse(success=True, data=[])

    device_map = {}
    for device, room_name in rows:
        device_map[device.device_id] = {
            "room_id": device.room_id,
            "room_name": room_name,
        }

    # InfluxDB'den son okumalari al
    try:
        device_ids = list(device_map.keys())
        # Validate device IDs before interpolating into Flux query
        for did in device_ids:
            if not _DEVICE_ID_RE.match(did):
                logger.warning("Gecersiz device_id format: %s", did)
                return ApiResponse(success=True, data=[])
        device_filter = " or ".join(
            [f'r["device_id"] == "{did}"' for did in device_ids]
        )

        flux_query = f'''
        from(bucket: "{settings.influxdb_bucket}")
          |> range(start: -1h)
          |> filter(fn: (r) => r["_measurement"] == "sensor_data")
          |> filter(fn: (r) => {device_filter})
          |> last()
          |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
        '''

        loop = asyncio.get_running_loop()
        tables = await loop.run_in_executor(None, _query_influx_sync, flux_query)

        readings = []
        for table in tables:
            for record in table.records:
                did = record.values.get("device_id", "")
                info = device_map.get(did, {})
                readings.append(
                    SensorReading(
                        device_id=did,
                        room_id=info.get("room_id"),
                        room_name=info.get("room_name"),
                        temperature=record.values.get("temperature"),
                        humidity=record.values.get("humidity"),
                        battery=record.values.get("battery"),
                        rssi=record.values.get("rssi"),
                        timestamp=record.get_time(),
                    ).model_dump()
                )

        return ApiResponse(success=True, data=readings)

    except Exception as e:
        logger.error("InfluxDB okuma hatasi: %s", e)
        return ApiResponse(success=True, data=[])


@router.get("/history")
async def get_sensor_history(
    room: int | None = Query(default=None, description="Oda ID filtresi"),
    device: str | None = Query(default=None, description="Device ID filtresi"),
    time_range: str = Query(
        default="24h",
        alias="range",
        description="Zaman araligi: 1h, 6h, 24h, 7d, 30d",
    ),
    _user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    """Zaman serisi sensor verisi dondur."""
    # Gecerli range degerleri
    valid_ranges = {"1h": "-1h", "6h": "-6h", "24h": "-24h", "7d": "-7d", "30d": "-30d"}
    flux_range = valid_ranges.get(time_range)
    if not flux_range:
        raise HTTPException(
            status_code=400,
            detail=f"Gecersiz aralik. Gecerli degerler: {', '.join(valid_ranges.keys())}",
        )

    # Aggregation window - uzun sureler icin veri noktasini azalt
    agg_windows = {"1h": "1m", "6h": "5m", "24h": "15m", "7d": "1h", "30d": "6h"}
    agg_window = agg_windows[time_range]

    # Device filtresi olustur
    device_filter = ""
    if device:
        if not _DEVICE_ID_RE.match(device):
            raise HTTPException(status_code=400, detail="Gecersiz device ID formati")
        device_filter = f'|> filter(fn: (r) => r["device_id"] == "{device}")'
    elif room:
        # Odadaki sensor ve combo cihazlarini bul
        result = await session.execute(
            select(Device.device_id).where(
                Device.room_id == room,
                Device.type.in_(["sensor", "combo"]),
            )
        )
        device_ids = [row[0] for row in result.all()]
        if not device_ids:
            return ApiResponse(success=True, data=[])
        filter_parts = " or ".join([f'r["device_id"] == "{did}"' for did in device_ids])
        device_filter = f"|> filter(fn: (r) => {filter_parts})"

    try:
        flux_query = f'''
        from(bucket: "{settings.influxdb_bucket}")
          |> range(start: {flux_range})
          |> filter(fn: (r) => r["_measurement"] == "sensor_data")
          {device_filter}
          |> filter(fn: (r) => r["_field"] == "temperature" or r["_field"] == "humidity" or r["_field"] == "pressure")
          |> aggregateWindow(every: {agg_window}, fn: mean, createEmpty: false)
          |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
          |> sort(columns: ["_time"])
        '''

        loop = asyncio.get_running_loop()
        tables = await loop.run_in_executor(None, _query_influx_sync, flux_query)

        history = []
        for table in tables:
            for record in table.records:
                temp = record.values.get("temperature")
                hum = record.values.get("humidity")
                pres = record.values.get("pressure")
                history.append({
                    "time": record.get_time().isoformat() if record.get_time() else None,
                    "temperature": round(temp, 1) if temp is not None else None,
                    "humidity": round(hum, 1) if hum is not None else None,
                    "pressure": round(pres, 1) if pres is not None else None,
                })

        return ApiResponse(success=True, data=history)

    except Exception as e:
        logger.error("InfluxDB history okuma hatasi: %s", e)
        return ApiResponse(success=True, data=[])
