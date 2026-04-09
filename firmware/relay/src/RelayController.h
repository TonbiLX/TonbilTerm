#pragma once

// ============================================================
// RelayController.h -- Safe relay control for gas boiler (ESP8266)
//
// Safety features:
//   - Boot-glitch prevention: digitalWrite BEFORE pinMode
//   - Minimum cycle time: prevents rapid on/off
//   - Maximum runtime: force OFF after 4h, 15min cooldown
//   - Freeze protection: force ON if temp < 5C
//   - Active-LOW relay on GPIO14/D5 (boot-safe pin on ESP8266)
// ============================================================

#include <Arduino.h>
#include "ConfigStore.h"

#define RELAY_PIN       14   // D5/GPIO14 on ESP8266 NodeMCU
#define RELAY_ON_STATE  LOW
#define RELAY_OFF_STATE HIGH

enum RelayMode : uint8_t {
    RELAY_MODE_AUTO   = 0,
    RELAY_MODE_MANUAL = 1
};

struct RelayStatus {
    bool      relayOn        = false;
    RelayMode mode           = RELAY_MODE_AUTO;
    float     targetTemp     = 22.0f;
    float     lastTemp       = NAN;
    bool      freezeProtect  = false;
    bool      maxRuntimeHit  = false;
    bool      cycleLocked    = false;
    uint32_t  relayOnSince   = 0;
    uint32_t  relayOffSince  = 0;
    uint32_t  uptime         = 0;
};

class RelayController {
public:
    void begin(const RelayConfig& cfg);
    void evaluate(float currentTemp);
    void setMode(RelayMode m);
    void setManual(bool on);
    void setTarget(float target);
    void setHysteresis(float hyst);
    void forceOn(const char* reason);
    void forceOff(const char* reason);
    void updateConfig(const RelayConfig& cfg);
    RelayStatus getStatus() const;
    void loop();

private:
    RelayConfig _config;
    bool        _relayOn         = false;
    RelayMode   _mode            = RELAY_MODE_AUTO;
    float       _lastTemp        = NAN;
    bool        _freezeActive    = false;
    bool        _maxRuntimeCool  = false;

    unsigned long _relayOnSince  = 0;
    unsigned long _relayOffSince = 0;
    unsigned long _maxRuntimeCoolStart = 0;
    unsigned long _bootTime      = 0;

    bool setRelay(bool on, const char* reason);
    void checkMaxRuntime();
    void checkFreezeProtection(float temp);
    bool isCycleLocked() const;
    void logTransition(bool on, const char* reason) const;
};
