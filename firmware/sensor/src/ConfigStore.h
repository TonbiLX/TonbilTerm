#pragma once

#include <Arduino.h>

// ============================================================
// SensorConfig -- sensor ve MQTT ayarlari
// ============================================================

struct MQTTConfig {
    char server[64]  = "";
    uint16_t port    = 1883;
};

struct SensorConfig {
    float calibOffsetTemp = 0.0f;   // Sicaklik kalibrasyon ofseti (-10..+10)
    float calibOffsetHum  = 0.0f;   // Nem kalibrasyon ofseti (-20..+20)
    uint32_t readIntervalMs = 5000; // Okuma araligi (ms)
};

// ============================================================
// ConfigStore -- LittleFS tabanli kalici depolama (ESP8266)
// ============================================================

class ConfigStore {
public:
    // LittleFS'den ayarlari yukle, ilk boot'ta default yaz
    bool begin();

    // MQTT ayarlarini yukle/kaydet
    MQTTConfig loadMQTT();
    bool saveMQTT(const MQTTConfig& cfg);

    // Sensor ayarlarini yukle/kaydet
    SensorConfig loadSensor();
    bool saveSensor(const SensorConfig& cfg);

    // Cihaz ID -- ChipId'den uretilir, bir kez kaydedilir
    // Format: "sensor-XXXX" (son 4 hex)
    String getDeviceId();

    // Tum ayarlari seri porta bas
    void printConfig();

private:
    String _deviceId;

    // ChipId'den device ID uret
    String generateDeviceId();

    static const char* FILE_MQTT;
    static const char* FILE_SENSOR;
    static const char* FILE_DEVICE;
};
