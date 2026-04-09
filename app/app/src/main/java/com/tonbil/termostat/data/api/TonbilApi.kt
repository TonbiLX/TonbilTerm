package com.tonbil.termostat.data.api

import android.util.Log
import com.tonbil.termostat.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.delete
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class UnauthorizedException(message: String) : Exception(message)

class TonbilApi(private val client: HttpClient, initialBaseUrl: String) {

    companion object {
        private const val TAG = "TonbilApi"
    }

    // Aktif sunucu adresi — otomatik keşif ile değişebilir
    var baseUrl: String = initialBaseUrl
        private set

    // Stored JWT cookie value
    private var jwtToken: String? = null

    // 401 auth expired signal — Repository dinler, login'e yonlendirir
    private val _authExpired = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val authExpired: SharedFlow<Boolean> = _authExpired.asSharedFlow()

    fun setToken(token: String?) {
        jwtToken = token
    }

    fun getToken(): String? = jwtToken

    /**
     * Sunucu adresini değiştir (otomatik keşif sonrası).
     */
    fun setBaseUrl(url: String) {
        baseUrl = url
        Log.d(TAG, "Base URL degistirildi: $url")
    }

    /**
     * Verilen URL listesini sırayla dene, ilk çalışanı kullan.
     * /health endpoint'ine GET yaparak bağlantı test eder.
     */
    suspend fun discoverServer(urls: List<String>): String? {
        for (url in urls) {
            try {
                val response = client.get("$url/health")
                if (response.status.value in 200..299) {
                    baseUrl = url
                    Log.d(TAG, "Sunucu bulundu: $url")
                    return url
                }
            } catch (e: Exception) {
                Log.d(TAG, "Sunucu denendi basarisiz: $url (${e.message})")
            }
        }
        return null
    }

    private fun HttpRequestBuilder.addAuth() {
        jwtToken?.let { token ->
            header(HttpHeaders.Cookie, "tonbil_token=$token")
        }
    }

    /**
     * 401 response kontrolu. Auth endpoint'leri (login) haric
     * 401 alindginda token temizlenir ve authExpired sinyali gonderilir.
     */
    private suspend fun checkUnauthorized(response: HttpResponse, path: String) {
        if (response.status.value == 401 && !path.contains("auth/login")) {
            Log.w(TAG, "401 Unauthorized: $path — token expired")
            jwtToken = null
            _authExpired.tryEmit(true)
            throw UnauthorizedException("Oturum suresi doldu, tekrar giris yapin")
        }
    }

    /**
     * Authenticated GET request with 401 handling.
     */
    private suspend inline fun <reified T> authGet(path: String): T {
        val response = client.get("$baseUrl$path") { addAuth() }
        checkUnauthorized(response, path)
        return response.body()
    }

