#include "MQTTClient.h"
#include <ArduinoJson.h>

// Static instance pointer (PubSubClient callback bridge icin)
MQTTClient* MQTTClient::_instance = nullptr;

// ============================================================
// begin() — MQTT client baslat
// ============================================================
bool MQTTClient::begin(const char* server, uint16_t port, const String& deviceId) {
    _instance = this;
    _deviceId = deviceId;
    strncpy(_server, server, sizeof(_server) - 1);
    _server[sizeof(_server) - 1] = '\0';
    _port = port;

    Serial.print(F("[MQTT] Baslatiyor: "));
    Serial.print(_server);
    Serial.print(F(":"));
    Serial.println(_port);

    _mqttClient.setClient(_wifiClient);
    _mqttClient.setServer(_server, _port);
    _mqttClient.setCallback(mqttCallback);

    // PubSubClient buffer boyutu — buyuk JSON mesajlari icin
    _mqttClient.setBufferSize(512);

    // Topic'leri olustur
    buildTopics();

    // Ilk baglanti denemesi
    if (strlen(_server) > 0) {
        return connectInternal();
    }

    Serial.println(F("[MQTT] Server adresi bos, baglanti atlanıyor"));
    return false;
}

// ============================================================
// loop() — reconnect + PubSubClient loop
// ============================================================
void MQTTClient::loop() {
    if (_mqttClient.connected()) {
        _mqttClient.loop();
        return;
    }

    // Server bos ise deneme
    if (strlen(_server) == 0) {
        return;
    }

    // Exponential backoff ile reconnect
    unsigned long now = millis();
    if (now - _lastReconnectAttempt < _reconnectDelay) {
        return;
    }
    _lastReconnectAttempt = now;

    Serial.print(F("[MQTT] Yeniden baglanıyor (bekle: "));
    Serial.print(_reconnectDelay / 1000);
    Serial.println(F("s)..."));

    if (connectInternal()) {
        _reconnectDelay = 1000; // Reset backoff
    } else {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s, 30s...
        _reconnectDelay = min(_reconnectDelay * 2, MAX_RECONNECT_DELAY);
    }
}

// ============================================================
// publishTelemetry() — sensor verisini JSON olarak gonder
// ============================================================
bool MQTTClient::publishTelemetry(const SensorReading& reading) {
    if (!_mqttClient.connected()) {
        return false;
    }

    if (!reading.valid) {
        return false;
    }

    // JSON olustur
    // Not: ArduinoJson float'lari olduklari gibi yazar (orn: 22.3)
    // Kesirli hassasiyet kontrolu icin round kullaniyoruz
    JsonDocument doc;
    doc["temp"]     = roundf(reading.temp * 10.0f) / 10.0f;
    doc["hum"]      = roundf(reading.hum * 10.0f) / 10.0f;
    doc["pres"]     = roundf(reading.pres * 10.0f) / 10.0f;
    doc["ts"]       = (unsigned long)(millis() / 1000); // uptime saniye
    doc["deviceId"] = _deviceId;

    char buffer[256];
    size_t len = serializeJson(doc, buffer, sizeof(buffer));

    if (len == 0) {
        Serial.println(F("[MQTT] JSON serialize basarisiz"));
        return false;
    }

    bool ok = _mqttClient.publish(_topicTelemetry.c_str(), buffer, false);

    if (ok) {
        _publishCount++;
        // Her 10 mesajda bir log bas (5s * 10 = 50s aralik)
        if (_publishCount % 10 == 1) {
            Serial.print(F("[MQTT] Telemetri #"));
            Serial.print(_publishCount);
            Serial.print(F(": T="));
            Serial.print(reading.temp, 1);
            Serial.print(F(" H="));
            Serial.print(reading.hum, 1);
            Serial.print(F(" P="));
            Serial.println(reading.pres, 1);
        }
    } else {
        Serial.println(F("[MQTT] Telemetri publish basarisiz"));
    }

    return ok;
}

// ============================================================
// isConnected()
// ============================================================
bool MQTTClient::isConnected() {
    return _mqttClient.connected();
}

// ============================================================
// onConfigMessage() — config callback ayarla
// ============================================================
void MQTTClient::onConfigMessage(MQTTConfigCallback callback) {
    _configCallback = callback;
}

// ============================================================
// updateServer() — sunucu adresi guncelle
// ============================================================
void MQTTClient::updateServer(const char* server, uint16_t port) {
    strncpy(_server, server, sizeof(_server) - 1);
    _server[sizeof(_server) - 1] = '\0';
    _port = port;

    _mqttClient.setServer(_server, _port);
    _reconnectDelay = 1000; // Reset backoff

    Serial.print(F("[MQTT] Server guncellendi: "));
    Serial.print(_server);
    Serial.print(F(":"));
    Serial.println(_port);
}

