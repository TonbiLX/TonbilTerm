#include "WiFiProvisioning.h"
#include <ESP8266WiFi.h>
#include <WiFiManager.h>

// ============================================================
// begin() -- WiFi baglantisi + captive portal
// ============================================================
bool WiFiProvisioning::begin(ConfigStore& configStore, const String& deviceId) {
    _configStore = &configStore;
    _deviceId    = deviceId;

    Serial.println(F("[WIFI] WiFi provisioning baslatiyor..."));

    // LittleFS'den kayitli MQTT config'i yukle
    _mqttConfig = _configStore->loadMQTT();

    // WiFiManager olustur
    WiFiManager wm;

    // Debug output
    wm.setDebugOutput(true);

    // Portal timeout: 180 saniye, sonra restart
    wm.setConfigPortalTimeout(180);

    // Otomatik reconnect
    wm.setConnectRetries(3);

    // --- Custom MQTT parametreleri ---
    char mqttServerBuf[64];
    char mqttPortBuf[8];
    strncpy(mqttServerBuf, _mqttConfig.server, sizeof(mqttServerBuf));
    snprintf(mqttPortBuf, sizeof(mqttPortBuf), "%u", _mqttConfig.port);

    WiFiManagerParameter paramMqttServer("mqtt_server", "MQTT Server IP", mqttServerBuf, 63);
    WiFiManagerParameter paramMqttPort("mqtt_port", "MQTT Port", mqttPortBuf, 7);

    wm.addParameter(&paramMqttServer);
    wm.addParameter(&paramMqttPort);

    // Save params callback
    bool shouldSaveConfig = false;
    wm.setSaveParamsCallback([&shouldSaveConfig]() {
        shouldSaveConfig = true;
    });

    // AP ismini olustur
    String apName = getAPName();
    Serial.print(F("[WIFI] AP ismi: "));
    Serial.println(apName);

    // Pre-set WiFi credentials (skip portal if hardcoded)
    #ifdef WIFI_SSID
    WiFi.persistent(true);
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASS);
    Serial.print(F("[WIFI] Hardcoded SSID: "));
    Serial.println(WIFI_SSID);
    Serial.print(F("[WIFI] Pass length: "));
    Serial.println(strlen(WIFI_PASS));
    unsigned long wStart = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - wStart < 30000) {
        delay(500);
        Serial.print(".");
        if (WiFi.status() == WL_CONNECT_FAILED) {
            Serial.println(F("\n[WIFI] WL_CONNECT_FAILED"));
            break;
        }
        if (WiFi.status() == WL_NO_SSID_AVAIL) {
            Serial.println(F("\n[WIFI] WL_NO_SSID_AVAIL - SSID bulunamadi"));
            break;
        }
        if (WiFi.status() == WL_WRONG_PASSWORD) {
            Serial.println(F("\n[WIFI] WL_WRONG_PASSWORD"));
            break;
        }
    }
    Serial.println();
    Serial.print(F("[WIFI] Status: "));
    Serial.println(WiFi.status());
    bool connected = (WiFi.status() == WL_CONNECTED);
    if (!connected) {
        Serial.println(F("[WIFI] Hardcoded WiFi failed, falling back to portal"));
        WiFi.disconnect();
        delay(1000);
        connected = wm.autoConnect(apName.c_str(), "tonbil123");
    }
    #else
    // autoConnect: kayitli ag varsa baglan, yoksa portal ac
    bool connected = wm.autoConnect(apName.c_str(), "tonbil123");
    #endif

    if (!connected) {
        Serial.println(F("[WIFI] Baglanti basarisiz! 10 saniye sonra restart..."));
        delay(10000);
        ESP.restart();
        return false;
    }

    Serial.println(F("[WIFI] WiFi baglandi!"));
    Serial.print(F("[WIFI] IP: "));
    Serial.println(WiFi.localIP());
    Serial.print(F("[WIFI] RSSI: "));
    Serial.print(WiFi.RSSI());
    Serial.println(F(" dBm"));

    // Portal'dan yeni config geldi mi?
    if (shouldSaveConfig) {
        strncpy(_mqttConfig.server, paramMqttServer.getValue(), sizeof(_mqttConfig.server) - 1);
        _mqttConfig.server[sizeof(_mqttConfig.server) - 1] = '\0';

        uint16_t port = (uint16_t)atoi(paramMqttPort.getValue());
        _mqttConfig.port = (port > 0 && port < 65535) ? port : 1883;

        _configStore->saveMQTT(_mqttConfig);

        Serial.println(F("[WIFI] Yeni MQTT config kaydedildi"));
    }

    return true;
}

// ============================================================
// isConnected()
// ============================================================
bool WiFiProvisioning::isConnected() {
    return WiFi.status() == WL_CONNECTED;
}

// ============================================================
// checkConnection() -- baglanti koparsa tekrar dene
// ============================================================
void WiFiProvisioning::checkConnection() {
    if (WiFi.status() == WL_CONNECTED) {
        return;
    }

    unsigned long now = millis();
    if (now - _lastReconnectAttempt < RECONNECT_INTERVAL) {
        return;
    }
    _lastReconnectAttempt = now;

    Serial.println(F("[WIFI] Baglanti koptu, yeniden deniyor..."));
    WiFi.reconnect();

    // 10 saniye bekle
    unsigned long start = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - start < 10000) {
        delay(100);
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.println(F("[WIFI] Yeniden baglandi!"));
        Serial.print(F("[WIFI] IP: "));
        Serial.println(WiFi.localIP());
    } else {
        Serial.println(F("[WIFI] Yeniden baglanti basarisiz"));
    }
}

// ============================================================
// getMQTTConfig()
// ============================================================
MQTTConfig WiFiProvisioning::getMQTTConfig() const {
    return _mqttConfig;
}

// ============================================================
// getAPName() -- "TonbilSensor-XXXX"
// ============================================================
String WiFiProvisioning::getAPName() const {
    String suffix = _deviceId;
    int dashIdx = suffix.indexOf('-');
    if (dashIdx >= 0) {
        suffix = suffix.substring(dashIdx + 1);
    }
    return "TonbilSensor-" + suffix;
}
