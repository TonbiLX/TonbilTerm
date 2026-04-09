#include "MQTTClient.h"

// Static instance for PubSubClient callback routing
MQTTClient* MQTTClient::_instance = nullptr;

// ============================================================
// begin — initialize MQTT client, build topics
// ============================================================
void MQTTClient::begin(const char* server, uint16_t port, const char* deviceId) {
    _instance = this;
    _port = port;
    strncpy(_server, server, sizeof(_server) - 1);
    strncpy(_deviceId, deviceId, sizeof(_deviceId) - 1);

    buildTopics();

    _mqtt.setClient(_wifiClient);
    _mqtt.setServer(_server, _port);
    _mqtt.setBufferSize(512);  // Larger buffer for JSON payloads
    _mqtt.setKeepAlive(30);

    // Static callback wrapper — routes to instance method
    _mqtt.setCallback([](char* topic, byte* payload, unsigned int length) {
        if (_instance) {
            _instance->onMessage(topic, payload, length);
        }
    });

    Serial.print(F("[MQTT] Configured: "));
    Serial.print(_server);
    Serial.print(F(":"));
    Serial.println(_port);
    Serial.print(F("[MQTT] Device: "));
    Serial.println(_deviceId);
}

// ============================================================
// buildTopics — construct MQTT topic strings
// ============================================================
void MQTTClient::buildTopics() {
    snprintf(_topicCommand,   sizeof(_topicCommand),   "tonbil/devices/%s/command",   _deviceId);
    snprintf(_topicConfig,    sizeof(_topicConfig),    "tonbil/devices/%s/config",    _deviceId);
    snprintf(_topicTelemetry, sizeof(_topicTelemetry), "tonbil/devices/%s/telemetry", _deviceId);
    snprintf(_topicStatus,    sizeof(_topicStatus),    "tonbil/devices/%s/status",    _deviceId);
}

// ============================================================
// loop — handle MQTT, reconnect, periodic telemetry
// ============================================================
void MQTTClient::loop() {
    if (!_mqtt.connected()) {
        unsigned long now = millis();
        if (now - _lastReconnectAttempt >= _reconnectInterval) {
            _lastReconnectAttempt = now;
            if (reconnect()) {
                _reconnectInterval = 1000;  // Reset backoff on success
            } else {
                // Exponential backoff: 1s, 2s, 4s, 8s, ... 60s max
                _reconnectInterval = min(_reconnectInterval * 2, MAX_RECONNECT_INTERVAL);
                Serial.print(F("[MQTT] Next retry in "));
                Serial.print(_reconnectInterval / 1000);
                Serial.println(F("s"));
            }
        }
        return;
    }

    _mqtt.loop();
}

// ============================================================
// setCredentials — set MQTT auth credentials
// ============================================================
void MQTTClient::setCredentials(const char* username, const char* password) {
    strncpy(_username, username, sizeof(_username) - 1);
    strncpy(_password, password, sizeof(_password) - 1);
    _hasCredentials = (strlen(_username) > 0 && strlen(_password) > 0);

    Serial.print(F("[MQTT] Credentials set for user: "));
    Serial.println(_username);
}

// ============================================================
// onAuthFailure — set auth failure callback
// ============================================================
void MQTTClient::onAuthFailure(AuthFailureCallback cb) {
    _onAuthFailCb = cb;
}

