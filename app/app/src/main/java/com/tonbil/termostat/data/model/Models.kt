package com.tonbil.termostat.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Generic API Wrapper ──

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
)

// ── Auth ──

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class UserInfo(
    val id: Int = 0,
    val email: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String = "",
    val role: String = "user",
)

// ── Sensor ──

@Serializable
data class SensorReading(
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("room_id") val roomId: Int? = null,
    @SerialName("room_name") val roomName: String? = null,
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val battery: Float? = null,
    val rssi: Int? = null,
    val timestamp: String = "",
)

// ── Device ──

@Serializable
data class Device(
    val id: Int = 0,
    @SerialName("device_id") val deviceId: String = "",
    val name: String = "",
    @SerialName("room_id") val roomId: Int? = null,
    @SerialName("room_name") val roomName: String? = null,
    val type: String = "",
    @SerialName("mqtt_user") val mqttUser: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("firmware_version") val firmwareVersion: String? = null,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class DeviceCommand(
    val command: String,
    val payload: Map<String, String> = emptyMap(),
)

@Serializable
data class DeviceCommandResponse(
    val message: String = "",
    val command: String = "",
)

// ── Room ──

@Serializable
data class Room(
    val id: Int = 0,
    val name: String = "",
    val weight: Float = 1.0f,
    @SerialName("min_temp") val minTemp: Float = 5f,
    val icon: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("current_temp") val currentTemp: Float? = null,
    @SerialName("current_humidity") val currentHumidity: Float? = null,
    @SerialName("device_count") val deviceCount: Int = 0,
)

// ── Weather ──

@Serializable
data class WeatherData(
    val temperature: Float = 0f,
    val humidity: Int = 0,
    val pressure: Float = 0f,
    @SerialName("wind_speed") val windSpeed: Float = 0f,
    @SerialName("wind_direction") val windDirection: Int = 0,
    @SerialName("weather_code") val weatherCode: Int = 0,
    val description: String = "",
    val icon: String = "",
    @SerialName("feels_like") val feelsLike: Float = 0f,
    val city: String = "",
)

// ── Heating Config ──

@Serializable
data class HeatingConfig(
    @SerialName("target_temp") val targetTemp: Float = 22f,
    val hysteresis: Float = 0.3f,
    @SerialName("min_cycle_min") val minCycleMin: Int = 0,
    val mode: String = "auto",
    val strategy: String = "weighted_avg",
    @SerialName("gas_price_per_m3") val gasPricePerM3: Float = 0f,
    @SerialName("floor_area_m2") val floorAreaM2: Float = 0f,
    @SerialName("boiler_power_kw") val boilerPowerKw: Float = 0f,
    @SerialName("flow_temp") val flowTemp: Float = 0f,
    @SerialName("boiler_brand") val boilerBrand: String = "",
    @SerialName("boiler_model") val boilerModel: String = "",
    @SerialName("relay_state") val relayState: Boolean = false,
    @SerialName("updated_at") val updatedAt: String = "",
)

// ── Boost ──

@Serializable
data class BoostConfig(
    val active: Boolean = false,
    @SerialName("remaining_minutes") val remainingMinutes: Int = 0,
    @SerialName("total_minutes") val totalMinutes: Int = 0,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("estimated_gas_cost_tl") val estimatedGasCostTl: Float? = null,
)

@Serializable
data class BoostActivateRequest(
    val minutes: Int,
)

// ── Schedule (legacy alias — kullanilmiyor, ScheduleEntry tercih edilmeli) ──

@Deprecated("Use ScheduleEntry instead", replaceWith = ReplaceWith("ScheduleEntry"))
typealias Schedule = ScheduleEntry

// ── Energy ──

@Serializable
data class EnergyData(
    val today: EnergyDaySummary = EnergyDaySummary(),
    val relay: RelayInfo = RelayInfo(),
    @SerialName("flow_temp") val flowTemp: Float = 0f,
    val efficiency: EfficiencyInfo = EfficiencyInfo(),
    @SerialName("gas_price_per_m3") val gasPricePerM3: Float = 0f,
)

@Serializable
data class EnergyDaySummary(
    @SerialName("runtime_minutes") val runtimeMinutes: Float = 0f,
    @SerialName("gas_m3") val gasM3: Float = 0f,
    @SerialName("thermal_kwh") val thermalKwh: Float = 0f,
    @SerialName("thermal_kcal") val thermalKcal: Float = 0f,
    @SerialName("cost_tl") val costTl: Float = 0f,
)

@Serializable
data class RelayInfo(
    val state: Boolean = false,
    @SerialName("on_since") val onSince: String? = null,
)

@Serializable
data class EfficiencyInfo(
    @SerialName("current_pct") val currentPct: Float = 0f,
    @SerialName("is_condensing") val isCondensing: Boolean = false,
    @SerialName("optimal_flow_temp") val optimalFlowTemp: Float = 0f,
    @SerialName("potential_savings_pct") val potentialSavingsPct: Float = 0f,
)

@Serializable
data class EnergyAlert(
    val type: String = "",
    val severity: String = "",
    val title: String = "",
    val message: String = "",
    val icon: String = "",
)

@Serializable
data class AlertsResponse(
    val alerts: List<EnergyAlert> = emptyList(),
    val count: Int = 0,
    @SerialName("critical_count") val criticalCount: Int = 0,
    @SerialName("warning_count") val warningCount: Int = 0,
)

// ── Room Request Bodies ──

@Serializable
data class RoomRequest(
    val name: String,
    val icon: String,
    val weight: Float,
)

// ── Device Update Request ──

@Serializable
data class DeviceUpdateRequest(
    val name: String,
    @SerialName("room_id") val roomId: Int?,
)

// ── Register Request ──

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("display_name") val displayName: String,
)

