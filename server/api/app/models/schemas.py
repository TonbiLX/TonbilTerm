"""Pydantic request/response modelleri."""

from datetime import datetime

from pydantic import BaseModel, EmailStr, Field


# --- Genel ---


class ApiResponse[T](BaseModel):
    """Tutarli API yanit formati."""
    success: bool = True
    data: T | None = None
    error: str | None = None


class PaginatedResponse[T](BaseModel):
    success: bool = True
    data: list[T] = []
    meta: dict | None = None


# --- Auth ---


class LoginRequest(BaseModel):
    email: str
    password: str


class UserResponse(BaseModel):
    id: int
    email: str
    display_name: str | None = None
    role: str = "user"
    is_active: bool = True
    created_at: datetime

    model_config = {"from_attributes": True}


class TokenData(BaseModel):
    user_id: int
    email: str


class RegisterRequest(BaseModel):
    email: str = Field(min_length=3, max_length=255)
    password: str = Field(min_length=6, max_length=128)
    display_name: str = Field(min_length=1, max_length=100)


class UpdateUserRequest(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=100)
    is_active: bool | None = None
    email: str | None = Field(default=None, min_length=3, max_length=255)
    role: str | None = Field(default=None, pattern="^(admin|user)$")


# --- Devices ---


class DeviceResponse(BaseModel):
    id: int
    device_id: str
    name: str | None = None
    room_id: int | None = None
    room_name: str | None = None
    type: str
    mqtt_user: str | None = None
    last_seen: datetime | None = None
    firmware_version: str | None = None
    ip_address: str | None = None
    is_online: bool = False
    created_at: datetime

    model_config = {"from_attributes": True}


class DeviceUpdate(BaseModel):
    name: str | None = None
    room_id: int | None = None


class DeviceCommand(BaseModel):
    command: str  # "relay_on", "relay_off", "reboot", "ota_update"
    payload: dict | None = None


# --- Rooms ---


class RoomResponse(BaseModel):
    id: int
    name: str
    weight: float = 1.0
    min_temp: float = 5.0
    icon: str = "room"
    sort_order: int = 0
    current_temp: float | None = None
    current_humidity: float | None = None
    device_count: int = 0

    model_config = {"from_attributes": True}


class RoomCreate(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    weight: float = Field(default=0.5, ge=0.0, le=1.0)
    min_temp: float = Field(default=5.0, ge=0.0, le=30.0)
    icon: str = "room"
    sort_order: int = 0


class RoomUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=100)
    weight: float | None = Field(default=None, ge=0.0, le=1.0)
    min_temp: float | None = Field(default=None, ge=0.0, le=30.0)
    icon: str | None = None
    sort_order: int | None = None


# --- Sensors ---


class SensorReading(BaseModel):
    device_id: str
    room_id: int | None = None
    room_name: str | None = None
    temperature: float | None = None
    humidity: float | None = None
    battery: float | None = None
    rssi: int | None = None
    timestamp: datetime


class SensorHistoryPoint(BaseModel):
    time: datetime
    temperature: float | None = None
    humidity: float | None = None


# --- Heating Config ---


class HeatingConfigResponse(BaseModel):
    target_temp: float
    hysteresis: float
    min_cycle_min: int
    mode: str
    strategy: str
    gas_price_per_m3: float | None = None
    floor_area_m2: float | None = None
    boiler_power_kw: float | None = None
    flow_temp: float | None = 60.0
    boiler_brand: str | None = "ECA Proteus Premix"
    boiler_model: str | None = "30kW"
    relay_state: bool = False  # Anlik role durumu
    updated_at: datetime | None = None

    model_config = {"from_attributes": True}


class HeatingConfigUpdate(BaseModel):
    target_temp: float | None = Field(default=None, ge=5.0, le=35.0)
    hysteresis: float | None = Field(default=None, ge=0.1, le=5.0)
    min_cycle_min: int | None = Field(default=None, ge=0, le=30)
    mode: str | None = None
    strategy: str | None = None
    gas_price_per_m3: float | None = Field(default=None, ge=0)
    floor_area_m2: float | None = Field(default=None, ge=1)
    boiler_power_kw: float | None = Field(default=None, ge=1)
    flow_temp: float | None = Field(default=None, ge=30.0, le=85.0)


# --- Heating Profiles ---


class HeatingProfileResponse(BaseModel):
    id: int
    name: str
    icon: str = "thermostat"
    target_temp: float
    hysteresis: float
    is_default: bool = False
    sort_order: int = 0

    model_config = {"from_attributes": True}


class HeatingProfileCreate(BaseModel):
    name: str = Field(min_length=1, max_length=50)
    icon: str = Field(default="thermostat", max_length=30)
    target_temp: float = Field(ge=5.0, le=35.0)
    hysteresis: float = Field(default=0.5, ge=0.1, le=5.0)
    sort_order: int = 0


class HeatingProfileUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=50)
    icon: str | None = Field(default=None, max_length=30)
    target_temp: float | None = Field(default=None, ge=5.0, le=35.0)
    hysteresis: float | None = Field(default=None, ge=0.1, le=5.0)
    sort_order: int | None = None


# --- Schedules ---


class ScheduleEntry(BaseModel):
    id: int | None = None
    day_of_week: int = Field(ge=0, le=6)
    hour: int = Field(ge=0, le=23)
    minute: int = Field(default=0, ge=0, le=59)
    target_temp: float = Field(ge=5.0, le=35.0)
    enabled: bool = True

    model_config = {"from_attributes": True}


class ScheduleUpdate(BaseModel):
    entries: list[ScheduleEntry]


# --- Weather ---


class WeatherCurrent(BaseModel):
    temperature: float
    humidity: float | None = None
    wind_speed: float | None = None
    weather_code: int | None = None
    description: str | None = None
    icon: str | None = None
    feels_like: float | None = None
    city: str


class WeatherForecastPoint(BaseModel):
    time: datetime
    temperature: float
    humidity: float | None = None
    weather_code: int | None = None


class WeatherForecast(BaseModel):
    city: str
    hourly: list[WeatherForecastPoint]


# --- WebSocket mesajlari ---


class WSMessage(BaseModel):
    type: str  # "telemetry", "relay_state", "device_status", "config_update"
    data: dict