// ============================================================
// reconnect — attempt MQTT connection with LWT
// ============================================================
bool MQTTClient::reconnect() {
    Serial.print(F("[MQTT] Connecting to "));
    Serial.print(_server);
    Serial.print(F(":"));
    Serial.print(_port);
    Serial.println(F("..."));

    // LWT: publish "offline" to status topic on ungraceful disconnect
    bool connected;
    if (_hasCredentials) {
        Serial.print(F("[MQTT] Auth user: "));
        Serial.println(_username);
        connected = _mqtt.connect(
            _deviceId,         // client ID
            _username,         // username
            _password,         // password
            _topicStatus,      // LWT topic
            1,                 // LWT QoS
            true,              // LWT retain
            "offline"          // LWT message
        );
    } else {
        connected = _mqtt.connect(
            _deviceId,         // client ID
            nullptr,           // username (none)
            nullptr,           // password (none)
            _topicStatus,      // LWT topic
            1,                 // LWT QoS
            true,              // LWT retain
            "offline"          // LWT message
        );
    }

    if (connected) {
        _consecutiveAuthFailures = 0;
        Serial.println(F("[MQTT] Connected"));

        // Subscribe to command and config topics
        _mqtt.subscribe(_topicCommand, 1);
        _mqtt.subscribe(_topicConfig, 1);

        Serial.print(F("[MQTT] Subscribed: "));
        Serial.println(_topicCommand);
        Serial.print(F("[MQTT] Subscribed: "));
        Serial.println(_topicConfig);

        // Publish online status (retained)
        publishOnline();

        _lastMessageMs = millis();
    } else {
        int rc = _mqtt.state();
        Serial.print(F("[MQTT] Connection failed, rc="));
        Serial.println(rc);

        // rc=4: bad credentials, rc=5: not authorized
        if (_hasCredentials && (rc == 4 || rc == 5)) {
            _consecutiveAuthFailures++;
            Serial.print(F("[MQTT] Auth failure #"));
            Serial.println(_consecutiveAuthFailures);

            if (_consecutiveAuthFailures >= 3 && _onAuthFailCb) {
                Serial.println(F("[MQTT] 3 consecutive auth failures, triggering re-provision"));
                _consecutiveAuthFailures = 0;
                _onAuthFailCb();
            }
        }
    }

    return connected;
}

// ============================================================
// publishOnline — announce device is online
// ============================================================
void MQTTClient::publishOnline() {
    _mqtt.publish(_topicStatus, "online", true);  // retained
    Serial.println(F("[MQTT] Published: online (retained)"));
}

// ============================================================
// publishTelemetry — relay status + optional sensor data
// ============================================================
void MQTTClient::publishTelemetry(const RelayStatus& status, const BME280Data& bme,
                                   bool localFallback, const char* fallbackLevel,
                                   bool boostActive, int boostRemaining,
                                   int boostTotal) {
    JsonDocument doc;

    doc["relay"]         = status.relayOn;
    doc["mode"]          = (status.mode == RELAY_MODE_AUTO) ? "auto" : "manual";
    doc["target"]        = serialized(String(status.targetTemp, 1));
    doc["uptime"]        = status.uptime;
    doc["localFallback"] = localFallback;

    if (localFallback && fallbackLevel) {
        doc["fallbackLevel"] = fallbackLevel;
    }

    // Boost mode
    doc["boost"] = boostActive;
    if (boostActive) {
        doc["boostRemaining"] = boostRemaining;
        doc["boostTotal"]     = boostTotal;
    }

    // Safety flags
    if (status.freezeProtect)  doc["freezeProtect"]  = true;
    if (status.maxRuntimeHit)  doc["maxRuntimeCool"]  = true;
    if (status.cycleLocked)    doc["cycleLocked"]     = true;

    // Relay timing
    if (status.relayOn) {
        doc["relayOnFor"] = status.relayOnSince;  // seconds relay has been ON
    }

    // Optional local sensor data
    if (bme.valid) {
        doc["temperature"] = serialized(String(bme.temperature, 2));
        doc["humidity"]    = serialized(String(bme.humidity, 1));
        doc["pressure"]    = serialized(String(bme.pressure, 1));
    }

    // Last known temperature (may come from local sensor or server-forwarded)
    if (!isnan(status.lastTemp)) {
        doc["lastTemp"] = serialized(String(status.lastTemp, 1));
    }

    // Device type
    doc["type"] = "relay";

    char buffer[512];
    size_t len = serializeJson(doc, buffer, sizeof(buffer));

    if (_mqtt.publish(_topicTelemetry, buffer, false)) {
        // Silent on success — too frequent for serial
    } else {
        Serial.println(F("[MQTT] Telemetry publish failed"));
    }
}

