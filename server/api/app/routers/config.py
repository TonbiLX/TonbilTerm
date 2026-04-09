"""Isitma konfigurasyonu, zamanlama ve boost endpoint'leri."""

import asyncio
import json
import logging
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.database import AuditLog, HeatingConfig, Schedule, User, get_session
from app.models.schemas import (
    ApiResponse,
    HeatingConfigResponse,
    HeatingConfigUpdate,
    HeatingProfileCreate,
    HeatingProfileResponse,
    HeatingProfileUpdate,
    ScheduleEntry,
    ScheduleUpdate,
)
from app.routers.auth import get_current_user
from app.websocket.manager import ws_manager

logger = logging.getLogger("tonbil.config")
router = APIRouter()


async def _trigger_heating_eval():
    """Heating service'i arka planda tetikle.

    InfluxDB sorgusu zaman alabilir; HTTP response'unu bloklamak yerine
    asyncio.create_task ile ayırıyoruz. UI config_update WS mesajını
    anında alır, heating kararı arka planda verilir.
    """
    from app.services.heating import heating_service
    try:
        await heating_service.on_config_changed()
    except Exception as e:
        logger.warning("Heating service arka plan tetikleme hatasi: %s", e)


# --- Boost Mode Request/Response Models ---


class BoostRequest(BaseModel):
    minutes: int = Field(ge=10, le=120, description="Boost suresi (dakika, 10-120)")


class BoostStatusResponse(BaseModel):
    active: bool = False
    remaining_minutes: int = 0
    total_minutes: int = 0
    started_at: str | None = None
    estimated_gas_cost_tl: float | None = None


