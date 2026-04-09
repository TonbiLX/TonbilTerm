#pragma once

// ============================================================
// ConfigStore.h -- LittleFS-backed persistent configuration (ESP8266)
//
// Stores: MQTT server/port, target temp, hysteresis, mode,
//         min cycle time, max runtime, freeze threshold.
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

class ConfigStore {
public:
    bool        begin();
    RelayConfig load();
    bool        save(const RelayConfig& cfg);

    // Individual field updates
    void setTargetTemp(float t);
    void setMode(uint8_t m);
    void setMqttServer(const char* server, uint16_t port);

    void print(const RelayConfig& cfg) const;

private:
    static const char* CONFIG_FILE;
};
