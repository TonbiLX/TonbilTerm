#pragma once

#include <Arduino.h>
#include "ConfigStore.h"

// ============================================================
// WiFiProvisioning -- WiFiManager ile otomatik provisioning (ESP8266)
//
// Ilk boot'ta AP mode acilir: "TonbilSensor-XXXX"
// Kullanici WiFi + MQTT server/port girer
// Basarili baglaninca config LittleFS'ye kaydedilir
// ============================================================

class WiFiProvisioning {
public:
    bool begin(ConfigStore& configStore, const String& deviceId);

    bool isConnected();

    void checkConnection();

    MQTTConfig getMQTTConfig() const;

private:
    MQTTConfig _mqttConfig;
    String     _deviceId;
    ConfigStore* _configStore = nullptr;

    unsigned long _lastReconnectAttempt = 0;
    static const unsigned long RECONNECT_INTERVAL = 30000; // 30 saniye

    String getAPName() const;
};
