"""Oda yonetimi endpoint'leri."""

import asyncio
import logging
import re
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException
from influxdb_client import InfluxDBClient
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.database import Device, Room, User, get_session
from app.models.schemas import ApiResponse, RoomCreate, RoomResponse, RoomUpdate
from app.routers.auth import get_current_user

logger = logging.getLogger("tonbil.rooms")
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


async def _get_room_latest_data(room_id: int, device_ids: list[str]) -> dict:
    """InfluxDB'den oda icin en son sensor verisini al."""
    if not device_ids:
        return {"current_temp": None, "current_humidity": None}

    try:
        # Validate device IDs before interpolating into Flux query
        for did in device_ids:
            if not _DEVICE_ID_RE.match(did):
                logger.warning("Gecersiz device_id format (room %d): %s", room_id, did)
                return {"current_temp": None, "current_humidity": None}
        # Device ID filter olustur
        device_filter = " or ".join(
            [f'r["device_id"] == "{did}"' for did in device_ids]
        )

        flux_query = f'''
        from(bucket: "{settings.influxdb_bucket}")
          |> range(start: -1h)
          |> filter(fn: (r) => r["_measurement"] == "sensor_data")
          |> filter(fn: (r) => {device_filter})
          |> filter(fn: (r) => r["_field"] == "temperature" or r["_field"] == "humidity")
          |> last()
        '''

        # Blocking InfluxDB cagrisini thread'de calistir
        loop = asyncio.get_running_loop()
        tables = await loop.run_in_executor(None, _query_influx_sync, flux_query)

        result = {"current_temp": None, "current_humidity": None}
        for table in tables:
            for record in table.records:
                field = record.get_field()
                if field == "temperature":
                    result["current_temp"] = round(record.get_value(), 1)
                elif field == "humidity":
                    result["current_humidity"] = round(record.get_value(), 1)

        return result
    except Exception as e:
        logger.warning("InfluxDB okuma hatasi (room %d): %s", room_id, e)
        return {"current_temp": None, "current_humidity": None}


@router.get("")
async def list_rooms(
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Tum odalari, sensor cihaz sayisi ve anlik sicaklik ile listele."""
    # Odalari ve cihaz sayisini al
    result = await session.execute(
        select(Room).order_by(Room.sort_order, Room.name)
    )
    rooms = result.scalars().all()

    room_responses = []
    for room in rooms:
        # Odadaki sensor cihazlarini bul
        dev_result = await session.execute(
            select(Device).where(
                Device.room_id == room.id,
                Device.type.in_(["sensor", "combo"]),
            )
        )
        devices = dev_result.scalars().all()
        device_ids = [d.device_id for d in devices]

        # InfluxDB'den anlik veri
        latest = await _get_room_latest_data(room.id, device_ids)

        # Toplam cihaz sayisi (sensor + relay)
        count_result = await session.execute(
            select(func.count()).select_from(Device).where(Device.room_id == room.id)
        )
        device_count = count_result.scalar() or 0

        room_responses.append(
            RoomResponse(
                id=room.id,
                name=room.name,
                weight=room.weight,
                min_temp=room.min_temp,
                icon=room.icon,
                sort_order=room.sort_order,
                current_temp=latest["current_temp"],
                current_humidity=latest["current_humidity"],
                device_count=device_count,
            ).model_dump()
        )

    return ApiResponse(success=True, data=room_responses)


@router.get("/{room_id}")
async def get_room(
    room_id: int,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Tek oda detayi."""
    result = await session.execute(select(Room).where(Room.id == room_id))
    room = result.scalar_one_or_none()

    if not room:
        raise HTTPException(status_code=404, detail="Oda bulunamadi")

    dev_result = await session.execute(
        select(Device).where(Device.room_id == room.id, Device.type == "sensor")
    )
    device_ids = [d.device_id for d in dev_result.scalars().all()]
    latest = await _get_room_latest_data(room.id, device_ids)

    count_result = await session.execute(
        select(func.count()).select_from(Device).where(Device.room_id == room.id)
    )

    return ApiResponse(
        success=True,
        data=RoomResponse(
            id=room.id,
            name=room.name,
            weight=room.weight,
            min_temp=room.min_temp,
            icon=room.icon,
            sort_order=room.sort_order,
            current_temp=latest["current_temp"],
            current_humidity=latest["current_humidity"],
            device_count=count_result.scalar() or 0,
        ).model_dump(),
    )


@router.post("")
async def create_room(
    body: RoomCreate,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Yeni oda olustur."""
    room = Room(**body.model_dump())
    session.add(room)
    await session.commit()
    await session.refresh(room)

    logger.info("Yeni oda olusturuldu: %s", room.name)

    return ApiResponse(
        success=True,
        data=RoomResponse(
            id=room.id,
            name=room.name,
            weight=room.weight,
            min_temp=room.min_temp,
            icon=room.icon,
            sort_order=room.sort_order,
            device_count=0,
        ).model_dump(),
    )


@router.put("/{room_id}")
async def update_room(
    room_id: int,
    body: RoomUpdate,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Oda bilgilerini guncelle."""
    result = await session.execute(select(Room).where(Room.id == room_id))
    room = result.scalar_one_or_none()

    if not room:
        raise HTTPException(status_code=404, detail="Oda bulunamadi")

    update_data = body.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(room, key, value)

    await session.commit()
    await session.refresh(room)

    logger.info("Oda guncellendi: %s -> %s", room.name, update_data)

    return ApiResponse(
        success=True,
        data=RoomResponse(
            id=room.id,
            name=room.name,
            weight=room.weight,
            min_temp=room.min_temp,
            icon=room.icon,
            sort_order=room.sort_order,
        ).model_dump(),
    )


@router.delete("/{room_id}")
async def delete_room(
    room_id: int,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Oda sil. Odaya atanmis cihazlarin room_id'si NULL olur."""
    result = await session.execute(select(Room).where(Room.id == room_id))
    room = result.scalar_one_or_none()

    if not room:
        raise HTTPException(status_code=404, detail="Oda bulunamadi")

    room_name = room.name
    await session.delete(room)
    await session.commit()

    logger.info("Oda silindi: %s", room_name)

    return ApiResponse(success=True, data={"message": f"Oda '{room_name}' silindi"})
