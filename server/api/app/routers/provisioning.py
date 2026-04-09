"""Device provisioning endpoints for ESP32 MQTT authentication.

Handles device registration, credential generation, and lifecycle
management. ESP32 devices call POST /register on first boot to
obtain MQTT credentials stored in NVS.

Security model:
- First-time registration is open (any ESP32 can register)
- Admin endpoints require JWT authentication
- MQTT passwords are bcrypt-hashed in PostgreSQL
- Device tokens are JWTs with 1-year expiry
- All actions logged to audit_log
"""

import logging
import os
import re
import time
from collections import defaultdict
from datetime import datetime, timedelta, timezone

import hashlib

from fastapi import APIRouter, Depends, HTTPException, Request
from jose import jwt
from pydantic import BaseModel, Field, field_validator
from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.database import AuditLog, Device, User, get_session
from app.models.schemas import ApiResponse
from app.routers.auth import get_current_user
from app.services.mqtt_credentials import (
    add_acl_entry,
    add_to_mosquitto,
    generate_credentials,
    generate_mqtt_username,
    remove_from_mosquitto,
)

logger = logging.getLogger("tonbil.provisioning")
router = APIRouter()

def _hash_secret(secret: str) -> str:
    """SHA256 hash for DB storage (MQTT auth is done by Mosquitto passwd file)."""
    return hashlib.sha256(secret.encode()).hexdigest()

# Rate limiting: IP bazli kayit denemesi takibi
_register_attempts: dict[str, list[float]] = defaultdict(list)
REGISTER_RATE_LIMIT = 3  # Dakikada max kayit denemesi
PROVISIONING_SECRET = os.getenv("PROVISIONING_SECRET", "tonbil-provision-2024")

# Device token expiry
DEVICE_TOKEN_EXPIRE_DAYS = 365

# Allowed device_id pattern: alphanumeric, dash, underscore, 3-50 chars
DEVICE_ID_PATTERN = re.compile(r"^[a-zA-Z0-9_-]{3,50}$")


# --- Request/Response Schemas ---


class RegisterRequest(BaseModel):
    """ESP32 device registration request."""

    device_id: str = Field(min_length=3, max_length=50)
    type: str = Field(pattern="^(sensor|relay|combo)$")
    firmware_version: str = Field(default="1.0.0", max_length=50)

    @field_validator("device_id")
    @classmethod
    def validate_device_id(cls, v: str) -> str:
        if not DEVICE_ID_PATTERN.match(v):
            raise ValueError(
                "device_id must be 3-50 chars, alphanumeric/dash/underscore only"
            )
        return v


class ProvisioningTopics(BaseModel):
    telemetry: str
    status: str
    command: str
    config: str


class ProvisioningData(BaseModel):
    mqtt_username: str
    mqtt_password: str
    mqtt_host: str
    mqtt_port: int
    device_token: str
    topics: ProvisioningTopics


class DeviceProvisioningInfo(BaseModel):
    device_id: str
    type: str
    mqtt_username: str | None = None
    firmware_version: str | None = None
    provisioned_at: datetime | None = None
    last_seen: datetime | None = None
    is_online: bool = False
    token_active: bool = False


# --- Helper Functions ---


