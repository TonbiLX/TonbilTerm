package com.tonbil.termostat.data.api

import android.util.Log
import com.tonbil.termostat.data.model.BoilerStatus
import com.tonbil.termostat.data.model.BoostConfig
import com.tonbil.termostat.data.model.WsDeviceStatus
import com.tonbil.termostat.data.model.WsMessage
import com.tonbil.termostat.data.model.WsSensorData
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class WebSocketClient(
    private val client: HttpClient,
    private var wsUrl: String,
    private val json: Json,
) {
    companion object {
        private const val TAG = "WebSocketClient"
        private const val INITIAL_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 30000L
    }

    private var scope: CoroutineScope? = null
    private var wsJob: Job? = null
    private var reconnectDelay = INITIAL_DELAY_MS

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _sensorUpdates = MutableSharedFlow<WsSensorData>(extraBufferCapacity = 16)
    val sensorUpdates = _sensorUpdates.asSharedFlow()

    private val _boilerUpdates = MutableSharedFlow<BoilerStatus>(extraBufferCapacity = 16)
    val boilerUpdates = _boilerUpdates.asSharedFlow()

    private val _boostUpdates = MutableSharedFlow<BoostConfig>(extraBufferCapacity = 16)
    val boostUpdates = _boostUpdates.asSharedFlow()

    private val _deviceStatusUpdates = MutableSharedFlow<WsDeviceStatus>(extraBufferCapacity = 16)
    val deviceStatusUpdates = _deviceStatusUpdates.asSharedFlow()

    private val _configUpdates = MutableSharedFlow<BoilerStatus>(extraBufferCapacity = 16)
    val configUpdates = _configUpdates.asSharedFlow()

    private var token: String? = null

    fun connect(authToken: String) {
        token = authToken
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        reconnectDelay = INITIAL_DELAY_MS
        startConnection()
    }

    fun disconnect() {
        wsJob?.cancel()
        scope?.cancel()
        scope = null
        _isConnected.value = false
    }

    fun updateUrl(newUrl: String) {
        wsUrl = newUrl
        Log.d(TAG, "WS URL degistirildi: $newUrl")
    }

    private fun startConnection() {
        wsJob?.cancel()
        wsJob = scope?.launch {
            while (isActive) {
                try {
                    Log.d(TAG, "WebSocket baglanti baslatiliyor: $wsUrl")
                    client.webSocket(urlString = wsUrl, request = {
                        token?.let { t -> headers.append("Cookie", "tonbil_token=$t") }
                    }) {
                        _isConnected.value = true
                        reconnectDelay = INITIAL_DELAY_MS
                        Log.d(TAG, "WebSocket baglandi")

                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    handleMessage(frame.readText())
                                }
                                is Frame.Close -> {
                                    Log.d(TAG, "WebSocket kapatildi: ${frame.readReason()}")
                                    break
                                }
                                else -> { /* ignore */ }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "WebSocket hata: ${e.message}")
                }

                _isConnected.value = false
                Log.d(TAG, "Yeniden baglanma bekleniyor: ${reconnectDelay}ms")
                delay(reconnectDelay)
                reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_DELAY_MS)
            }
        }
    }

    private suspend fun handleMessage(text: String) {
        try {
            val msg = json.decodeFromString<WsMessage>(text)
            val data = msg.data ?: return

            when (msg.type) {
                // Backend "telemetry" gönderir, eski "sensor" uyumluluk için
                "telemetry", "sensor" -> {
                    val sensorData = json.decodeFromJsonElement<WsSensorData>(data)
                    _sensorUpdates.emit(sensorData)
                }
                "boiler" -> {
                    val status = json.decodeFromJsonElement<BoilerStatus>(data)
                    _boilerUpdates.emit(status)
                }
                // Relay durumu değiştiğinde
                "relay_state" -> {
                    try {
                        val stateVal = data.jsonObject["state"]?.let {
                            json.decodeFromJsonElement<Boolean>(it)
                        } ?: false
                        _boilerUpdates.emit(BoilerStatus(
                            active = stateVal, relay = stateVal,
                            mode = null, target = null
                        ))
                    } catch (_: Exception) {}
                }
                // Config güncellendiğinde (sunucu tarafından yayınlanır — güvenilir kaynak)
                "config_update" -> {
                    try {
                        val obj = data.jsonObject
                        val relay = obj["relay_state"]?.let { json.decodeFromJsonElement<Boolean>(it) }
                        val target = obj["target_temp"]?.let { json.decodeFromJsonElement<Double>(it) }
                        val mode = obj["mode"]?.let { json.decodeFromJsonElement<String>(it) }
                        if (relay != null || target != null || mode != null) {
                            _configUpdates.emit(BoilerStatus(
                                active = relay ?: false, relay = relay ?: false,
                                mode = mode, target = target
                            ))
                        }
                    } catch (_: Exception) {}
                }
                "boost_update" -> {
                    val boost = json.decodeFromJsonElement<BoostConfig>(data)
                    _boostUpdates.emit(boost)
                }
                "device_status" -> {
                    val deviceStatus = json.decodeFromJsonElement<WsDeviceStatus>(data)
                    _deviceStatusUpdates.emit(deviceStatus)
                }
                "ping", "initial_state" -> { /* ignore */ }
                else -> Log.d(TAG, "Bilinmeyen mesaj tipi: ${msg.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Mesaj parse hatasi: ${e.message}")
        }
    }
}
