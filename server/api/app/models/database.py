"""SQLAlchemy async engine ve session yonetimi."""

from datetime import datetime

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    Column,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
    func,
)
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase, relationship

from app.config import settings

engine = create_async_engine(
    settings.database_url,
    echo=settings.api_debug,
    pool_size=10,
    max_overflow=5,
    pool_pre_ping=True,
)

async_session = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


# --- ORM Modelleri ---


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True)
    email = Column(String(255), unique=True, nullable=False)
    password_hash = Column(String(255), nullable=False)
    display_name = Column(String(100))
    role = Column(String(20), nullable=False, default="user")
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())


class Room(Base):
    __tablename__ = "rooms"

    id = Column(Integer, primary_key=True)
    name = Column(String(100), nullable=False)
    weight = Column(Float, nullable=False, default=0.5)
    min_temp = Column(Float, nullable=False, default=5.0)
    icon = Column(String(50), default="room")
    sort_order = Column(Integer, default=0)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    devices = relationship("Device", back_populates="room")


class Device(Base):
    __tablename__ = "devices"

    id = Column(Integer, primary_key=True)
    device_id = Column(String(50), unique=True, nullable=False)
    name = Column(String(100))
    room_id = Column(Integer, ForeignKey("rooms.id", ondelete="SET NULL"))
    type = Column(String(20), nullable=False)
    mqtt_user = Column(String(100))
    last_seen = Column(DateTime(timezone=True))
    firmware_version = Column(String(50))
    ip_address = Column(String(45))
    is_online = Column(Boolean, default=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    __table_args__ = (
        CheckConstraint("type IN ('sensor', 'relay', 'combo')", name="ck_device_type"),
    )

    room = relationship("Room", back_populates="devices")


class HeatingConfig(Base):
    __tablename__ = "heating_config"

    id = Column(Integer, primary_key=True)
    target_temp = Column(Float, nullable=False, default=22.0)
    hysteresis = Column(Float, nullable=False, default=0.5)
    min_cycle_min = Column(Integer, nullable=False, default=3)
    mode = Column(String(20), nullable=False, default="auto")
    strategy = Column(String(30), nullable=False, default="weighted_avg")
    gas_price_per_m3 = Column(Float, default=7.0)
    floor_area_m2 = Column(Float, default=100.0)
    boiler_power_kw = Column(Float, default=30.0)
    flow_temp = Column(Float, default=60.0)
    boiler_brand = Column(String(100), default="ECA Proteus Premix")
    boiler_model = Column(String(50), default="30kW")
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    __table_args__ = (
        CheckConstraint(
            "mode IN ('auto', 'manual_on', 'manual_off', 'schedule')",
            name="ck_heating_mode",
        ),
        CheckConstraint(
            "strategy IN ('weighted_avg', 'coldest_room', 'hottest_room', 'single_room')",
            name="ck_heating_strategy",
        ),
    )


class HeatingProfile(Base):
    __tablename__ = "heating_profiles"

    id = Column(Integer, primary_key=True)
    name = Column(String(50), nullable=False)
    icon = Column(String(30), default="thermostat")
    target_temp = Column(Float, nullable=False)
    hysteresis = Column(Float, nullable=False, default=0.5)
    is_default = Column(Boolean, default=False)
    sort_order = Column(Integer, default=0)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())


class Schedule(Base):
    __tablename__ = "schedules"

    id = Column(Integer, primary_key=True)
    day_of_week = Column(Integer, nullable=False)
    hour = Column(Integer, nullable=False)
    minute = Column(Integer, nullable=False, default=0)
    target_temp = Column(Float, nullable=False)
    enabled = Column(Boolean, default=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    __table_args__ = (
        CheckConstraint("day_of_week BETWEEN 0 AND 6", name="ck_schedule_dow"),
        CheckConstraint("hour BETWEEN 0 AND 23", name="ck_schedule_hour"),
        CheckConstraint("minute BETWEEN 0 AND 59", name="ck_schedule_minute"),
    )


class AuditLog(Base):
    __tablename__ = "audit_log"

    id = Column(Integer, primary_key=True)
    device_id = Column(String(50))
    action = Column(String(100), nullable=False)
    old_value = Column(Text)
    new_value = Column(Text)
    user_id = Column(Integer, nullable=True)
    user_email = Column(String(255), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())


# --- Baglanti yonetimi ---


async def get_session() -> AsyncSession:
    """Dependency injection icin session factory."""
    async with async_session() as session:
        yield session


async def init_db():
    """Veritabani tablolarini olustur (varsa atla)."""
    async with engine.begin() as conn:
        # Tablolari olustur (varsa CREATE IF NOT EXISTS davranisi)
        await conn.run_sync(Base.metadata.create_all)
        # Baglanti testi
        from sqlalchemy import text
        await conn.execute(text("SELECT 1"))


async def close_db():
    """Engine'i kapat."""
    await engine.dispose()
