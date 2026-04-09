#pragma once

// ============================================================
// ConfigStore.h -- LittleFS-backed persistent configuration (Combo)
//
// Stores: MQTT server/port, target temp, hysteresis, mode,
//         min cycle time, max runtime, freeze threshold,
//         sensor calibration, read interval.
// Uses LittleFS JSON files -- ESP8266 has no Preferences/NVS.
// ============================================================

#include <Arduino.h>

struct RelayConfig {
    // MQTT
    char     mqttServer[64]   = "192.168.1.100";
    uint16_t mqttPort          = 1883;

    // Thermostat
    float    targetTemp        = 22.0f;
    float    hysteresis        = 0.3f;

    // Timing (seconds)
    uint32_t minCycleTime      = 180;     // 3 minutes
    uint32_t maxRuntime        = 14400;   // 4 hours
    uint32_t maxRuntimeCooldown = 900;    // 15 minutes forced OFF after max runtime

    // Safety
    float    freezeThreshold   = 5.0f;    // Force ON below this temp

    // Fallback
    uint32_t fallbackTimeout   = 300;     // 5 min -- enter LOCAL mode
    uint32_t fallbackCycleOn   = 900;     // 15 min ON in fixed cycle
    uint32_t fallbackCycleOff  = 2700;    // 45 min OFF in fixed cycle

    // Mode: 0=auto, 1=manual
    uint8_t  mode              = 0;
};

struct SensorConfig {
    float calibOffsetTemp = 0.0f;   // Sicaklik kalibrasyon ofseti (-10..+10)
    float calibOffsetHum  = 0.0f;   // Nem kalibrasyon ofseti (-20..+20)
    uint32_t readIntervalMs = 5000; // Okuma araligi (ms)
};

class ConfigStore {
public:
    bool        begin();
    RelayConfig loadRelay();
    bool        saveRelay(const RelayConfig& cfg);

    SensorConfig loadSensor();
    bool         saveSensor(const SensorConfig& cfg);

    // Individual field updates
    void setTargetTemp(float t);
    void setMode(uint8_t m);
    void setMqttServer(const char* server, uint16_t port);

    // Device ID -- ChipId'den uretilir, LittleFS'de saklanir
    // Format: "combo-XXXX" (son 4 hex)
    const char* getDeviceId();

    void printConfig();

private:
    char _deviceId[16] = {0};

    void buildDeviceId();

    static const char* FILE_RELAY;
    static const char* FILE_SENSOR;
    static const char* FILE_DEVICE;
};
