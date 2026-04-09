package com.tonbil.termostat.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tonbil.termostat.data.api.TonbilApi
import com.tonbil.termostat.data.api.WebSocketClient
import com.tonbil.termostat.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TonbilRepository(
    private val api: TonbilApi,
    private val webSocketClient: WebSocketClient,
    private val dataStore: DataStore<Preferences>,
    context: Context,
) {
    companion object {
        private const val TAG = "TonbilRepository"
        private const val ENCRYPTED_PREFS_NAME = "tonbil_secure_prefs"
        private const val ENCRYPTED_TOKEN_KEY = "auth_token"
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val EMAIL_KEY = stringPreferencesKey("email")
        private val PROFILE_KEY = stringPreferencesKey("heating_profile")
    }

    private val encryptedPrefs: SharedPreferences = createEncryptedPrefs(context)

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ── Token Management (EncryptedSharedPreferences) ──

    private val _tokenState = MutableStateFlow(encryptedPrefs.getString(ENCRYPTED_TOKEN_KEY, null))
    val tokenFlow: Flow<String?> = _tokenState

    fun getToken(): String? = encryptedPrefs.getString(ENCRYPTED_TOKEN_KEY, null)

    fun saveToken(token: String) {
        encryptedPrefs.edit().putString(ENCRYPTED_TOKEN_KEY, token).apply()
        _tokenState.value = token
    }

    fun clearToken() {
        encryptedPrefs.edit().remove(ENCRYPTED_TOKEN_KEY).apply()
        _tokenState.value = null
        api.setToken(null)
    }

    suspend fun saveServerUrl(url: String) {
        dataStore.edit { prefs -> prefs[SERVER_URL_KEY] = url }
    }

    suspend fun getServerUrl(): String? = dataStore.data.first()[SERVER_URL_KEY]

    suspend fun saveEmail(email: String) {
        dataStore.edit { prefs -> prefs[EMAIL_KEY] = email }
    }

    suspend fun getEmail(): String? = dataStore.data.first()[EMAIL_KEY]

    // ── Heating Profile ──

    suspend fun saveHeatingProfile(profile: String) {
        dataStore.edit { prefs -> prefs[PROFILE_KEY] = profile }
    }

    suspend fun getHeatingProfile(): String? = dataStore.data.first()[PROFILE_KEY]

    fun isLoggedIn(): Flow<Boolean> = tokenFlow.map { it != null }

    /**
     * API'den 401 Unauthorized alindginda emit eder.
     * MainActivity bu flow'u dinleyip login ekranina yonlendirir.
     */
    val authExpired: SharedFlow<Boolean> = api.authExpired

    // ── Auth ──

    suspend fun login(email: String, password: String): Result<UserInfo> = runCatching {
        val user = api.login(email, password)
        val token = api.getToken()
        if (token != null) {
            saveToken(token)
            saveEmail(email)
            webSocketClient.connect(token)
        } else {
            throw Exception("No token received from server")
        }
        user
    }

    suspend fun logout() {
        try {
            api.logout()
        } catch (e: Exception) {
            Log.w(TAG, "Backend logout cagrisi basarisiz: ${e.message}")
        }
        webSocketClient.disconnect()
        clearToken()
    }

    suspend fun getMe(): Result<UserInfo> = runCatching { api.getMe() }

    // Restore token from DataStore + sunucu keşfi
    suspend fun restoreToken() {
        val token = getToken()
        if (token != null) {
            api.setToken(token)
        }
        // Kaydedilmiş sunucu URL'i varsa kullan
        val savedUrl = getServerUrl()
        if (savedUrl != null) {
            api.setBaseUrl(savedUrl)
        }
    }

    /**
     * Tüm bilinen sunucu adreslerini dene, çalışanı kaydet.
     * Login ekranından veya app başlangıcında çağrılır.
     */
    suspend fun discoverAndConnect(): Result<String> = runCatching {
        val savedUrl = getServerUrl()
        val urls = if (savedUrl != null) {
            // Kaydedilmiş URL'i önce dene
            listOf(savedUrl) + com.tonbil.termostat.di.SERVER_URLS.filter { it != savedUrl }
        } else {
            com.tonbil.termostat.di.SERVER_URLS
        }

        val foundUrl = api.discoverServer(urls)
            ?: throw Exception("Sunucuya baglanilamiyor. Tum adresler denendi.")

        saveServerUrl(foundUrl)

        // WebSocket URL'ini de güncelle
        val wsUrl = com.tonbil.termostat.di.baseUrlToWsUrl(foundUrl)
        webSocketClient.updateUrl(wsUrl)

        foundUrl
    }

    // ── Devices ──

    suspend fun getDevices(): Result<List<Device>> = runCatching { api.getDevices() }

    suspend fun updateDevice(deviceId: String, name: String, roomId: Int?): Result<Device> =
        runCatching { api.updateDevice(deviceId, name, roomId) }

    suspend fun sendCommand(deviceId: String, command: String, payload: Map<String, String> = emptyMap()): Result<DeviceCommandResponse> =
        runCatching { api.sendDeviceCommand(deviceId, DeviceCommand(command, payload)) }

    suspend fun deleteDevice(deviceId: String): Result<Unit> = runCatching { api.deleteDevice(deviceId) }

    // ── Sensors ──

    suspend fun getCurrentSensors(): Result<List<SensorReading>> = runCatching { api.getCurrentSensors() }

    // ── Rooms ──

    suspend fun getRooms(): Result<List<Room>> = runCatching { api.getRooms() }

    suspend fun createRoom(name: String, icon: String, weight: Float): Result<Room> =
        runCatching { api.createRoom(name, icon, weight) }

    suspend fun updateRoom(id: Int, name: String, icon: String, weight: Float): Result<Room> =
        runCatching { api.updateRoom(id, name, icon, weight) }

    suspend fun deleteRoom(id: Int): Result<Unit> = runCatching { api.deleteRoom(id) }

    // ── Weather ──

    suspend fun getCurrentWeather(): Result<WeatherData> = runCatching { api.getCurrentWeather() }

    // ── Heating ──

    suspend fun getHeatingConfig(): Result<HeatingConfig> = runCatching { api.getHeatingConfig() }

    suspend fun updateHeatingConfig(config: HeatingConfig): Result<HeatingConfig> =
        runCatching { api.updateHeatingConfig(config) }

    /** Sadece belirtilen alanları güncelle */
    suspend fun updateHeatingPartial(fields: Map<String, Any?>): Result<HeatingConfig> =
        runCatching { api.updateHeatingPartial(fields) }

    // ── Boost ──

    suspend fun getBoostConfig(): Result<BoostConfig> = runCatching { api.getBoostConfig() }

    suspend fun activateBoost(minutes: Int): Result<BoostConfig> = runCatching { api.activateBoost(minutes) }

    suspend fun cancelBoost(): Result<BoostConfig> = runCatching { api.cancelBoost() }

    // ── User Management ──

    suspend fun getUsers(): Result<List<UserInfo>> = runCatching { api.getUsers() }

    suspend fun createUser(email: String, password: String, displayName: String): Result<UserInfo> =
        runCatching { api.createUser(email, password, displayName) }

    suspend fun deleteUser(userId: Int): Result<Unit> = runCatching { api.deleteUser(userId) }

    suspend fun updateUser(userId: Int, displayName: String?, isActive: Boolean?): Result<UserInfo> =
        runCatching { api.updateUser(userId, displayName, isActive) }

    // ── Password ──

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> =
        runCatching { api.changePassword(oldPassword, newPassword) }

    // ── Sensor History ──

    suspend fun getSensorHistory(deviceId: String, range: String = "24h"): Result<SensorHistoryResponse> =
        runCatching { api.getSensorHistory(deviceId, range) }

    suspend fun getSensorHistoryByRoom(roomId: Int?, range: String = "24h"): Result<SensorHistoryResponse> =
        runCatching { api.getSensorHistoryByRoom(roomId, range) }

    // ── Schedules ──

    suspend fun getSchedules(): Result<List<ScheduleEntry>> = runCatching { api.getSchedules() }

    suspend fun getScheduleEntries(): Result<List<ScheduleEntry>> = runCatching { api.getScheduleEntries() }

    suspend fun updateScheduleEntries(entries: List<ScheduleEntry>): Result<List<ScheduleEntry>> =
        runCatching { api.updateScheduleEntries(entries) }

    // ── Energy ──

    suspend fun getEnergyData(): Result<EnergyData> = runCatching { api.getEnergyData() }

    suspend fun getEnergyAlerts(): Result<List<EnergyAlert>> = runCatching { api.getEnergyAlerts() }

    suspend fun getEnergyDaily(days: Int = 7): Result<EnergyDailyResponse> =
        runCatching { api.getEnergyDaily(days) }

    // ── Profiles ──

    suspend fun getProfiles(): Result<List<HeatingProfile>> = runCatching { api.getProfiles() }

    suspend fun createProfile(name: String, icon: String, targetTemp: Float, hysteresis: Float): Result<HeatingProfile> =
        runCatching { api.createProfile(HeatingProfileCreate(name, icon, targetTemp, hysteresis)) }

    suspend fun updateProfile(id: Int, name: String?, icon: String?, targetTemp: Float?, hysteresis: Float?): Result<HeatingProfile> =
        runCatching { api.updateProfile(id, HeatingProfileUpdate(name, icon, targetTemp, hysteresis)) }

    suspend fun deleteProfile(id: Int): Result<Unit> = runCatching { api.deleteProfile(id) }

    // ── WebSocket ──

    fun connectWebSocket(token: String) {
        webSocketClient.connect(token)
    }

    fun disconnectWebSocket() {
        webSocketClient.disconnect()
    }

    val wsConnected = webSocketClient.isConnected
    val sensorUpdates = webSocketClient.sensorUpdates
    val boilerUpdates = webSocketClient.boilerUpdates
    val configUpdates = webSocketClient.configUpdates
    val boostUpdates = webSocketClient.boostUpdates
    val deviceStatusUpdates = webSocketClient.deviceStatusUpdates

    // ── Reconnect on resume ──

    suspend fun ensureWebSocketConnected() {
        if (!wsConnected.value) {
            val token = getToken()
            if (token != null) {
                Log.d(TAG, "WebSocket yeniden baglaniyor...")
                api.setToken(token)
                webSocketClient.connect(token)
            }
        }
    }
}
