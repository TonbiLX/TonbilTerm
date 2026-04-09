package com.tonbil.termostat.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonbil.termostat.data.model.BoilerStatus
import com.tonbil.termostat.data.model.BoostConfig
import com.tonbil.termostat.data.model.HeatingConfig
import com.tonbil.termostat.data.model.HeatingProfile
import com.tonbil.termostat.data.model.Room
import com.tonbil.termostat.data.model.SensorHistoryPoint
import com.tonbil.termostat.data.model.SensorReading
import com.tonbil.termostat.data.model.WeatherData
import com.tonbil.termostat.data.repository.TonbilRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: TonbilRepository) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _heatingConfig = MutableStateFlow(HeatingConfig())
    val heatingConfig: StateFlow<HeatingConfig> = _heatingConfig.asStateFlow()

    private val _sensors = MutableStateFlow<List<SensorReading>>(emptyList())
    val sensors: StateFlow<List<SensorReading>> = _sensors.asStateFlow()

    private val _weather = MutableStateFlow<WeatherData?>(null)
    val weather: StateFlow<WeatherData?> = _weather.asStateFlow()

    private val _boilerStatus = MutableStateFlow<BoilerStatus?>(null)
    val boilerStatus: StateFlow<BoilerStatus?> = _boilerStatus.asStateFlow()

    private val _boostConfig = MutableStateFlow<BoostConfig?>(null)
    val boostConfig: StateFlow<BoostConfig?> = _boostConfig.asStateFlow()

    private val _wsConnected = MutableStateFlow(false)
    val wsConnected: StateFlow<Boolean> = _wsConnected.asStateFlow()

    private val _relayLoading = MutableStateFlow(false)
    val relayLoading: StateFlow<Boolean> = _relayLoading.asStateFlow()

    private val _modeLoading = MutableStateFlow(false)
    val modeLoading: StateFlow<Boolean> = _modeLoading.asStateFlow()

    private val _historyPoints = MutableStateFlow<List<SensorHistoryPoint>>(emptyList())
    val historyPoints: StateFlow<List<SensorHistoryPoint>> = _historyPoints.asStateFlow()

    private val _profiles = MutableStateFlow<List<HeatingProfile>>(emptyList())
    val profiles: StateFlow<List<HeatingProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow<Int?>(null)
    val activeProfileId: StateFlow<Int?> = _activeProfileId.asStateFlow()

    private val _runtimeToday = MutableStateFlow(0)
    val runtimeToday: StateFlow<Int> = _runtimeToday.asStateFlow()

    private val _runtimeWeekly = MutableStateFlow(0)
    val runtimeWeekly: StateFlow<Int> = _runtimeWeekly.asStateFlow()

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    private val _selectedRoomId = MutableStateFlow<Int?>(null)
    val selectedRoomId: StateFlow<Int?> = _selectedRoomId.asStateFlow()

    private var targetDebounceJob: Job? = null
    private var isUserAdjustingTarget = false

    init {
        loadInitialData()
        collectWebSocketUpdates()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val savedId = repository.getHeatingProfile()
            _activeProfileId.value = savedId?.toIntOrNull()
            loadAllData()
            _isLoading.value = false
            repository.ensureWebSocketConnected()
        }
    }

    private fun collectWebSocketUpdates() {
        viewModelScope.launch {
            repository.wsConnected.collect { _wsConnected.value = it }
        }
        viewModelScope.launch {
            repository.boilerUpdates.collect { boiler ->
                _boilerStatus.value = boiler
                // Relay state'i WS'ten güncelle (gerçek röle durumu)
                if (boiler.relay != null) {
                    _heatingConfig.value = _heatingConfig.value.copy(relayState = boiler.relay)
                }
                // NOT: boiler.mode ve boiler.target KULLANILMAZ!
                // ESP cihazı kendi lokal mode'unu gönderiyor, sunucu mode'u değil.
                // Mode ve target sadece API response'undan (selectMode/selectProfile)
                // veya config_update WS mesajından alınır.
                // boiler/telemetri mesajındaki mode cihazın kendi görüşüdür,
                // sunucu DB'sindeki mode ile uyumsuz olabilir.
            }
        }
        viewModelScope.launch {
            repository.sensorUpdates.collect { wsSensor ->
                _sensors.value = _sensors.value.map {
                    if (it.deviceId == wsSensor.deviceId) {
                        it.copy(
                            temperature = wsSensor.effectiveTemp,
                            humidity = wsSensor.effectiveHum,
                        )
                    } else {
                        it
                    }
                }
            }
        }
        // config_update: sunucu tarafından yayınlanan güvenilir config değişikliği
        viewModelScope.launch {
            repository.configUpdates.collect { config ->
                if (config.mode != null) {
                    _heatingConfig.value = _heatingConfig.value.copy(mode = config.mode)
                }
                if (!isUserAdjustingTarget && config.target != null) {
                    _heatingConfig.value = _heatingConfig.value.copy(
                        targetTemp = config.target.toFloat()
                    )
                }
                if (config.relay != null) {
                    _heatingConfig.value = _heatingConfig.value.copy(relayState = config.relay)
                }
            }
        }
        viewModelScope.launch {
            repository.boostUpdates.collect { _boostConfig.value = it }
        }
    }

    private suspend fun loadAllData() {
        repository.getHeatingConfig().onSuccess { _heatingConfig.value = it }
        val sensorsResult = repository.getCurrentSensors()
        sensorsResult.onSuccess { _sensors.value = it }
        repository.getCurrentWeather().onSuccess { _weather.value = it }
        repository.getBoostConfig().onSuccess { _boostConfig.value = it }
        repository.getProfiles().onSuccess { list ->
            _profiles.value = list.sortedBy { p -> p.sortOrder }
        }
        repository.getRooms().onSuccess { _rooms.value = it }
        loadHistory()
        repository.getEnergyData().onSuccess { energy ->
            _runtimeToday.value = Math.round(energy.today.runtimeMinutes)
        }
        repository.getEnergyDaily(7).onSuccess { daily ->
            _runtimeWeekly.value = daily.summary?.totalRuntimeMinutes?.let { Math.round(it) }
                ?: daily.days.sumOf { Math.round(it.runtimeMinutes).toInt() }
        }
    }

    private suspend fun loadHistory() {
        val roomId = _selectedRoomId.value
        if (roomId != null) {
            // Load history filtered by room
            repository.getSensorHistoryByRoom(roomId, "24h")
                .onSuccess { _historyPoints.value = it.points }
        } else {
            // "Tümü" — load first sensor's history (default behavior)
            _sensors.value.firstOrNull()?.deviceId?.let { deviceId ->
                repository.getSensorHistory(deviceId, "24h")
                    .onSuccess { _historyPoints.value = it.points }
            }
        }
    }

    fun selectRoom(roomId: Int?) {
        _selectedRoomId.value = roomId
        viewModelScope.launch {
            loadHistory()
        }
    }

    fun calculateEffectiveTemp(
        rooms: List<Room>,
        sensors: List<SensorReading>,
        strategy: String,
    ): Float {
        val roomsWithTemp = rooms.filter { it.currentTemp != null }
        if (roomsWithTemp.isEmpty()) return sensors.firstOrNull()?.temperature ?: 20f

        return when (strategy) {
            "weighted_avg" -> {
                val totalWeight = roomsWithTemp.sumOf { it.weight.toDouble() }
                if (totalWeight == 0.0) {
                    roomsWithTemp.first().currentTemp!!
                } else {
                    (roomsWithTemp.sumOf { (it.currentTemp!! * it.weight).toDouble() } / totalWeight).toFloat()
                }
            }
            "coldest_room" -> roomsWithTemp.minOf { it.currentTemp!! }
            "hottest_room" -> roomsWithTemp.maxOf { it.currentTemp!! }
            else -> roomsWithTemp.firstOrNull()?.currentTemp ?: sensors.firstOrNull()?.temperature ?: 20f
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            loadAllData()
            _isLoading.value = false
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadAllData()
            _isRefreshing.value = false
        }
    }

    fun updateTargetTemp(newTarget: Float) {
        isUserAdjustingTarget = true
        _heatingConfig.value = _heatingConfig.value.copy(targetTemp = newTarget)
        _activeProfileId.value = null
        viewModelScope.launch { repository.saveHeatingProfile("") }

        targetDebounceJob?.cancel()
        targetDebounceJob = viewModelScope.launch {
            delay(2500)
            repository.updateHeatingPartial(mapOf("target_temp" to newTarget))
            delay(1000) // WS echo'nun gelmesi için bekle
            isUserAdjustingTarget = false
        }
    }

    fun toggleRelay(
        currentRelayOn: Boolean,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val targetRelayState = !currentRelayOn
        val newMode = if (targetRelayState) "manual_on" else "manual_off"
        val previousMode = _heatingConfig.value.mode
        val previousRelay = _heatingConfig.value.relayState

        // Optimistic update: UI anında güncelle
        _heatingConfig.value = _heatingConfig.value.copy(
            relayState = targetRelayState,
            mode = newMode,
        )

        viewModelScope.launch {
            _relayLoading.value = true
            // Mode değiştir → backend hem DB'ye yazar hem MQTT relay komutu gönderir
            repository.updateHeatingPartial(mapOf("mode" to newMode)).fold(
                onSuccess = {
                    onSuccess(if (targetRelayState) "Kombi acildi" else "Kombi kapatildi")
                },
                onFailure = {
                    // Rollback
                    _heatingConfig.value = _heatingConfig.value.copy(
                        relayState = previousRelay,
                        mode = previousMode,
                    )
                    onFailure("Komut gonderilemedi: ${it.message}")
                },
            )
            _relayLoading.value = false
        }
    }

    fun selectMode(
        newMode: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val previousMode = _heatingConfig.value.mode
        // Optimistic update: API yaniti beklenmeden UI'i hemen guncelle
        _heatingConfig.value = _heatingConfig.value.copy(mode = newMode)

        viewModelScope.launch {
            _modeLoading.value = true
            repository.updateHeatingPartial(mapOf("mode" to newMode)).fold(
                onSuccess = { saved ->
                    _heatingConfig.value = saved
                    onSuccess(
                        if (newMode == "auto") "Otomatik mod aktif"
                        else "Manuel mod aktif ($newMode)"
                    )
                },
                onFailure = {
                    // API basarisiz olursa eski mode'a geri don
                    _heatingConfig.value = _heatingConfig.value.copy(mode = previousMode)
                    onFailure("Mod degistirilemedi: ${it.message}")
                },
            )
            _modeLoading.value = false
        }
    }

    fun selectProfile(
        profile: HeatingProfile,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _activeProfileId.value = profile.id
            repository.saveHeatingProfile(profile.id.toString())
            repository.updateHeatingPartial(
                mapOf(
                    "target_temp" to profile.targetTemp,
                    "hysteresis" to profile.hysteresis,
                    "mode" to "auto",
                )
            ).fold(
                onSuccess = { saved ->
                    _heatingConfig.value = saved
                    onSuccess("${profile.name} profili secildi (${profile.targetTemp.toInt()}°C)")
                },
                onFailure = {
                    onFailure("Profil degistirilemedi: ${it.message}")
                },
            )
        }
    }

    fun saveProfile(
        editingProfile: HeatingProfile?,
        name: String,
        icon: String,
        targetTemp: Float,
        hysteresis: Float,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val result = if (editingProfile != null) {
                repository.updateProfile(editingProfile.id, name, icon, targetTemp, hysteresis)
            } else {
                repository.createProfile(name, icon, targetTemp, hysteresis)
            }
            result.fold(
                onSuccess = {
                    repository.getProfiles().onSuccess { list ->
                        _profiles.value = list.sortedBy { p -> p.sortOrder }
                    }
                    onSuccess(
                        if (editingProfile != null) "Profil guncellendi"
                        else "Profil olusturuldu"
                    )
                },
                onFailure = {
                    onFailure("Profil kaydedilemedi: ${it.message}")
                },
            )
        }
    }

    fun deleteProfile(
        id: Int,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        viewModelScope.launch {
            repository.deleteProfile(id).fold(
                onSuccess = {
                    if (_activeProfileId.value == id) _activeProfileId.value = null
                    repository.getProfiles().onSuccess { list ->
                        _profiles.value = list.sortedBy { p -> p.sortOrder }
                    }
                    onSuccess("Profil silindi")
                },
                onFailure = {
                    onFailure("Profil silinemedi: ${it.message}")
                },
            )
        }
    }

    fun updateStrategy(
        newStrategy: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val previousStrategy = _heatingConfig.value.strategy
        _heatingConfig.value = _heatingConfig.value.copy(strategy = newStrategy)

        viewModelScope.launch {
            repository.updateHeatingPartial(mapOf("strategy" to newStrategy)).fold(
                onSuccess = { saved ->
                    _heatingConfig.value = saved
                    onSuccess("Strateji degistirildi")
                },
                onFailure = {
                    _heatingConfig.value = _heatingConfig.value.copy(strategy = previousStrategy)
                    onFailure("Strateji degistirilemedi: ${it.message}")
                },
            )
        }
    }

    fun activateBoost(minutes: Int) {
        viewModelScope.launch {
            repository.activateBoost(minutes).onSuccess { _boostConfig.value = it }
        }
    }

    fun cancelBoost() {
        viewModelScope.launch {
            repository.cancelBoost().onSuccess { _boostConfig.value = it }
        }
    }
}
