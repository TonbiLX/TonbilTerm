"""Kimlik dogrulama endpoint'leri - JWT tabanli."""

import logging
from datetime import datetime, timedelta, timezone

import bcrypt as _bcrypt

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from jose import JWTError, jwt
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.database import AuditLog, User, get_session
from app.models.schemas import ApiResponse, LoginRequest, RegisterRequest, UpdateUserRequest, UserResponse

logger = logging.getLogger("tonbil.auth")
router = APIRouter()


def hash_password(password: str) -> str:
    """Sifre hash'le (bcrypt)."""
    return _bcrypt.hashpw(password.encode("utf-8"), _bcrypt.gensalt()).decode("utf-8")


def verify_password(password: str, hashed: str) -> bool:
    """Sifre dogrula (bcrypt)."""
    try:
        return _bcrypt.checkpw(password.encode("utf-8"), hashed.encode("utf-8"))
    except Exception:
        return False

COOKIE_NAME = "tonbil_token"


def create_token(user_id: int, email: str) -> str:
    """JWT token olustur."""
    expire = datetime.now(timezone.utc) + timedelta(hours=settings.jwt_expire_hours)
    payload = {
        "sub": str(user_id),
        "email": email,
        "exp": expire,
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def verify_token(token: str) -> dict:
    """JWT token dogrula, payload dondur."""
    try:
        payload = jwt.decode(
            token, settings.jwt_secret, algorithms=[settings.jwt_algorithm]
        )
        return payload
    except JWTError:
        raise HTTPException(status_code=401, detail="Gecersiz veya suresi dolmus token")


async def get_current_user(
    request: Request,
    session: AsyncSession = Depends(get_session),
) -> User:
    """Cookie'den JWT oku, kullaniciyi dogrula."""
    token = request.cookies.get(COOKIE_NAME)
    if not token:
        raise HTTPException(status_code=401, detail="Giris yapmaniz gerekiyor")

    payload = verify_token(token)
    user_id = int(payload["sub"])

    result = await session.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()

    if not user or not user.is_active:
        raise HTTPException(status_code=401, detail="Kullanici bulunamadi veya deaktif")

    return user


async def require_admin(user: User = Depends(get_current_user)) -> User:
    """Sadece admin rolune sahip kullanicilara izin verir."""
    if user.role != "admin":
        raise HTTPException(status_code=403, detail="Bu islem icin admin yetkisi gerekli")
    return user


@router.post("/login")
async def login(
    body: LoginRequest,
    request: Request,
    response: Response,
    session: AsyncSession = Depends(get_session),
):
    """Kullanici girisi - JWT cookie dondurur."""
    result = await session.execute(select(User).where(User.email == body.email))
    user = result.scalar_one_or_none()

    if not user or not verify_password(body.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Gecersiz email veya sifre")

    if not user.is_active:
        raise HTTPException(status_code=403, detail="Hesap deaktif edilmis")

    token = create_token(user.id, user.email)

    # Cookie: hem HTTP (LAN) hem HTTPS (dış proxy) ile çalışmalı
    # SameSite=none + Secure=true gerekli (HTTPS proxy arkası)
    # LAN HTTP erişimi için de ayrı cookie set et
    response.set_cookie(
        key=COOKIE_NAME,
        value=token,
        httponly=True,
        samesite="lax",
        max_age=settings.jwt_expire_hours * 3600,
        secure=False,
        path="/",
    )

    logger.info("Kullanici giris yapti: %s", user.email)

    return ApiResponse(
        success=True,
        data=UserResponse.model_validate(user).model_dump(),
    )


@router.post("/logout")
async def logout(response: Response):
    """Oturumu kapat - cookie sil."""
    response.delete_cookie(COOKIE_NAME)
    return ApiResponse(success=True, data={"message": "Cikis yapildi"})


@router.post("/change-password")
async def change_password(
    request: Request,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(get_current_user),
):
    """Sifre degistir."""
    body = await request.json()
    old_password = body.get("old_password", "")
    new_password = body.get("new_password", "")

    if not old_password or not new_password:
        raise HTTPException(status_code=400, detail="Eski ve yeni sifre gerekli")

    if len(new_password) < 6:
        raise HTTPException(status_code=400, detail="Yeni sifre en az 6 karakter olmali")

    if not verify_password(old_password, user.password_hash):
        raise HTTPException(status_code=401, detail="Mevcut sifre hatali")

    new_hash = hash_password(new_password)
    user.password_hash = new_hash
    await session.commit()

    logger.info("Kullanici sifre degistirdi: %s", user.email)
    return ApiResponse(success=True, data={"message": "Sifre basariyla degistirildi"})


@router.get("/me")
async def get_me(user: User = Depends(get_current_user)):
    """Mevcut kullanici bilgisini dondur."""
    return ApiResponse(
        success=True,
        data=UserResponse.model_validate(user).model_dump(),
    )


@router.get("/users")
async def list_users(
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user),
):
    """Kullanici listesi: Admin tum listeyi gorebilir, normal kullanici sadece kendini."""
    if current_user.role == "admin":
        result = await session.execute(select(User).order_by(User.id))
        users = result.scalars().all()
        return ApiResponse(
            success=True,
            data=[UserResponse.model_validate(u).model_dump() for u in users],
        )
    # Normal kullanici: sadece kendi bilgisi
    return ApiResponse(
        success=True,
        data=[UserResponse.model_validate(current_user).model_dump()],
    )


@router.post("/register", status_code=201)
async def register_user(
    body: RegisterRequest,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(require_admin),
):
    """Yeni kullanici olustur (sadece admin olusturabilir)."""
    result = await session.execute(select(User).where(User.email == body.email))
    if result.scalar_one_or_none() is not None:
        raise HTTPException(status_code=409, detail="Bu email adresi zaten kayitli")

    new_user = User(
        email=body.email,
        password_hash=hash_password(body.password),
        display_name=body.display_name,
        is_active=True,
    )
    session.add(new_user)
    session.add(AuditLog(
        action="user.create",
        old_value="",
        new_value=f"{body.email} ({body.display_name})",
        user_id=_user.id,
        user_email=_user.email,
    ))
    await session.commit()
    await session.refresh(new_user)

    logger.info("Yeni kullanici olusturuldu: %s (olusturan: %s)", new_user.email, _user.email)
    return ApiResponse(
        success=True,
        data=UserResponse.model_validate(new_user).model_dump(),
    )


@router.delete("/users/{user_id}")
async def delete_user(
    user_id: int,
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(require_admin),
):
    """Kullanici sil. Sadece admin yapabilir, admin kendini silemez."""
    if user_id == current_user.id:
        raise HTTPException(status_code=400, detail="Kendi hesabinizi silemezsiniz")

    result = await session.execute(select(User).where(User.id == user_id))
    target = result.scalar_one_or_none()
    if target is None:
        raise HTTPException(status_code=404, detail="Kullanici bulunamadi")

    deleted_email = target.email
    session.add(AuditLog(
        action="user.delete",
        old_value=f"{deleted_email} (id={user_id})",
        new_value="",
        user_id=current_user.id,
        user_email=current_user.email,
    ))
    await session.delete(target)
    await session.commit()

    logger.info("Kullanici silindi: %s (silen: %s)", deleted_email, current_user.email)
    return ApiResponse(success=True, data={"message": "Kullanici silindi"})


@router.put("/users/{user_id}")
async def update_user(
    user_id: int,
    body: UpdateUserRequest,
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user),
):
    """Kullanici guncelle. Admin herkesi duzenleyebilir; normal kullanici sadece kendini."""
    # Yetki kontrolu: normal kullanici baskasini duzenleyemez
    if current_user.role != "admin" and current_user.id != user_id:
        raise HTTPException(status_code=403, detail="Baska kullanicilari duzenleme yetkiniz yok")

    result = await session.execute(select(User).where(User.id == user_id))
    target = result.scalar_one_or_none()
    if target is None:
        raise HTTPException(status_code=404, detail="Kullanici bulunamadi")

    changes: list[str] = []

    if body.display_name is not None:
        old_name = target.display_name
        target.display_name = body.display_name
        changes.append(f"display_name: {old_name!r} -> {body.display_name!r}")

    if body.is_active is not None:
        # is_active degisikligi sadece admin yapabilir
        if current_user.role != "admin":
            raise HTTPException(status_code=403, detail="is_active alanini degistirmek icin admin yetkisi gerekli")
        old_active = target.is_active
        target.is_active = body.is_active
        changes.append(f"is_active: {old_active} -> {body.is_active}")

    if body.role is not None:
        # rol degisikligi sadece admin yapabilir
        if current_user.role != "admin":
            raise HTTPException(status_code=403, detail="Rol degistirmek icin admin yetkisi gerekli")
        old_role = target.role
        target.role = body.role
        changes.append(f"role: {old_role!r} -> {body.role!r}")

    if body.email is not None and body.email != target.email:
        # Uniqueness kontrolu
        dup = await session.execute(
            select(User).where(User.email == body.email, User.id != user_id)
        )
        if dup.scalar_one_or_none() is not None:
            raise HTTPException(status_code=409, detail="Bu email adresi zaten kullaniliyor")
        old_email = target.email
        target.email = body.email
        changes.append(f"email: {old_email!r} -> {body.email!r}")

    if changes:
        session.add(AuditLog(
            action="user.update",
            old_value="; ".join(changes),
            new_value=f"user_id={user_id}",
            user_id=current_user.id,
            user_email=current_user.email,
        ))

    await session.commit()
    await session.refresh(target)

    logger.info("Kullanici guncellendi: id=%s, degisiklikler=%s (guncelleyen: %s)", user_id, changes, current_user.email)
    return ApiResponse(
        success=True,
        data=UserResponse.model_validate(target).model_dump(),
    )
