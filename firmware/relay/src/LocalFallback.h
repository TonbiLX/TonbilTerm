#pragma once

// ============================================================
// LocalFallback.h — Server disconnection fallback controller
//
// Monitors MQTT connectivity and provides fallback behavior:
//   Level 1 (0-5 min):   Normal MQTT operation
//   Level 2 (5 min+):    LOCAL mode — use local BME280 + hysteresis
//   Level 3 (1 hour+):   FIXED CYCLE — 15min ON / 45min OFF
//   Level 4 (reconnect): Sync state, exit fallback
//
// All transitions are logged to Serial.
// ============================================================

#include <Arduino.h>
#include "ConfigStore.h"

enum FallbackLevel : uint8_t {
    FB_NORMAL      = 0,  // Server connected
    FB_LOCAL       = 1,  // Server lost, local sensor control
    FB_FIXED_CYCLE = 2   // Server lost long, fixed on/off cycle
};

class LocalFallback {
public:
    void begin(const RelayConfig& cfg);

    // Call every loop — updates fallback state
    void loop(bool mqttConnected, bool hasSensor);

    // Notify that a server message was received
    void onServerMessage();

    // Get current fallback state
    FallbackLevel getLevel() const;
    bool          isInFallback() const;

    // For fixed cycle mode — should relay be ON?
    bool fixedCycleRelayState() const;

    // Config update
    void updateConfig(const RelayConfig& cfg);

private:
    RelayConfig   _config;
    FallbackLevel _level           = FB_NORMAL;

    unsigned long _lastServerMsg   = 0;
    unsigned long _fallbackEntryMs = 0;

    // Fixed cycle tracking
    unsigned long _cycleStartMs    = 0;
    bool          _cyclePhaseOn    = true;  // true=ON phase, false=OFF phase

    void enterLocal();
    void enterFixedCycle();
    void exitFallback();
};
