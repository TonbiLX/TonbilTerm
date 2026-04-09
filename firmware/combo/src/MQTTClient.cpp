#include "MQTTClient.h"

MQTTClient* MQTTClient::_instance = nullptr;

// ============================================================
// begin -- initialize MQTT client, build topics
// ============================================================
void MQTTClient::begin(const char* server, uint16_t port, const char* deviceId) {
    _instance = this;
    _port = port;
    strncpy(_server, server, sizeof(_server) - 1);
    strncpy(_deviceId, deviceId, sizeof(_deviceId) - 1);

    buildTopics();

    _mqtt.setClient(_wifiClient);
    _mqtt.setServer(_server, _port);
    _mqtt.setBufferSize(512);
    _mqtt.setKeepAlive(30);

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
// buildTopics
// ============================================================
void MQTTClient::buildTopics() {
    snprintf(_topicCommand,   sizeof(_topicCommand),   "tonbil/devices/%s/command",   _deviceId);
    snprintf(_topicConfig,    sizeof(_topicConfig),    "tonbil/devices/%s/config",    _deviceId);
    snprintf(_topicTelemetry, sizeof(_topicTelemetry), "tonbil/devices/%s/telemetry", _deviceId);
    snprintf(_topicStatus,    sizeof(_topicStatus),    "tonbil/devices/%s/status",    _deviceId);
}

// ============================================================
// loop -- handle MQTT reconnect
// ============================================================
void MQTTClient::loop() {
    if (!_mqtt.connected()) {
        unsigned long now = millis();
        if (now - _lastReconnectAttempt >= _reconnectInterval) {
            _lastReconnectAttempt = now;
            if (reconnect()) {
                _reconnectInterval = 1000;
            } else {
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
// setCredentials
// ============================================================
void MQTTClient::setCredentials(const char* username, const char* password) {
    strncpy(_username, username, sizeof(_username) - 1);
    strncpy(_password, password, sizeof(_password) - 1);
    _hasCredentials = (strlen(_username) > 0 && strlen(_password) > 0);

    Serial.print(F("[MQTT] Credentials set for user: "));
    Serial.println(_username);
}

void MQTTClient::onAuthFailure(AuthFailureCallback cb) {
    _onAuthFailCb = cb;
}

// ============================================================
// reconnect -- MQTT connection with LWT
// ============================================================
bool MQTTClient::reconnect() {
    Serial.print(F("[MQTT] Connecting to "));
    Serial.print(_server);
    Serial.print(F(":"));
    Serial.print(_port);
    Serial.println(F("..."));

    bool connected;
    if (_hasCredentials) {
        Serial.print(F("[MQTT] Auth user: "));
        Serial.println(_username);
        connected = _mqtt.connect(
            _deviceId, _username, _password,
            _topicStatus, 1, true, "offline"
        );
    } else {
        connected = _mqtt.connect(
            _deviceId, nullptr, nullptr,
            _topicStatus, 1, true, "offline"
        );
    }

    if (connected) {
        _consecutiveAuthFailures = 0;
        Serial.println(F("[MQTT] Connected"));

        _mqtt.subscribe(_topicCommand, 1);
        _mqtt.subscribe(_topicConfig, 1);

        Serial.print(F("[MQTT] Subscribed: "));
        Serial.println(_topicCommand);
        Serial.print(F("[MQTT] Subscribed: "));
        Serial.println(_topicConfig);

        publishOnline();
        _lastMessageMs = millis();
    } else {
        int rc = _mqtt.state();
        Serial.print(F("[MQTT] Connection failed, rc="));
        Serial.println(rc);

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
// publishOnline
// ============================================================
void MQTTClient::publishOnline() {
    _mqtt.publish(_topicStatus, "online", true);
    Serial.println(F("[MQTT] Published: online (retained)"));
}

// ============================================================
// publishTelemetry -- combined sensor + relay + boost data
// ============================================================
void MQTTClient::publishTelemetry(const RelayStatus& status, const BME280Data& bme,
                                   bool localFallback, const char* fallbackLevel,
                                   bool boostActive, int boostRemaining,
                                   int boostTotal) {
    JsonDocument doc;

    // Sensor data
    if (bme.valid) {
        doc["temp"]  = roundf(bme.temperature * 10.0f) / 10.0f;
        doc["hum"]   = roundf(bme.humidity * 10.0f) / 10.0f;
        doc["pres"]  = roundf(bme.pressure * 10.0f) / 10.0f;
    }

    // Relay status
    doc["relay"]         = status.relayOn;
    doc["mode"]          = (status.mode == RELAY_MODE_AUTO) ? "auto" : "manual";
    doc["target"]        = roundf(status.targetTemp * 10.0f) / 10.0f;
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
        doc["relayOnFor"] = status.relayOnSince;
    }

    // Last known temperature
    if (!isnan(status.lastTemp)) {
        doc["lastTemp"] = roundf(status.lastTemp * 10.0f) / 10.0f;
    }

    // Device identity
    doc["deviceId"] = _deviceId;
    doc["type"]     = "combo";

    char buffer[512];
    size_t len = serializeJson(doc, buffer, sizeof(buffer));

    if (_mqtt.publish(_topicTelemetry, buffer, false)) {
        // Silent on success
    } else {
        Serial.println(F("[MQTT] Telemetry publish failed"));
    }
}

// ============================================================
// onMessage -- handle incoming MQTT messages
// ============================================================
void MQTTClient::onMessage(char* topic, byte* payload, unsigned int length) {
    _lastMessageMs = millis();

    char msg[256];
    size_t copyLen = min((unsigned int)(sizeof(msg) - 1), length);
    memcpy(msg, payload, copyLen);
    msg[copyLen] = '\0';

    Serial.print(F("[MQTT] Received ["));
    Serial.print(topic);
    Serial.print(F("]: "));
    Serial.println(msg);

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
        // Check if this is a sensor config update (calibration)
        if (!doc["calibOffsetTemp"].isNull() || !doc["calibOffsetHum"].isNull()
            || !doc["readIntervalMs"].isNull()) {
            if (_onSensorCfgCb) _onSensorCfgCb(topic, msg, copyLen);
        }

        // Full relay config update from server
        if (!doc["targetTemp"].isNull() || !doc["hysteresis"].isNull()
            || !doc["mode"].isNull() || !doc["minCycleTime"].isNull()) {
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

void MQTTClient::onRelayCommand(RelayCommandCallback cb)   { _onRelayCb = cb; }
void MQTTClient::onTargetCommand(TargetCommandCallback cb) { _onTargetCb = cb; }
void MQTTClient::onModeCommand(ModeCommandCallback cb)     { _onModeCb = cb; }
void MQTTClient::onConfigUpdate(ConfigUpdateCallback cb)   { _onConfigCb = cb; }
void MQTTClient::onBoostCommand(BoostCommandCallback cb)   { _onBoostCb = cb; }
void MQTTClient::onBoostCancel(BoostCancelCallback cb)     { _onBoostCancelCb = cb; }
void MQTTClient::onSensorConfig(SensorConfigCallback cb)   { _onSensorCfgCb = cb; }
