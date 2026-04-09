#include "WiFiProvisioning.h"
#include <ESP8266WiFi.h>

// ============================================================
// buildNames -- set device ID and AP name from ConfigStore
// ============================================================
void WiFiProvisioning::buildNames(const char* deviceId) {
    strncpy(_deviceId, deviceId, sizeof(_deviceId) - 1);
    snprintf(_apName, sizeof(_apName), "TonbilCombo-%s", deviceId + 6);
    // deviceId = "combo-XXXX", after "combo-" we get "XXXX"
}

// ============================================================
// begin -- start WiFiManager, connect or open config portal
// ============================================================
bool WiFiProvisioning::begin(ConfigStore& configStore) {
    buildNames(configStore.getDeviceId());

    Serial.print(F("[WIFI] Device ID: "));
    Serial.println(_deviceId);
    Serial.print(F("[WIFI] AP Name: "));
    Serial.println(_apName);

    // Load existing MQTT config to pre-fill portal fields
    RelayConfig cfg = configStore.loadRelay();
    strncpy(_mqttServerParam, cfg.mqttServer, sizeof(_mqttServerParam) - 1);
    snprintf(_mqttPortParam, sizeof(_mqttPortParam), "%u", cfg.mqttPort);

    // WiFiManager setup
    WiFiManager wm;
    wm.setConfigPortalTimeout(180);  // 3 min portal timeout, then retry
    wm.setConnectTimeout(15);        // 15s connection attempt timeout
    wm.setDebugOutput(true);

    // Custom MQTT parameters in portal
    WiFiManagerParameter mqttServerField("mqtt_server", "MQTT Server IP", _mqttServerParam, 63);
    WiFiManagerParameter mqttPortField("mqtt_port", "MQTT Port", _mqttPortParam, 7);
    wm.addParameter(&mqttServerField);
    wm.addParameter(&mqttPortField);

    // Save callback -- persist MQTT params
    wm.setSaveParamsCallback([&]() {
        strncpy(_mqttServerParam, mqttServerField.getValue(), sizeof(_mqttServerParam) - 1);
        strncpy(_mqttPortParam, mqttPortField.getValue(), sizeof(_mqttPortParam) - 1);

        // Validate and save to ConfigStore
        uint16_t port = (uint16_t)atoi(_mqttPortParam);
        if (port == 0) port = 1883;

        configStore.setMqttServer(_mqttServerParam, port);
        Serial.print(F("[WIFI] MQTT config saved: "));
        Serial.print(_mqttServerParam);
        Serial.print(F(":"));
        Serial.println(port);
    });

    // Attempt auto-connect, fall back to config portal
    bool connected = wm.autoConnect(_apName);

    if (connected) {
        _connected = true;
        Serial.print(F("[WIFI] Connected to: "));
        Serial.println(WiFi.SSID());
        Serial.print(F("[WIFI] IP: "));
        Serial.println(WiFi.localIP());
        Serial.print(F("[WIFI] RSSI: "));
        Serial.print(WiFi.RSSI());
        Serial.println(F(" dBm"));

        WiFi.setAutoReconnect(true);
    } else {
        _connected = false;
        Serial.println(F("[WIFI] Failed to connect -- will retry on next boot"));
    }

    return _connected;
}

// ============================================================
// loop -- periodic reconnection check
// ============================================================
void WiFiProvisioning::loop() {
    unsigned long now = millis();

    if (WiFi.status() == WL_CONNECTED) {
        if (!_connected) {
            _connected = true;
            Serial.print(F("[WIFI] Reconnected. IP: "));
            Serial.println(WiFi.localIP());
        }
        return;
    }

    // Lost connection
    if (_connected) {
        _connected = false;
        Serial.println(F("[WIFI] Connection lost"));
    }

    // Attempt reconnect every 30 seconds
    if (now - _lastReconnectAttempt >= 30000) {
        _lastReconnectAttempt = now;
        Serial.println(F("[WIFI] Attempting reconnect..."));
        WiFi.reconnect();
    }
}

// ============================================================
// Accessors
// ============================================================
const char* WiFiProvisioning::getDeviceId() const {
    return _deviceId;
}

bool WiFiProvisioning::isConnected() const {
    return _connected && (WiFi.status() == WL_CONNECTED);
}

void WiFiProvisioning::resetCredentials() {
    WiFiManager wm;
    wm.resetSettings();
    Serial.println(F("[WIFI] Credentials reset -- restart to reconfigure"));
}