def _create_device_token(device_id: str, device_type: str) -> str:
    """Create a JWT token for device authentication.

    Token contains device_id and type claims, expires in 1 year.
    """
    expire = datetime.now(timezone.utc) + timedelta(days=DEVICE_TOKEN_EXPIRE_DAYS)
    payload = {
        "sub": device_id,
        "type": device_type,
        "scope": "device",
        "exp": expire,
        "iat": datetime.now(timezone.utc),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


async def _log_audit(
    session: AsyncSession,
    device_id: str,
    action: str,
    old_value: str | None = None,
    new_value: str | None = None,
) -> None:
    """Write an entry to the audit log."""
    session.add(
        AuditLog(
            device_id=device_id,
            action=action,
            old_value=old_value,
            new_value=new_value,
        )
    )


# --- Endpoints ---


@router.post("/register")
async def register_device(
    body: RegisterRequest,
    request: Request,
    session: AsyncSession = Depends(get_session),
):
    """Register an ESP32 device and provision MQTT credentials.

    This endpoint is open (no auth) so ESP32 devices can self-register
    on first boot. If the device already exists and has credentials,
    returns 409 Conflict.

    Security:
    - IP-based rate limiting (max 3 registrations per minute per IP)
    - Optional X-Provision-Key header validation

    Flow:
    1. Validate device_id format
    2. Check if device already provisioned
    3. Generate MQTT username + password
    4. Add credentials to Mosquitto passwd file
    5. Add ACL entry for device-specific topics
    6. Create/update device record in PostgreSQL
    7. Generate device JWT token
    8. Store hashed password and token in DB
    9. Return credentials (one-time, device stores in NVS)
    """
    # Rate limit kontrolu
    client_ip = request.client.host if request.client else "unknown"
    now_ts = time.time()
    _register_attempts[client_ip] = [
        t for t in _register_attempts[client_ip] if now_ts - t < 60
    ]
    if len(_register_attempts[client_ip]) >= REGISTER_RATE_LIMIT:
        logger.warning("Rate limit asildi: IP=%s", client_ip)
        raise HTTPException(status_code=429, detail="Cok fazla kayit denemesi. 1 dakika bekleyin.")
    _register_attempts[client_ip].append(now_ts)

    # Provisioning secret header kontrolu (opsiyonel ama varsa dogru olmali)
    provision_key = request.headers.get("X-Provision-Key", "")
    if provision_key and provision_key != PROVISIONING_SECRET:
        logger.warning("Gecersiz provision key: IP=%s", client_ip)
        raise HTTPException(status_code=403, detail="Gecersiz provision key")

    device_id = body.device_id
    device_type = body.type

    logger.info(
        "Provisioning request: device_id=%s type=%s firmware=%s",
        device_id,
        device_type,
        body.firmware_version,
    )

    # Check if device already provisioned
    result = await session.execute(
        select(Device).where(Device.device_id == device_id)
    )
    existing = result.scalar_one_or_none()

    if existing and existing.mqtt_user:
        # Device already provisioned — re-provision with new credentials
        # (device may have been reflashed and lost stored credentials)
        logger.info("Re-provisioning existing device: %s", device_id)
        # Deactivate old tokens
        await session.execute(
            text(
                "UPDATE device_tokens SET is_active = false "
                "WHERE device_id = :did"
            ),
            {"did": device_id},
        )

    # Generate MQTT credentials
    creds = generate_credentials(device_id)
    mqtt_username = creds["username"]
    mqtt_password = creds["password"]
    topics = creds["topics"]

    # Add to Mosquitto passwd file
    if not add_to_mosquitto(mqtt_username, mqtt_password):
        logger.error("Failed to add MQTT credentials for: %s", device_id)
        raise HTTPException(
            status_code=500,
            detail={
                "code": "MQTT_CREDENTIAL_FAILED",
                "message": "Failed to create MQTT credentials",
            },
        )

    # Add ACL entry
    add_acl_entry(mqtt_username, device_id)

    # Generate device token (JWT)
    device_token = _create_device_token(device_id, device_type)

    # Hash password and token for DB storage
    password_hash = _hash_secret(mqtt_password)
    token_hash = _hash_secret(device_token[:72])  # bcrypt max 72 chars

    now = datetime.now(timezone.utc)

    # Create or update device record
    if existing:
        existing.mqtt_user = mqtt_username
        existing.firmware_version = body.firmware_version
        existing.type = device_type

        # Update provisioning columns via raw SQL (ORM model may not have them yet)
        await session.execute(
            text(
                "UPDATE devices SET "
                "mqtt_username = :mqtt_username, "
                "mqtt_password_hash = :password_hash, "
                "device_token = :device_token, "
                "provisioned_at = :now "
                "WHERE device_id = :device_id"
            ),
            {
                "mqtt_username": mqtt_username,
                "password_hash": password_hash,
                "device_token": device_token[:50],  # store prefix for identification
                "now": now,
                "device_id": device_id,
            },
        )
    else:
        # Insert new device
        new_device = Device(
            device_id=device_id,
            name=f"{device_type.capitalize()} {device_id}",
            type=device_type,
            mqtt_user=mqtt_username,
            firmware_version=body.firmware_version,
        )
        session.add(new_device)
        await session.flush()  # get the insert to complete before raw SQL

        await session.execute(
            text(
                "UPDATE devices SET "
                "mqtt_username = :mqtt_username, "
                "mqtt_password_hash = :password_hash, "
                "device_token = :device_token, "
                "provisioned_at = :now "
                "WHERE device_id = :device_id"
            ),
            {
                "mqtt_username": mqtt_username,
                "password_hash": password_hash,
                "device_token": device_token[:50],
                "now": now,
                "device_id": device_id,
            },
        )

    # Deactivate old tokens for this device
    await session.execute(
        text(
            "UPDATE device_tokens SET is_active = false "
            "WHERE device_id = :did"
        ),
        {"did": device_id},
    )

    # Store new token record
    await session.execute(
        text(
            "INSERT INTO device_tokens (device_id, token_hash, expires_at, is_active) "
            "VALUES (:device_id, :token_hash, :expires_at, true)"
        ),
        {
            "device_id": device_id,
            "token_hash": token_hash,
            "expires_at": now + timedelta(days=DEVICE_TOKEN_EXPIRE_DAYS),
        },
    )

    # Audit log
    await _log_audit(
        session,
        device_id,
        "device.provisioned",
        new_value=f"type={device_type} mqtt_user={mqtt_username}",
    )

    await session.commit()

    # Determine MQTT host to return to device
    # Use the external-facing MQTT host, not the Docker internal name
    mqtt_host = settings.mqtt_external_host

    logger.info(
        "Device provisioned: %s (type=%s, mqtt_user=%s)",
        device_id,
        device_type,
        mqtt_username,
    )

    return ApiResponse(
        success=True,
        data=ProvisioningData(
            mqtt_username=mqtt_username,
            mqtt_password=mqtt_password,
            mqtt_host=mqtt_host,
            mqtt_port=settings.mqtt_external_port,
            device_token=device_token,
            topics=ProvisioningTopics(**topics),
        ).model_dump(),
    )


@router.get("/devices")
async def list_provisioned_devices(
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """List all registered devices with provisioning status.

    Admin only. Returns device info including MQTT username,
    provisioning timestamp, and whether token is active.
    """
    result = await session.execute(
        text(
            """
            SELECT
                d.device_id,
                d.type,
                d.mqtt_user AS mqtt_username,
                d.firmware_version,
                d.provisioned_at,
                d.last_seen,
                d.is_online,
                COALESCE(
                    (SELECT true FROM device_tokens dt
                     WHERE dt.device_id = d.device_id
                       AND dt.is_active = true
                       AND (dt.expires_at IS NULL OR dt.expires_at > NOW())
                     LIMIT 1),
                    false
                ) AS token_active
            FROM devices d
            ORDER BY d.created_at DESC
            """
        )
    )
    rows = result.mappings().all()

    devices = [
        DeviceProvisioningInfo(
            device_id=row["device_id"],
            type=row["type"],
            mqtt_username=row["mqtt_username"],
            firmware_version=row["firmware_version"],
            provisioned_at=row["provisioned_at"],
            last_seen=row["last_seen"],
            is_online=row["is_online"],
            token_active=row["token_active"],
        ).model_dump()
        for row in rows
    ]

    return ApiResponse(success=True, data=devices)


@router.delete("/devices/{device_id}")
async def delete_provisioned_device(
    device_id: str,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Remove a device's MQTT credentials and DB entry.

    Admin only. Revokes MQTT access and deactivates all tokens.
    The device will need to be re-provisioned to connect again.
    """
    # Validate device_id
    if not DEVICE_ID_PATTERN.match(device_id):
        raise HTTPException(status_code=400, detail="Invalid device_id format")

    # Check device exists
    result = await session.execute(
        select(Device).where(Device.device_id == device_id)
    )
    device = result.scalar_one_or_none()

    if not device:
        raise HTTPException(
            status_code=404,
            detail={"code": "DEVICE_NOT_FOUND", "message": f"Device {device_id} not found"},
        )

    mqtt_username = generate_mqtt_username(device_id)

    # Remove from Mosquitto
    remove_from_mosquitto(mqtt_username)

    # Deactivate all tokens
    await session.execute(
        text(
            "UPDATE device_tokens SET is_active = false "
            "WHERE device_id = :did"
        ),
        {"did": device_id},
    )

    # Clear provisioning fields on device record
    await session.execute(
        text(
            "UPDATE devices SET "
            "mqtt_username = NULL, "
            "mqtt_password_hash = NULL, "
            "device_token = NULL, "
            "provisioned_at = NULL, "
            "mqtt_user = NULL "
            "WHERE device_id = :did"
        ),
        {"did": device_id},
    )

    # Audit log
    await _log_audit(
        session,
        device_id,
        "device.deprovisioned",
        old_value=f"mqtt_user={mqtt_username}",
    )

    await session.commit()

    logger.info("Device deprovisioned: %s", device_id)

    return ApiResponse(
        success=True,
        data={"message": f"Device {device_id} deprovisioned", "device_id": device_id},
    )


@router.post("/regenerate/{device_id}")
async def regenerate_credentials(
    device_id: str,
    session: AsyncSession = Depends(get_session),
    _user: User = Depends(get_current_user),
):
    """Generate new MQTT password for an existing device.

    Admin only. Invalidates old MQTT password and token,
    generates new ones. Device must re-provision (clear NVS
    credentials and call /register or receive new creds OTA).
    """
    if not DEVICE_ID_PATTERN.match(device_id):
        raise HTTPException(status_code=400, detail="Invalid device_id format")

    # Check device exists
    result = await session.execute(
        select(Device).where(Device.device_id == device_id)
    )
    device = result.scalar_one_or_none()

    if not device:
        raise HTTPException(
            status_code=404,
            detail={"code": "DEVICE_NOT_FOUND", "message": f"Device {device_id} not found"},
        )

    mqtt_username = generate_mqtt_username(device_id)

    # Generate new credentials
    creds = generate_credentials(device_id)
    new_password = creds["password"]
    topics = creds["topics"]

    # Update Mosquitto passwd file
    if not add_to_mosquitto(mqtt_username, new_password):
        raise HTTPException(
            status_code=500,
            detail={
                "code": "MQTT_CREDENTIAL_FAILED",
                "message": "Failed to update MQTT credentials",
            },
        )

    # Generate new device token
    device_token = _create_device_token(device_id, device.type)

    # Hash for storage
    password_hash = pwd_context.hash(new_password)
    token_hash = _hash_secret(device_token[:72])

    now = datetime.now(timezone.utc)

    # Update device record
    device.mqtt_user = mqtt_username
    await session.execute(
        text(
            "UPDATE devices SET "
            "mqtt_username = :mqtt_username, "
            "mqtt_password_hash = :password_hash, "
            "device_token = :device_token, "
            "provisioned_at = :now "
            "WHERE device_id = :device_id"
        ),
        {
            "mqtt_username": mqtt_username,
            "password_hash": password_hash,
            "device_token": device_token[:50],
            "now": now,
            "device_id": device_id,
        },
    )

    # Deactivate old tokens, create new one
    await session.execute(
        text(
            "UPDATE device_tokens SET is_active = false "
            "WHERE device_id = :did"
        ),
        {"did": device_id},
    )

    await session.execute(
        text(
            "INSERT INTO device_tokens (device_id, token_hash, expires_at, is_active) "
            "VALUES (:device_id, :token_hash, :expires_at, true)"
        ),
        {
            "device_id": device_id,
            "token_hash": token_hash,
            "expires_at": now + timedelta(days=DEVICE_TOKEN_EXPIRE_DAYS),
        },
    )

    # Audit log
    await _log_audit(
        session,
        device_id,
        "device.credentials_regenerated",
        new_value=f"mqtt_user={mqtt_username}",
    )

    await session.commit()

    mqtt_host = settings.mqtt_external_host

    logger.info("Credentials regenerated for device: %s", device_id)

    return ApiResponse(
        success=True,
        data=ProvisioningData(
            mqtt_username=mqtt_username,
            mqtt_password=new_password,
            mqtt_host=mqtt_host,
            mqtt_port=settings.mqtt_external_port,
            device_token=device_token,
            topics=ProvisioningTopics(**topics),
        ).model_dump(),
    )
