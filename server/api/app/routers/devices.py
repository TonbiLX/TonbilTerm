"""Cihaz yonetimi endpoint'leri."""

import logging

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import joinedload

from app.models.database import Device, Room, AuditLog, User, get_session
from app.models.schemas import (
    ApiResponse,
    DeviceCommand,
    DeviceResponse,
    DeviceUpdate,
)
from app.routers.auth import get_current_user, require_admin

logger = logging.getLogger("tonbil.devices")
router = APIRouter()


def _device_to_response(device: Device) -> dict:
    """Device ORM nesnesini response dict'e cevir."""
    return DeviceResponse(
        id=device.id,
        device_id=device.device_id,
        name=device.name,
        room_id=device.room_id,
        room_name=device.room.name if device.room else None,
        type=device.type,
        mqtt_user=device.mqtt_user,
        last_seen=device.last_seen,
        firmware_version=device.firmware_version,
        ip_address=device.ip_address,
        is_online=device.is_online,
        created_at=device.created_at,
    ).model_dump()


@router.get("")
async def list_devices(
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Tum cihazlari listele."""
    result = await session.execute(
        select(Device).options(joinedload(Device.room)).order_by(Device.created_at)
    )
    devices = result.scalars().unique().all()

    return ApiResponse(
        success=True,
        data=[_device_to_response(d) for d in devices],
    )


@router.get("/{device_id}")
async def get_device(
    device_id: str,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Tek cihaz detayi."""
    result = await session.execute(
        select(Device)
        .options(joinedload(Device.room))
        .where(Device.device_id == device_id)
    )
    device = result.scalar_one_or_none()

    if not device:
        raise HTTPException(status_code=404, detail="Cihaz bulunamadi")

    return ApiResponse(success=True, data=_device_to_response(device))


@router.put("/{device_id}")
async def update_device(
    device_id: str,
    body: DeviceUpdate,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(require_admin),
):
    """Cihaz bilgilerini guncelle (isim, oda atamasi). Sadece admin."""
    result = await session.execute(
        select(Device)
        .options(joinedload(Device.room))
        .where(Device.device_id == device_id)
    )
    device = result.scalar_one_or_none()

    if not device:
        raise HTTPException(status_code=404, detail="Cihaz bulunamadi")

    update_data = body.model_dump(exclude_unset=True)

    # Oda atamasi kontrol
    if "room_id" in update_data and update_data["room_id"] is not None:
        room_check = await session.execute(
            select(Room).where(Room.id == update_data["room_id"])
        )
        if not room_check.scalar_one_or_none():
            raise HTTPException(status_code=404, detail="Oda bulunamadi")

    # Audit log
    for key, new_val in update_data.items():
        old_val = getattr(device, key, None)
        if old_val != new_val:
            session.add(AuditLog(
                device_id=device_id,
                action=f"device.{key}.update",
                old_value=str(old_val),
                new_value=str(new_val),
            ))

    for key, value in update_data.items():
        setattr(device, key, value)

    await session.commit()
    await session.refresh(device)

    # Room relation'i yeniden yukle
    result = await session.execute(
        select(Device)
        .options(joinedload(Device.room))
        .where(Device.device_id == device_id)
    )
    device = result.scalar_one()

    logger.info("Cihaz guncellendi: %s -> %s", device_id, update_data)

    return ApiResponse(success=True, data=_device_to_response(device))


@router.delete("/{device_id}")
async def delete_device(
    device_id: str,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(require_admin),
):
    """Cihazi sil. Sadece admin."""
    result = await session.execute(
        select(Device).where(Device.device_id == device_id)
    )
    device = result.scalar_one_or_none()

    if not device:
        raise HTTPException(status_code=404, detail="Cihaz bulunamadi")

    session.add(AuditLog(
        device_id=device_id,
        action="device.delete",
        old_value=device.name,
    ))

    await session.delete(device)
    await session.commit()

    logger.info("Cihaz silindi: %s", device_id)

    return ApiResponse(success=True, data={"message": f"Cihaz {device_id} silindi"})


@router.post("/{device_id}/command")
async def send_command(
    device_id: str,
    body: DeviceCommand,
    request: Request,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Cihaza MQTT komutu gonder."""
    # Cihaz var mi kontrol et
    result = await session.execute(
        select(Device).where(Device.device_id == device_id)
    )
    device = result.scalar_one_or_none()

    if not device:
        raise HTTPException(status_code=404, detail="Cihaz bulunamadi")

    # Gecerli komutlar
    valid_commands = {"relay_on", "relay_off", "reboot", "ota_update", "config_update"}
    if body.command not in valid_commands:
        raise HTTPException(
            status_code=400,
            detail=f"Gecersiz komut. Gecerli komutlar: {', '.join(valid_commands)}",
        )

    # MQTT ile gonder
    mqtt_service = request.app.state.mqtt
    if not mqtt_service or not mqtt_service._connected:
        raise HTTPException(status_code=503, detail="MQTT servisi hazir degil, birkac saniye bekleyip tekrar deneyin")

    try:
        mqtt_service.publish_command(device_id, body.command, body.payload)
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Komut gonderilemedi: {e}")

    # Audit log
    session.add(AuditLog(
        device_id=device_id,
        action=f"command.{body.command}",
        new_value=str(body.payload) if body.payload else None,
    ))
    await session.commit()

    return ApiResponse(
        success=True,
        data={"message": f"Komut gonderildi: {body.command}", "device_id": device_id},
    )
