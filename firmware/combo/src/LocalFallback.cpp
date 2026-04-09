#include "LocalFallback.h"

// ============================================================
// begin -- initialize with config, set baseline time
// ============================================================
void LocalFallback::begin(const RelayConfig& cfg) {
    _config = cfg;
    _lastServerMsg = millis();
    _level = FB_NORMAL;

    Serial.println(F("[FALLBACK] Initialized -- NORMAL mode"));
    Serial.print(F("[FALLBACK] Timeout: "));
    Serial.print(_config.fallbackTimeout);
    Serial.print(F("s | Cycle: "));
    Serial.print(_config.fallbackCycleOn);
    Serial.print(F("s ON / "));
    Serial.print(_config.fallbackCycleOff);
    Serial.println(F("s OFF"));
}

// ============================================================
// loop -- state machine for fallback transitions
// ============================================================
void LocalFallback::loop(bool mqttConnected, bool hasSensor) {
    unsigned long now = millis();
    unsigned long sinceLast = (now - _lastServerMsg) / 1000;

    if (mqttConnected) {
        if (_level != FB_NORMAL) {
            exitFallback();
        }
        return;
    }

    // Server is NOT connected -- evaluate fallback level
    switch (_level) {
        case FB_NORMAL:
            if (sinceLast >= _config.fallbackTimeout) {
                if (hasSensor) {
                    enterLocal();
                } else {
                    enterFixedCycle();
                }
            }
            break;

        case FB_LOCAL:
            if (sinceLast >= 3600) {
                enterFixedCycle();
            }
            break;

        case FB_FIXED_CYCLE:
            {
                unsigned long cycleElapsed = (now - _cycleStartMs) / 1000;
                if (_cyclePhaseOn) {
                    if (cycleElapsed >= _config.fallbackCycleOn) {
                        _cyclePhaseOn = false;
                        _cycleStartMs = now;
                        Serial.println(F("[FALLBACK] Fixed cycle: OFF phase"));
                    }
                } else {
                    if (cycleElapsed >= _config.fallbackCycleOff) {
                        _cyclePhaseOn = true;
                        _cycleStartMs = now;
                        Serial.println(F("[FALLBACK] Fixed cycle: ON phase"));
                    }
                }
            }
            break;
    }
}

// ============================================================
// State transitions
// ============================================================
void LocalFallback::enterLocal() {
    _level = FB_LOCAL;
    _fallbackEntryMs = millis();

    Serial.println(F(""));
    Serial.println(F("========================================"));
    Serial.println(F("[FALLBACK] ** ENTERING LOCAL MODE **"));
    Serial.println(F("[FALLBACK] Server unreachable -- using local sensor"));
    Serial.println(F("[FALLBACK] Will switch to fixed cycle after 1 hour"));
    Serial.println(F("========================================"));
    Serial.println(F(""));
}

void LocalFallback::enterFixedCycle() {
    _level = FB_FIXED_CYCLE;
    _cycleStartMs = millis();
    _cyclePhaseOn = true;

    Serial.println(F(""));
    Serial.println(F("========================================"));
    Serial.println(F("[FALLBACK] ** ENTERING FIXED CYCLE MODE **"));
    Serial.print(F("[FALLBACK] "));
    Serial.print(_config.fallbackCycleOn);
    Serial.print(F("s ON / "));
    Serial.print(_config.fallbackCycleOff);
    Serial.println(F("s OFF"));
    Serial.println(F("[FALLBACK] Will continue until server reconnects"));
    Serial.println(F("========================================"));
    Serial.println(F(""));
}

void LocalFallback::exitFallback() {
    unsigned long duration = (millis() - _fallbackEntryMs) / 1000;

    Serial.println(F(""));
    Serial.println(F("========================================"));
    Serial.println(F("[FALLBACK] ** SERVER RECONNECTED **"));
    Serial.print(F("[FALLBACK] Was in fallback for "));
    Serial.print(duration);
    Serial.println(F(" seconds"));
    Serial.print(F("[FALLBACK] Previous level: "));
    Serial.println(_level == FB_LOCAL ? F("LOCAL") : F("FIXED_CYCLE"));
    Serial.println(F("[FALLBACK] Resuming normal operation"));
    Serial.println(F("========================================"));
    Serial.println(F(""));

    _level = FB_NORMAL;
    _lastServerMsg = millis();
}

// ============================================================
// Notifications
// ============================================================
void LocalFallback::onServerMessage() {
    _lastServerMsg = millis();
}

// ============================================================
// Accessors
// ============================================================
FallbackLevel LocalFallback::getLevel() const {
    return _level;
}

bool LocalFallback::isInFallback() const {
    return _level != FB_NORMAL;
}

bool LocalFallback::fixedCycleRelayState() const {
    if (_level != FB_FIXED_CYCLE) return false;
    return _cyclePhaseOn;
}

void LocalFallback::updateConfig(const RelayConfig& cfg) {
    _config = cfg;
}