// ============================================================
// onMessage — handle incoming MQTT messages
// ============================================================
void MQTTClient::onMessage(char* topic, byte* payload, unsigned int length) {
    _lastMessageMs = millis();

    // Null-terminate payload for safe parsing
    char msg[256];
    size_t copyLen = min((unsigned int)(sizeof(msg) - 1), length);
    memcpy(msg, payload, copyLen);
    msg[copyLen] = '\0';

    Serial.print(F("[MQTT] Received ["));
    Serial.print(topic);
    Serial.print(F("]: "));
    Serial.println(msg);

    // Parse JSON
    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, msg, copyLen);
    if (err) {
        Serial.print(F("[MQTT] JSON parse error: "));
        Serial.println(err.c_str());
        return;
    }

    // Route by topic
    if (strcmp(topic, _topicCommand) == 0) {
        const char* cmd = doc["cmd"] | "";

        if (strcmp(cmd, "setRelay") == 0) {
            bool value = doc["value"] | false;
            Serial.print(F("[MQTT] Command: setRelay = "));
            Serial.println(value ? F("ON") : F("OFF"));
            if (_onRelayCb) _onRelayCb(value);

        } else if (strcmp(cmd, "setTarget") == 0) {
            float value = doc["value"] | 22.0f;
            Serial.print(F("[MQTT] Command: setTarget = "));
            Serial.println(value, 1);
            if (_onTargetCb) _onTargetCb(value);

        } else if (strcmp(cmd, "setMode") == 0) {
            const char* value = doc["value"] | "auto";
            Serial.print(F("[MQTT] Command: setMode = "));
            Serial.println(value);
            if (_onModeCb) _onModeCb(value);

        } else if (strcmp(cmd, "boost") == 0) {
            int minutes = doc["minutes"] | 30;
            Serial.print(F("[MQTT] Command: boost = "));
            Serial.print(minutes);
            Serial.println(F(" min"));
            if (_onBoostCb) _onBoostCb(minutes);

        } else if (strcmp(cmd, "boostCancel") == 0) {
            Serial.println(F("[MQTT] Command: boostCancel"));
            if (_onBoostCancelCb) _onBoostCancelCb();

        } else {
            Serial.print(F("[MQTT] Unknown command: "));
            Serial.println(cmd);
        }

    } else if (strcmp(topic, _topicConfig) == 0) {
        // Full config update from server
        RelayConfig cfg;
        if (!doc["targetTemp"].isNull())       cfg.targetTemp       = doc["targetTemp"] | 22.0f;
        if (!doc["hysteresis"].isNull())       cfg.hysteresis       = doc["hysteresis"] | 0.3f;
        if (!doc["minCycleTime"].isNull())     cfg.minCycleTime     = doc["minCycleTime"] | 180;
        if (!doc["maxRuntime"].isNull())       cfg.maxRuntime       = doc["maxRuntime"] | 14400;
        if (!doc["freezeThreshold"].isNull())  cfg.freezeThreshold  = doc["freezeThreshold"] | 5.0f;

        const char* modeStr = doc["mode"] | "auto";
        cfg.mode = (strcmp(modeStr, "manual") == 0) ? 1 : 0;

        Serial.println(F("[MQTT] Config update received"));
        if (_onConfigCb) _onConfigCb(cfg);
    }
}

// ============================================================
// Accessors
// ============================================================
bool MQTTClient::isConnected() {
    return _mqtt.connected();
}

unsigned long MQTTClient::lastMessageTime() const {
    return _lastMessageMs;
}

void MQTTClient::onRelayCommand(RelayCommandCallback cb)  { _onRelayCb = cb; }
void MQTTClient::onTargetCommand(TargetCommandCallback cb) { _onTargetCb = cb; }
void MQTTClient::onModeCommand(ModeCommandCallback cb)    { _onModeCb = cb; }
void MQTTClient::onConfigUpdate(ConfigUpdateCallback cb)  { _onConfigCb = cb; }
void MQTTClient::onBoostCommand(BoostCommandCallback cb)  { _onBoostCb = cb; }
void MQTTClient::onBoostCancel(BoostCancelCallback cb)    { _onBoostCancelCb = cb; }
