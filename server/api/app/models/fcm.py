"""FCM token saklama modeli - push notification icin cihaz token'lari."""

from sqlalchemy import Column, DateTime, ForeignKey, Integer, String, func

from app.models.database import Base


class FCMToken(Base):
    """Kullanicilarin FCM push notification token'lari."""

    __tablename__ = "fcm_tokens"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    token = Column(String(512), unique=True, nullable=False)
    device_name = Column(String(128))
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