// ============================================================
// setCredentials() — MQTT auth bilgileri ayarla
// ============================================================
void MQTTClient::setCredentials(const char* username, const char* password) {
    strncpy(_username, username, sizeof(_username) - 1);
    _username[sizeof(_username) - 1] = '\0';
    strncpy(_password, password, sizeof(_password) - 1);
    _password[sizeof(_password) - 1] = '\0';
    _hasCredentials = (strlen(_username) > 0 && strlen(_password) > 0);

    Serial.print(F("[MQTT] Credentials set for user: "));
    Serial.println(_username);
}

// ============================================================
// onAuthFailure() — auth failure callback ayarla
// ============================================================
void MQTTClient::onAuthFailure(AuthFailureCallback callback) {
    _authFailureCallback = callback;
}

// ============================================================
// connectInternal() — MQTT baglantisi kur
// ============================================================
bool MQTTClient::connectInternal() {
    Serial.print(F("[MQTT] Baglanıyor: "));
    Serial.print(_server);
    Serial.print(F(":"));
    Serial.print(_port);
    Serial.print(F(" clientId="));
    Serial.println(_deviceId);

    // LWT (Last Will and Testament) — baglanti koparsa "offline" yayinla
    bool connected;
    if (_hasCredentials) {
        Serial.print(F("[MQTT] Auth user: "));
        Serial.println(_username);
        connected = _mqttClient.connect(
            _deviceId.c_str(),       // client ID
            _username,                // username
            _password,                // password
            _topicStatus.c_str(),    // will topic
            1,                        // will QoS
            true,                     // will retain
            "offline"                 // will message
        );
    } else {
        connected = _mqttClient.connect(
            _deviceId.c_str(),       // client ID
            nullptr,                  // username (yok)
            nullptr,                  // password (yok)
            _topicStatus.c_str(),    // will topic
            1,                        // will QoS
            true,                     // will retain
            "offline"                 // will message
        );
    }

    if (!connected) {
        int rc = _mqttClient.state();
        Serial.print(F("[MQTT] Baglanti basarisiz, rc="));
        Serial.println(rc);

        // rc=4: bad credentials, rc=5: not authorized
        if (_hasCredentials && (rc == 4 || rc == 5)) {
            _consecutiveAuthFailures++;
            Serial.print(F("[MQTT] Auth failure #"));
            Serial.println(_consecutiveAuthFailures);

            if (_consecutiveAuthFailures >= 3 && _authFailureCallback) {
                Serial.println(F("[MQTT] 3 consecutive auth failures, triggering re-provision"));
                _consecutiveAuthFailures = 0;
                _authFailureCallback();
            }
        }
        return false;
    }

    _consecutiveAuthFailures = 0;

    Serial.println(F("[MQTT] Baglandi!"));

    // Status: online (retained)
    _mqttClient.publish(_topicStatus.c_str(), "online", true);
    Serial.print(F("[MQTT] Status: online -> "));
    Serial.println(_topicStatus);

    // Config topic'ine abone ol
    _mqttClient.subscribe(_topicConfig.c_str(), 1);
    Serial.print(F("[MQTT] Subscribe: "));
    Serial.println(_topicConfig);

    return true;
}

// ============================================================
// buildTopics() — topic string'leri olustur
// ============================================================
void MQTTClient::buildTopics() {
    _topicTelemetry = "tonbil/devices/" + _deviceId + "/telemetry";
    _topicStatus    = "tonbil/devices/" + _deviceId + "/status";
    _topicConfig    = "tonbil/devices/" + _deviceId + "/config";

    Serial.println(F("[MQTT] Topic'ler:"));
    Serial.print(F("[MQTT]   telemetry: "));
    Serial.println(_topicTelemetry);
    Serial.print(F("[MQTT]   status:    "));
    Serial.println(_topicStatus);
    Serial.print(F("[MQTT]   config:    "));
    Serial.println(_topicConfig);
}

// ============================================================
// mqttCallback() — gelen mesaj (static bridge)
// ============================================================
void MQTTClient::mqttCallback(char* topic, byte* payload, unsigned int length) {
    if (_instance == nullptr) return;

    // Null-terminate payload
    char msg[256];
    unsigned int copyLen = min(length, (unsigned int)(sizeof(msg) - 1));
    memcpy(msg, payload, copyLen);
    msg[copyLen] = '\0';

    Serial.print(F("[MQTT] Mesaj alindi ["));
    Serial.print(topic);
    Serial.print(F("]: "));
    Serial.println(msg);

    // Config topic'ine gelen mesajlari callback'e ilet
    if (_instance->_configCallback != nullptr) {
        String topicStr(topic);
        if (topicStr == _instance->_topicConfig) {
            _instance->_configCallback(topic, msg, copyLen);
        }
    }
}