// ── Update User Request ──

@Serializable
data class UpdateUserRequest(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

// ── Change Password Request ──

@Serializable
data class ChangePasswordRequest(
    @SerialName("old_password") val oldPassword: String,
    @SerialName("new_password") val newPassword: String,
)

// ── Sensor History ──

@Serializable
data class SensorHistoryResponse(
    @SerialName("device_id") val deviceId: String = "",
    val range: String = "",
    val points: List<SensorHistoryPoint> = emptyList(),
)

@Serializable
data class SensorHistoryPoint(
    val time: String = "",
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val pressure: Float? = null,
)

// ── Schedule Entry (backend format: day_of_week Int, hour/minute Int) ──

@Serializable
data class ScheduleEntry(
    val id: Int? = null,
    @SerialName("day_of_week") val dayOfWeek: Int = 0,  // 0=Pazartesi, 6=Pazar
    val hour: Int = 0,
    val minute: Int = 0,
    @SerialName("target_temp") val targetTemp: Float = 22f,
    val enabled: Boolean = true,
)

// ── Energy Daily ──

@Serializable
data class EnergyDailyResponse(
    val days: List<EnergyDayEntry> = emptyList(),
    val summary: EnergySummary? = null,
)

@Serializable
data class EnergyDayEntry(
    val date: String = "",
    @SerialName("runtime_minutes") val runtimeMinutes: Float = 0f,
)

@Serializable
data class EnergySummary(
    @SerialName("total_runtime_minutes") val totalRuntimeMinutes: Float = 0f,
)

// ── WebSocket Messages ──

@Serializable
data class WsMessage(
    val type: String = "",
    val data: kotlinx.serialization.json.JsonElement? = null,
)

// ── Boiler Status (from WS "boiler" type) ──

@Serializable
data class BoilerStatus(
    val active: Boolean = false,
    val relay: Boolean = false,
    val mode: String? = null,
    val target: Double? = null,
)

// ── WS Sensor (from backend "telemetry" message) ──

@Serializable
data class WsSensorData(
    @SerialName("device_id") val deviceId: String = "",
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val pressure: Float? = null,
    // Kısaltılmış alan uyumluluğu (eski format)
    val temp: Float? = null,
    val hum: Float? = null,
    val pres: Float? = null,
) {
    /** Sıcaklık — yeni veya eski format */
    val effectiveTemp: Float get() = if (temperature != 0f) temperature else (temp ?: 0f)
    /** Nem — yeni veya eski format */
    val effectiveHum: Float get() = if (humidity != 0f) humidity else (hum ?: 0f)
    /** Basınç — yeni veya eski format */
    val effectivePres: Float? get() = pressure ?: pres
}

// ── WS Device Status ──

@Serializable
data class WsDeviceStatus(
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("is_online") val isOnline: Boolean = false,
)

// ── Heating Profiles ──

@Serializable
data class HeatingProfile(
    val id: Int = 0,
    val name: String,
    val icon: String = "thermostat",
    @SerialName("target_temp") val targetTemp: Float,
    val hysteresis: Float = 0.5f,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class HeatingProfileCreate(
    val name: String,
    val icon: String = "thermostat",
    @SerialName("target_temp") val targetTemp: Float,
    val hysteresis: Float = 0.5f,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class HeatingProfileUpdate(
    val name: String? = null,
    val icon: String? = null,
    @SerialName("target_temp") val targetTemp: Float? = null,
    val hysteresis: Float? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
)