@router.get("/heating")
async def get_heating_config(
    request: Request,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Mevcut isitma konfigurasyonunu dondur."""
    result = await session.execute(select(HeatingConfig).limit(1))
    config = result.scalar_one_or_none()

    if not config:
        raise HTTPException(status_code=404, detail="Isitma konfigurasyonu bulunamadi")

    # Anlik role durumunu MQTT servisinden al
    mqtt_service = request.app.state.mqtt
    relay_state = mqtt_service.relay_state if mqtt_service else False

    response = HeatingConfigResponse(
        target_temp=config.target_temp,
        hysteresis=config.hysteresis,
        min_cycle_min=config.min_cycle_min,
        mode=config.mode,
        strategy=config.strategy,
        gas_price_per_m3=config.gas_price_per_m3,
        floor_area_m2=config.floor_area_m2,
        boiler_power_kw=config.boiler_power_kw,
        flow_temp=getattr(config, "flow_temp", 60.0),
        boiler_brand=getattr(config, "boiler_brand", "ECA Proteus Premix"),
        boiler_model=getattr(config, "boiler_model", "30kW"),
        relay_state=relay_state,
        updated_at=config.updated_at,
    )

    return ApiResponse(success=True, data=response.model_dump())


@router.put("/heating")
async def update_heating_config(
    body: HeatingConfigUpdate,
    request: Request,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Isitma konfigurasyonunu guncelle."""
    result = await session.execute(select(HeatingConfig).limit(1))
    config = result.scalar_one_or_none()

    if not config:
        raise HTTPException(status_code=404, detail="Isitma konfigurasyonu bulunamadi")

    # Gecerli degerler kontrolu
    if body.mode and body.mode not in ("auto", "manual", "manual_on", "manual_off", "schedule"):
        raise HTTPException(status_code=400, detail="Gecersiz mod")
    if body.strategy and body.strategy not in (
        "weighted_avg", "coldest_room", "hottest_room", "single_room"
    ):
        raise HTTPException(status_code=400, detail="Gecersiz strateji")

    update_data = body.model_dump(exclude_unset=True)

    # Audit log
    for key, new_val in update_data.items():
        old_val = getattr(config, key, None)
        if old_val != new_val:
            session.add(AuditLog(
                action=f"heating_config.{key}.update",
                old_value=str(old_val),
                new_value=str(new_val),
                user_id=_user.id,
                user_email=_user.email,
            ))

    for key, value in update_data.items():
        setattr(config, key, value)

    await session.commit()
    await session.refresh(config)

    logger.info("Isitma konfigurasyonu guncellendi: %s", update_data)

    mqtt_service = request.app.state.mqtt

    # Mod degisikligine gore anlik aksiyon
    if body.mode:
        try:
            if body.mode == "manual_on":
                # Manuel AC: relay'i hemen ac
                if mqtt_service:
                    mqtt_service.publish_relay(True)
            elif body.mode == "manual_off":
                # Manuel KAPA: relay'i hemen kapat
                if mqtt_service:
                    mqtt_service.publish_relay(False)
            elif body.mode == "manual":
                # Manuel mod: relay olduğu gibi kalır, sadece otomatik kontrol durur
                pass
            elif body.mode in ("auto", "schedule"):
                # Auto/schedule: cihaza mod degisikligini bildir
                if mqtt_service:
                    mqtt_service.publish_command_to_relays("config", {
                        "mode": body.mode,
                        "target": config.target_temp,
                        "hysteresis": config.hysteresis,
                    })
        except ConnectionError as e:
            raise HTTPException(
                status_code=503,
                detail=f"MQTT servisi hazir degil, birkac saniye bekleyip tekrar deneyin: {e}",
            )

    # WebSocket broadcast ONCE yap — UI anlık güncellenir, InfluxDB beklenmez
    relay_state = mqtt_service.relay_state if mqtt_service else False
    config_response = HeatingConfigResponse(
        target_temp=config.target_temp,
        hysteresis=config.hysteresis,
        min_cycle_min=config.min_cycle_min,
        mode=config.mode,
        strategy=config.strategy,
        gas_price_per_m3=config.gas_price_per_m3,
        floor_area_m2=config.floor_area_m2,
        boiler_power_kw=config.boiler_power_kw,
        flow_temp=getattr(config, "flow_temp", 60.0),
        boiler_brand=getattr(config, "boiler_brand", "ECA Proteus Premix"),
        boiler_model=getattr(config, "boiler_model", "30kW"),
        relay_state=relay_state,
        updated_at=config.updated_at,
    )

    await ws_manager.broadcast({
        "type": "config_update",
        "data": config_response.model_dump(),
    })

    # Auto/schedule modda heating_service'i arka planda tetikle (HTTP response'u bekletme)
    # InfluxDB sorgusu 200ms-2s sürebileceği için create_task ile ayırıyoruz
    if not body.mode or body.mode in ("auto", "schedule"):
        from app.services.heating import heating_service
        asyncio.create_task(_trigger_heating_eval())

    return ApiResponse(success=True, data=config_response.model_dump())


@router.get("/schedules")
async def get_schedules(
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Haftalik zamanlama programini dondur."""
    result = await session.execute(
        select(Schedule).order_by(Schedule.day_of_week, Schedule.hour, Schedule.minute)
    )
    schedules = result.scalars().all()

    return ApiResponse(
        success=True,
        data=[ScheduleEntry.model_validate(s).model_dump() for s in schedules],
    )


@router.put("/schedules")
async def update_schedules(
    body: ScheduleUpdate,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Haftalik zamanlama programini toplu guncelle (replace all)."""
    # Mevcut tum schedule'lari sil
    await session.execute(delete(Schedule))

    # Yeni schedule'lari ekle
    new_schedules = []
    for entry in body.entries:
        schedule = Schedule(
            day_of_week=entry.day_of_week,
            hour=entry.hour,
            minute=entry.minute,
            target_temp=entry.target_temp,
            enabled=entry.enabled,
        )
        session.add(schedule)
        new_schedules.append(schedule)

    # Audit log
    session.add(AuditLog(
        action="schedules.bulk_update",
        new_value=f"{len(new_schedules)} entries",
        user_id=_user.id,
        user_email=_user.email,
    ))

    await session.commit()

    # Yeniden oku
    result = await session.execute(
        select(Schedule).order_by(Schedule.day_of_week, Schedule.hour, Schedule.minute)
    )
    schedules = result.scalars().all()

    logger.info("Zamanlama programi guncellendi: %d girdi", len(schedules))

    return ApiResponse(
        success=True,
        data=[ScheduleEntry.model_validate(s).model_dump() for s in schedules],
    )


# ============================================================
# Boost Mode Endpoints
# ============================================================


def _get_boost_state(request: Request) -> dict:
    """Boost durumunu app.state'ten oku."""
    return getattr(request.app.state, "boost", {
        "active": False,
        "remaining_minutes": 0,
        "total_minutes": 0,
        "started_at": None,
    })


def _set_boost_state(request: Request, state: dict):
    """Boost durumunu app.state'e yaz."""
    request.app.state.boost = state


def _estimate_gas_cost(minutes: int, request: Request) -> float | None:
    """Boost suresine gore tahmini dogalgaz maliyeti hesapla (TL).

    Basit formul: boiler_power_kw * (minutes/60) * gas_price_per_m3 / 10
    (10 kWh ~= 1 m3 dogalgaz)
    """
    try:
        # Default: 30kW boiler, ~7 TL/m3
        boiler_kw = 30.0
        gas_price = 7.0

        hours = minutes / 60.0
        kwh = boiler_kw * hours
        m3 = kwh / 10.0  # ~10 kWh per m3 natural gas
        cost = m3 * gas_price

        return round(cost, 2)
    except Exception:
        return None


@router.post("/boost")
async def activate_boost(
    body: BoostRequest,
    request: Request,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Boost modu: kombiyi belirli sure zorla calistir.

    Relay cihazina MQTT komutu gonderir ve durumu takip eder.
    Boost aktifken normal isitma kontrol algoritmasi devre disi kalir.
    """
    minutes = body.minutes

    if minutes < 10 or minutes > 120:
        raise HTTPException(
            status_code=400,
            detail="Boost suresi 10-120 dakika arasinda olmalidir",
        )

    mqtt_service = getattr(request.app.state, "mqtt", None)
    if mqtt_service is None:
        raise HTTPException(
            status_code=503,
            detail="MQTT servisi hazir degil",
        )

    # Send boost command to all relay/combo devices
    mqtt_service.publish_command_to_relays("boost", {"minutes": minutes})
    logger.info("Boost komutu gonderildi: %d dakika", minutes)

    # Track boost state in app.state
    now = datetime.now(timezone.utc)
    estimated_cost = _estimate_gas_cost(minutes, request)

    boost_state = {
        "active": True,
        "remaining_minutes": minutes,
        "total_minutes": minutes,
        "started_at": now.isoformat(),
    }
    _set_boost_state(request, boost_state)

    # Notify heating service to pause automatic control
    from app.services.heating import heating_service
    heating_service.set_boost_active(True)

    # Audit log
    async with session.begin_nested():
        session.add(AuditLog(
            action="boost.activate",
            new_value=f"{minutes} minutes",
            user_id=_user.id,
            user_email=_user.email,
        ))
    await session.commit()

    # WebSocket broadcast
    ws_data = {
        "type": "boost_update",
        "data": {
            "active": True,
            "remaining_minutes": minutes,
            "total_minutes": minutes,
            "started_at": now.isoformat(),
            "estimated_gas_cost_tl": estimated_cost,
        },
    }
    await ws_manager.broadcast(ws_data)

    return ApiResponse(
        success=True,
        data=BoostStatusResponse(
            active=True,
            remaining_minutes=minutes,
            total_minutes=minutes,
            started_at=now.isoformat(),
            estimated_gas_cost_tl=estimated_cost,
        ).model_dump(),
    )


@router.delete("/boost")
async def cancel_boost(
    request: Request,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Boost modunu iptal et.

    Relay cihazina iptal komutu gonderir, kombi kapanir
    ve onceki moda (auto/manual) donulur.
    """
    mqtt_service = getattr(request.app.state, "mqtt", None)
    if mqtt_service is None:
        raise HTTPException(
            status_code=503,
            detail="MQTT servisi hazir degil",
        )

    boost_state = _get_boost_state(request)
    if not boost_state.get("active", False):
        return ApiResponse(
            success=True,
            data=BoostStatusResponse(active=False).model_dump(),
        )

    # Send boost cancel command
    mqtt_service.publish_command_to_relays("boost_cancel", {})
    logger.info("Boost iptal komutu gonderildi")

    # Clear boost state
    _set_boost_state(request, {
        "active": False,
        "remaining_minutes": 0,
        "total_minutes": 0,
        "started_at": None,
    })

    # Notify heating service to resume
    from app.services.heating import heating_service
    heating_service.set_boost_active(False)

    # Audit log
    async with session.begin_nested():
        session.add(AuditLog(
            action="boost.cancel",
            old_value=f"{boost_state.get('total_minutes', 0)} minutes",
            user_id=_user.id,
            user_email=_user.email,
        ))
    await session.commit()

    # WebSocket broadcast
    await ws_manager.broadcast({
        "type": "boost_update",
        "data": {"active": False, "remaining_minutes": 0, "total_minutes": 0},
    })

    return ApiResponse(
        success=True,
        data=BoostStatusResponse(active=False).model_dump(),
    )


@router.get("/boost")
async def get_boost_status(
    request: Request,
    _user: User = Depends(get_current_user),
):
    """Boost modu durumunu dondur."""
    boost_state = _get_boost_state(request)

    # Calculate remaining time based on started_at
    remaining = 0
    if boost_state.get("active") and boost_state.get("started_at"):
        try:
            started = datetime.fromisoformat(boost_state["started_at"])
            elapsed = (datetime.now(timezone.utc) - started).total_seconds() / 60.0
            total = boost_state.get("total_minutes", 0)
            remaining = max(0, int(total - elapsed))

            # Auto-deactivate if timer expired
            if remaining <= 0:
                boost_state["active"] = False
                boost_state["remaining_minutes"] = 0
                _set_boost_state(request, boost_state)

                from app.services.heating import heating_service
                heating_service.set_boost_active(False)
        except Exception:
            remaining = boost_state.get("remaining_minutes", 0)

    estimated_cost = None
    if boost_state.get("active"):
        estimated_cost = _estimate_gas_cost(
            boost_state.get("total_minutes", 0), request
        )

    return ApiResponse(
        success=True,
        data=BoostStatusResponse(
            active=boost_state.get("active", False),
            remaining_minutes=remaining,
            total_minutes=boost_state.get("total_minutes", 0),
            started_at=boost_state.get("started_at"),
            estimated_gas_cost_tl=estimated_cost,
        ).model_dump(),
    )


# ============================================================
# Heating Profiles CRUD
# ============================================================


@router.get("/profiles")
async def get_profiles(
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Tum isitma profillerini listele."""
    from app.models.database import HeatingProfile

    result = await session.execute(
        select(HeatingProfile).order_by(HeatingProfile.sort_order, HeatingProfile.id)
    )
    profiles = result.scalars().all()
    return ApiResponse(
        success=True,
        data=[HeatingProfileResponse.model_validate(p).model_dump() for p in profiles],
    )


@router.post("/profiles")
async def create_profile(
    body: HeatingProfileCreate,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Yeni profil olustur."""
    from app.models.database import HeatingProfile

    profile = HeatingProfile(
        name=body.name,
        icon=body.icon,
        target_temp=body.target_temp,
        hysteresis=body.hysteresis,
        sort_order=body.sort_order,
    )
    session.add(profile)
    await session.commit()
    await session.refresh(profile)
    return ApiResponse(
        success=True,
        data=HeatingProfileResponse.model_validate(profile).model_dump(),
    )


@router.put("/profiles/{profile_id}")
async def update_profile(
    profile_id: int,
    body: HeatingProfileUpdate,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Profili guncelle."""
    from app.models.database import HeatingProfile

    result = await session.execute(
        select(HeatingProfile).where(HeatingProfile.id == profile_id)
    )
    profile = result.scalar_one_or_none()
    if not profile:
        raise HTTPException(status_code=404, detail="Profil bulunamadi")

    update_data = body.model_dump(exclude_none=True)
    for key, value in update_data.items():
        setattr(profile, key, value)

    await session.commit()
    await session.refresh(profile)
    return ApiResponse(
        success=True,
        data=HeatingProfileResponse.model_validate(profile).model_dump(),
    )


@router.delete("/profiles/{profile_id}")
async def delete_profile(
    profile_id: int,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Profili sil (varsayilan profiller silinemez)."""
    from app.models.database import HeatingProfile

    result = await session.execute(
        select(HeatingProfile).where(HeatingProfile.id == profile_id)
    )
    profile = result.scalar_one_or_none()
    if not profile:
        raise HTTPException(status_code=404, detail="Profil bulunamadi")
    if profile.is_default:
        raise HTTPException(status_code=400, detail="Varsayilan profiller silinemez")

    await session.delete(profile)
    await session.commit()
    return ApiResponse(success=True, data=None)
