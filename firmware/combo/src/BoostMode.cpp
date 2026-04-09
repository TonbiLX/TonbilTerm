#include "BoostMode.h"

// ============================================================
// activate -- start boost mode, force relay ON
// ============================================================
void BoostMode::activate(int minutes, RelayController& relay) {
    // Clamp to safe range
    if (minutes < MIN_BOOST_MINUTES) minutes = MIN_BOOST_MINUTES;
    if (minutes > MAX_BOOST_MINUTES) minutes = MAX_BOOST_MINUTES;

    // Save previous mode for restoration
    RelayStatus status = relay.getStatus();
    _prevMode = (uint8_t)status.mode;

    _active       = true;
    _startMs      = millis();
    _totalMinutes = minutes;

    // Force relay ON regardless of mode/temperature
    relay.forceOn("BOOST_MODE");

    Serial.println(F(""));
    Serial.println(F("========================================"));
    Serial.println(F("[BOOST] ** BOOST MODE ACTIVATED **"));
    Serial.print(F("[BOOST] Duration: "));
    Serial.print(minutes);
    Serial.println(F(" minutes"));
    Serial.print(F("[BOOST] Previous mode: "));
    Serial.println(_prevMode == 0 ? F("AUTO") : F("MANUAL"));
    Serial.println(F("[BOOST] Relay FORCED ON"));
    Serial.println(F("========================================"));
    Serial.println(F(""));
}

// ============================================================
// cancel -- stop boost mode, turn relay OFF
// ============================================================
void BoostMode::cancel(RelayController& relay) {
    if (!_active) return;

    _active = false;

    unsigned long elapsed = (millis() - _startMs) / 1000;

    // Turn relay OFF
    relay.forceOff("BOOST_CANCEL");

    // Restore previous mode
    relay.setMode((RelayMode)_prevMode);

    Serial.println(F(""));
    Serial.println(F("========================================"));
    Serial.println(F("[BOOST] ** BOOST MODE CANCELLED **"));
    Serial.print(F("[BOOST] Ran for: "));
    Serial.print(elapsed / 60);
    Serial.print(F(" min "));
    Serial.print(elapsed % 60);
    Serial.println(F(" sec"));
    Serial.print(F("[BOOST] Restored mode: "));
    Serial.println(_prevMode == 0 ? F("AUTO") : F("MANUAL"));
    Serial.println(F("========================================"));
    Serial.println(F(""));
}

// ============================================================
// loop -- check timer, turn off when expired
// ============================================================
void BoostMode::loop(RelayController& relay) {
    if (!_active) return;

    unsigned long elapsedMs = millis() - _startMs;
    unsigned long durationMs = (unsigned long)_totalMinutes * 60UL * 1000UL;

    if (elapsedMs >= durationMs) {
        // Timer expired
        Serial.println(F("[BOOST] Timer expired"));
        cancel(relay);
        return;
    }

    // Ensure relay stays ON during boost (in case something turned it off)
    RelayStatus status = relay.getStatus();
    if (!status.relayOn && !status.maxRuntimeHit) {
        relay.forceOn("BOOST_KEEPALIVE");
    }

    // Log remaining time every 60 seconds
    static unsigned long lastLogMs = 0;
    if (millis() - lastLogMs >= 60000) {
        lastLogMs = millis();
        int remaining = remainingMinutes();
        Serial.print(F("[BOOST] Remaining: "));
        Serial.print(remaining);
        Serial.print(F("/"));
        Serial.print(_totalMinutes);
        Serial.println(F(" min"));
    }
}

// ============================================================
// Status queries
// ============================================================
bool BoostMode::isActive() const {
    return _active;
}

int BoostMode::remainingMinutes() const {
    if (!_active) return 0;

    unsigned long elapsedMs = millis() - _startMs;
    unsigned long durationMs = (unsigned long)_totalMinutes * 60UL * 1000UL;

    if (elapsedMs >= durationMs) return 0;

    return (int)((durationMs - elapsedMs) / 60000UL);
}

int BoostMode::totalMinutes() const {
    if (!_active) return 0;
    return _totalMinutes;
}

int BoostMode::remainingSeconds() const {
    if (!_active) return 0;

    unsigned long elapsedMs = millis() - _startMs;
    unsigned long durationMs = (unsigned long)_totalMinutes * 60UL * 1000UL;

    if (elapsedMs >= durationMs) return 0;

    return (int)((durationMs - elapsedMs) / 1000UL);
}
