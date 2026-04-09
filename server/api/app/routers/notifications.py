"""Push notification yonetimi endpoint'leri - FCM token kayit ve bildirim tercihleri."""

import logging

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.database import User, get_session
from app.models.fcm import FCMToken
from app.models.notification_prefs import NotificationPreferences
from app.models.schemas import ApiResponse
from app.routers.auth import get_current_user

logger = logging.getLogger("tonbil.notifications")
router = APIRouter()


# --- Request/Response Schemas ---


class RegisterTokenRequest(BaseModel):
    """FCM token kayit istegi."""

    token: str = Field(min_length=10, max_length=512, description="FCM device token")
    device_name: str | None = Field(
        default=None,
        max_length=128,
        description="Cihaz ismi (ornek: 'iPhone 15', 'Chrome - Windows')",
    )


class UnregisterTokenRequest(BaseModel):
    """FCM token silme istegi."""

    token: str = Field(min_length=10, max_length=512)


class NotificationPrefsResponse(BaseModel):
    """Bildirim tercihleri yaniti."""

    temp_low_alert: bool = True
    temp_high_alert: bool = True
    relay_change_alert: bool = True
    sensor_offline_alert: bool = True
    temp_low_threshold: float = 16.0
    temp_high_threshold: float = 28.0

    model_config = {"from_attributes": True}


class NotificationPrefsUpdate(BaseModel):
    """Bildirim tercihleri guncelleme."""

    temp_low_alert: bool | None = None
    temp_high_alert: bool | None = None
    relay_change_alert: bool | None = None
    sensor_offline_alert: bool | None = None
    temp_low_threshold: float | None = Field(default=None, ge=0.0, le=40.0)
    temp_high_threshold: float | None = Field(default=None, ge=0.0, le=50.0)


# --- Endpoints ---


@router.post("/register")
async def register_token(
    body: RegisterTokenRequest,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(get_current_user),
):
    """FCM push notification token'i kaydet.

    Ayni token varsa updated_at guncellenir (idempotent).
    Bir kullanici birden fazla cihaz kaydedebilir.
    """
    # Token zaten var mi kontrol et
    existing = await session.execute(
        select(FCMToken).where(FCMToken.token == body.token)
    )
    fcm_token = existing.scalar_one_or_none()

    if fcm_token:
        # Token varsa sahipligini guncelle (farkli kullanici olabilir)
        fcm_token.user_id = user.id
        fcm_token.device_name = body.device_name or fcm_token.device_name
        logger.info("FCM token guncellendi: user=%d, device=%s", user.id, body.device_name)
    else:
        fcm_token = FCMToken(
            user_id=user.id,
            token=body.token,
            device_name=body.device_name,
        )
        session.add(fcm_token)
        logger.info("FCM token kaydedildi: user=%d, device=%s", user.id, body.device_name)

    await session.commit()

    return ApiResponse(success=True, data={"message": "Token kaydedildi"})


@router.delete("/unregister")
async def unregister_token(
    body: UnregisterTokenRequest,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(get_current_user),
):
    """FCM token'i sil (cihaz logout veya bildirim kapatma).

    Sadece token sahibi silebilir.
    """
    result = await session.execute(
        delete(FCMToken).where(
            FCMToken.token == body.token,
            FCMToken.user_id == user.id,
        )
    )
    await session.commit()

    if result.rowcount == 0:
        raise HTTPException(status_code=404, detail="Token bulunamadi")

    logger.info("FCM token silindi: user=%d", user.id)
    return ApiResponse(success=True, data={"message": "Token silindi"})


@router.get("/preferences")
async def get_preferences(
    session: AsyncSession = Depends(get_session),
    user: User = Depends(get_current_user),
):
    """Kullanicinin bildirim tercihlerini getir.

    Tercih kaydedilmemisse varsayilan degerler dondurulur.
    """
    result = await session.execute(
        select(NotificationPreferences).where(
            NotificationPreferences.user_id == user.id
        )
    )
    prefs = result.scalar_one_or_none()

    if prefs:
        data = NotificationPrefsResponse.model_validate(prefs).model_dump()
    else:
        # Varsayilan degerler
        data = NotificationPrefsResponse().model_dump()

    return ApiResponse(success=True, data=data)


@router.put("/preferences")
async def update_preferences(
    body: NotificationPrefsUpdate,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(get_current_user),
):
    """Bildirim tercihlerini guncelle (upsert).

    Sadece gonderilen alanlar guncellenir, gonderilmeyenler degismez.
    """
    # Esik degeri validasyonu: low < high
    if body.temp_low_threshold is not None and body.temp_high_threshold is not None:
        if body.temp_low_threshold >= body.temp_high_threshold:
            raise HTTPException(
                status_code=400,
                detail="Dusuk sicaklik esigi, yuksek sicaklik esiginden kucuk olmali",
            )

    result = await session.execute(
        select(NotificationPreferences).where(
            NotificationPreferences.user_id == user.id
        )
    )
    prefs = result.scalar_one_or_none()

    if not prefs:
        prefs = NotificationPreferences(user_id=user.id)
        session.add(prefs)

    # Sadece gonderilen alanlari guncelle
    update_data = body.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(prefs, field, value)

    # Cross-validate: mevcut degerlerle kontrol et
    if prefs.temp_low_threshold >= prefs.temp_high_threshold:
        raise HTTPException(
            status_code=400,
            detail="Dusuk sicaklik esigi, yuksek sicaklik esiginden kucuk olmali",
        )

    await session.commit()

    logger.info("Bildirim tercihleri guncellendi: user=%d", user.id)
    return ApiResponse(
        success=True,
        data=NotificationPrefsResponse.model_validate(prefs).model_dump(),
    )