    /**
     * Authenticated POST request with 401 handling.
     */
    private suspend inline fun <reified T> authPost(
        path: String,
        body: Any? = null,
    ): T {
        val response = client.post("$baseUrl$path") {
            addAuth()
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        checkUnauthorized(response, path)
        return response.body()
    }

    /**
     * Authenticated PUT request with 401 handling.
     */
    private suspend inline fun <reified T> authPut(
        path: String,
        body: Any? = null,
    ): T {
        val response = client.put("$baseUrl$path") {
            addAuth()
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        checkUnauthorized(response, path)
        return response.body()
    }

    /**
     * Authenticated DELETE request with 401 handling.
     */
    private suspend inline fun <reified T> authDelete(path: String): T {
        val response = client.delete("$baseUrl$path") { addAuth() }
        checkUnauthorized(response, path)
        return response.body()
    }

    /**
     * Authenticated DELETE (Unit response) with 401 handling.
     */
    private suspend fun authDeleteUnit(path: String) {
        val response = client.delete("$baseUrl$path") { addAuth() }
        checkUnauthorized(response, path)
    }

    // ── Auth ──

    suspend fun login(email: String, password: String): UserInfo {
        val response: HttpResponse = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }

        // Extract JWT from Set-Cookie header
        val setCookieHeaders = response.headers.getAll(HttpHeaders.SetCookie) ?: emptyList()
        for (cookie in setCookieHeaders) {
            val parts = cookie.split(";").first().split("=", limit = 2)
            if (parts.size == 2 && parts[0].trim() == "tonbil_token") {
                jwtToken = parts[1].trim()
                Log.d(TAG, "JWT token extracted from Set-Cookie")
                break
            }
        }

        val apiResponse: ApiResponse<UserInfo> = response.body()
        return apiResponse.data ?: throw Exception("Login failed: ${apiResponse.error ?: "no data"}")
    }

    suspend fun logout() {
        client.post("$baseUrl/api/auth/logout") {
            addAuth()
        }
    }

    suspend fun getMe(): UserInfo {
        val apiResponse: ApiResponse<UserInfo> = authGet("/api/auth/me")
        return apiResponse.data ?: throw Exception("getMe failed: ${apiResponse.error ?: "no data"}")
    }

    // ── Devices ──

    suspend fun getDevices(): List<Device> {
        val apiResponse: ApiResponse<List<Device>> = authGet("/api/devices")
        return apiResponse.data ?: emptyList()
    }

    suspend fun sendDeviceCommand(deviceId: String, command: DeviceCommand): DeviceCommandResponse {
        val apiResponse: ApiResponse<DeviceCommandResponse> = authPost("/api/devices/$deviceId/command", command)
        return apiResponse.data ?: throw Exception("Command failed: ${apiResponse.error ?: "no data"}")
    }

    // ── Sensors ──

    suspend fun getCurrentSensors(): List<SensorReading> {
        val apiResponse: ApiResponse<List<SensorReading>> = authGet("/api/sensors/current")
        return apiResponse.data ?: emptyList()
    }

    // ── Rooms ──

    suspend fun getRooms(): List<Room> {
        val apiResponse: ApiResponse<List<Room>> = authGet("/api/rooms")
        return apiResponse.data ?: emptyList()
    }

    suspend fun createRoom(name: String, icon: String, weight: Float): Room {
        val apiResponse: ApiResponse<Room> = authPost("/api/rooms", RoomRequest(name, icon, weight))
        return apiResponse.data ?: throw Exception("createRoom failed: ${apiResponse.error ?: "no data"}")
    }

    suspend fun updateRoom(id: Int, name: String, icon: String, weight: Float): Room {
        val apiResponse: ApiResponse<Room> = authPut("/api/rooms/$id", RoomRequest(name, icon, weight))
        return apiResponse.data ?: throw Exception("updateRoom failed: ${apiResponse.error ?: "no data"}")
    }

    suspend fun deleteRoom(id: Int) {
        authDeleteUnit("/api/rooms/$id")
    }

    // ── Device Update ──

    suspend fun updateDevice(deviceId: String, name: String, roomId: Int?): Device {
        val apiResponse: ApiResponse<Device> = authPut("/api/devices/$deviceId", DeviceUpdateRequest(name, roomId))
        return apiResponse.data ?: throw Exception("updateDevice failed: ${apiResponse.error ?: "no data"}")
    }

    suspend fun deleteDevice(deviceId: String) {
        authDeleteUnit("/api/devices/$deviceId")
    }

    // ── User Management ──

    suspend fun getUsers(): List<UserInfo> {
        val apiResponse: ApiResponse<List<UserInfo>> = authGet("/api/auth/users")
        return apiResponse.data ?: emptyList()
    }

    suspend fun createUser(email: String, password: String, displayName: String): UserInfo {
        val apiResponse: ApiResponse<UserInfo> = authPost("/api/auth/register", RegisterRequest(email, password, displayName))
        return apiResponse.data ?: throw Exception("createUser failed: ${apiResponse.error ?: "no data"}")
    }

    suspend fun deleteUser(userId: Int) {
        authDeleteUnit("/api/auth/users/$userId")
    }

    suspend fun updateUser(userId: Int, displayName: String?, isActive: Boolean?): UserInfo {
        val apiResponse: ApiResponse<UserInfo> = authPut("/api/auth/users/$userId", UpdateUserRequest(displayName, isActive))
        return apiResponse.data ?: throw Exception("updateUser failed: ${apiResponse.error ?: "no data"}")
    }

    // ── Change Password ──

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        authPost<HttpResponse>("/api/auth/change-password", ChangePasswordRequest(oldPassword, newPassword))
    }

    // ── Weather ──

    suspend fun getCurrentWeather(): WeatherData {
        val apiResponse: ApiResponse<WeatherData> = authGet("/api/weather/current")
        return apiResponse.data ?: throw Exception("Weather failed: ${apiResponse.error ?: "no data"}")
    }

    // ── Heating Config ──

    suspend fun getHeatingConfig(): HeatingConfig {
        val apiResponse: ApiResponse<HeatingConfig> = authGet("/api/config/heating")
        return apiResponse.data ?: throw Exception("HeatingConfig failed: ${apiResponse.error ?: "no data"}")
    }

    suspend fun updateHeatingConfig(config: HeatingConfig): HeatingConfig {
        val apiResponse: ApiResponse<HeatingConfig> = authPut("/api/config/heating", config)
        return apiResponse.data ?: throw Exception("UpdateHeating failed: ${apiResponse.error ?: "no data"}")
    }

    /**
     * Sadece belirtilen alanları güncelle (partial update).
     * Backend HeatingConfigUpdate modeli exclude_unset=True kullanır,
     * bu yüzden sadece gönderilen alanlar değişir.
     */
    suspend fun updateHeatingPartial(fields: Map<String, Any?>): HeatingConfig {
        // Map<String, Any?> -> JsonObject dönüşümü (kotlinx.serialization Any? serialize edemez)
        val jsonMap = fields.mapNotNull { (k, v) ->
            val jsonValue = when (v) {
                is Number -> JsonPrimitive(v.toDouble())
                is String -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                null -> null
                else -> JsonPrimitive(v.toString())
            }
            if (jsonValue != null) k to jsonValue else null
        }.toMap()
        val jsonBody = JsonObject(jsonMap)

        val response = client.put("$baseUrl/api/config/heating") {
            addAuth()
            contentType(ContentType.Application.Json)
            setBody(jsonBody.toString())
        }
        checkUnauthorized(response, "/api/config/heating")
        val apiResponse: ApiResponse<HeatingConfig> = response.body()
        return apiResponse.data ?: throw Exception("UpdateHeating failed: ${apiResponse.error ?: "no data"}")
    }

