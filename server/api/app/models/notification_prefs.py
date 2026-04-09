"""Bildirim tercihleri modeli - kullanici bazli push notification ayarlari."""

from sqlalchemy import Boolean, Column, DateTime, Float, ForeignKey, Integer, func

from app.models.database import Base


class NotificationPreferences(Base):
    """Kullanici bildirim tercihleri."""

    __tablename__ = "notification_preferences"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), unique=True, nullable=False)

    # Alert tipleri
    temp_low_alert = Column(Boolean, default=True, nullable=False)
    temp_high_alert = Column(Boolean, default=True, nullable=False)
    relay_change_alert = Column(Boolean, default=True, nullable=False)
    sensor_offline_alert = Column(Boolean, default=True, nullable=False)

    # Sicaklik esik degerleri
    temp_low_threshold = Column(Float, default=16.0, nullable=False)
    temp_high_threshold = Column(Float, default=28.0, nullable=False)

    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