    // ── Boost ──

    suspend fun getBoostConfig(): BoostConfig {
        val apiResponse: ApiResponse<BoostConfig> = authGet("/api/config/boost")
        return apiResponse.data ?: throw Exception("BoostConfig failed: ${apiResponse.error ?: "no data"}")
    }

    suspend fun activateBoost(minutes: Int): BoostConfig {
        val apiResponse: ApiResponse<BoostConfig> = authPost("/api/config/boost", BoostActivateRequest(minutes))
        return apiResponse.data ?: throw Exception("ActivateBoost failed: ${apiResponse.error ?: "no data"}")
    }

    suspend fun cancelBoost(): BoostConfig {
        val apiResponse: ApiResponse<BoostConfig> = authDelete("/api/config/boost")
        return apiResponse.data ?: throw Exception("CancelBoost failed: ${apiResponse.error ?: "no data"}")
    }

    // ── Sensor History ──

    suspend fun getSensorHistory(deviceId: String, range: String = "24h"): SensorHistoryResponse {
        val response = client.get("$baseUrl/api/sensors/history") {
            addAuth()
            parameter("device", deviceId)
            parameter("range", range)
        }
        checkUnauthorized(response, "/api/sensors/history")
        val apiResponse: ApiResponse<List<SensorHistoryPoint>> = response.body()
        return SensorHistoryResponse(
            deviceId = deviceId,
            range = range,
            points = apiResponse.data ?: emptyList(),
        )
    }

    suspend fun getSensorHistoryByRoom(roomId: Int?, range: String = "24h"): SensorHistoryResponse {
        val response = client.get("$baseUrl/api/sensors/history") {
            addAuth()
            parameter("range", range)
            if (roomId != null) {
                parameter("room", roomId)
            }
        }
        checkUnauthorized(response, "/api/sensors/history")
        val apiResponse: ApiResponse<List<SensorHistoryPoint>> = response.body()
        return SensorHistoryResponse(
            deviceId = roomId?.toString() ?: "all",
            range = range,
            points = apiResponse.data ?: emptyList(),
        )
    }

    // ── Schedules ──

    suspend fun getSchedules(): List<ScheduleEntry> {
        val apiResponse: ApiResponse<List<ScheduleEntry>> = authGet("/api/config/schedules")
        return apiResponse.data ?: emptyList()
    }

    suspend fun getScheduleEntries(): List<ScheduleEntry> {
        val apiResponse: ApiResponse<List<ScheduleEntry>> = authGet("/api/config/schedules")
        return apiResponse.data ?: emptyList()
    }

    suspend fun updateScheduleEntries(entries: List<ScheduleEntry>): List<ScheduleEntry> {
        val apiResponse: ApiResponse<List<ScheduleEntry>> = authPut("/api/config/schedules", mapOf("entries" to entries))
        return apiResponse.data ?: emptyList()
    }

    // ── Energy ──

    suspend fun getEnergyData(): EnergyData {
        val apiResponse: ApiResponse<EnergyData> = authGet("/api/energy/current")
        return apiResponse.data ?: throw Exception("EnergyData failed: ${apiResponse.error ?: "no data"}")
    }

    suspend fun getEnergyAlerts(): List<EnergyAlert> {
        val apiResponse: ApiResponse<AlertsResponse> = authGet("/api/energy/alerts")
        return apiResponse.data?.alerts ?: emptyList()
    }

    suspend fun getEnergyDaily(days: Int = 7): EnergyDailyResponse {
        val apiResponse: ApiResponse<EnergyDailyResponse> = authGet("/api/energy/daily?days=$days")
        return apiResponse.data ?: throw Exception("EnergyDaily failed: ${apiResponse.error ?: "no data"}")
    }

    // ── Profiles ──

    suspend fun getProfiles(): List<HeatingProfile> {
        val apiResponse: ApiResponse<List<HeatingProfile>> = authGet("/api/config/profiles")
        return apiResponse.data ?: emptyList()
    }

    suspend fun createProfile(profile: HeatingProfileCreate): HeatingProfile {
        val apiResponse: ApiResponse<HeatingProfile> = authPost("/api/config/profiles", profile)
        return apiResponse.data ?: throw Exception("Profil oluşturulamadı")
    }

    suspend fun updateProfile(id: Int, profile: HeatingProfileUpdate): HeatingProfile {
        val apiResponse: ApiResponse<HeatingProfile> = authPut("/api/config/profiles/$id", profile)
        return apiResponse.data ?: throw Exception("Profil güncellenemedi")
    }

    suspend fun deleteProfile(id: Int) {
        authDeleteUnit("/api/config/profiles/$id")
    }
}
