#include "RelayController.h"

// ============================================================
// begin -- boot-safe relay initialization
// CRITICAL: digitalWrite BEFORE pinMode to prevent glitch
// ============================================================
void RelayController::begin(const RelayConfig& cfg) {
    _config   = cfg;
    _mode     = (RelayMode)cfg.mode;
    _bootTime = millis();

    // BOOT-GLITCH PREVENTION:
    // On ESP8266 boot, GPIO pins briefly float or go LOW.
    // For an active-LOW relay, this would momentarily energize the relay.
    // Writing HIGH (OFF) BEFORE setting as OUTPUT prevents this.
    digitalWrite(RELAY_PIN, RELAY_OFF_STATE);
    pinMode(RELAY_PIN, OUTPUT);
    digitalWrite(RELAY_PIN, RELAY_OFF_STATE);  // Double-ensure OFF

    _relayOn = false;
    _relayOffSince = millis();

    Serial.println(F("[RELAY] Initialized -- relay OFF"));
    Serial.print(F("[RELAY] Pin: GPIO"));
    Serial.print(RELAY_PIN);
    Serial.print(F(" | Active-LOW | Target: "));
    Serial.print(_config.targetTemp, 1);
    Serial.print(F("C | Hyst: "));
    Serial.print(_config.hysteresis, 2);
    Serial.print(F("C | MinCycle: "));
    Serial.print(_config.minCycleTime);
    Serial.print(F("s | MaxRun: "));
    Serial.print(_config.maxRuntime);
    Serial.println(F("s"));
}

// ============================================================
// evaluate -- hysteresis control (AUTO mode only)
// ============================================================
void RelayController::evaluate(float currentTemp) {
    if (isnan(currentTemp)) return;

    _lastTemp = currentTemp;

    // Freeze protection overrides everything
    checkFreezeProtection(currentTemp);
    if (_freezeActive) return;

    // Max runtime cooldown overrides auto control
    if (_maxRuntimeCool) return;

    // Only auto mode does hysteresis evaluation
    if (_mode != RELAY_MODE_AUTO) return;

    bool shouldBeOn = _relayOn;

    if (currentTemp < (_config.targetTemp - _config.hysteresis)) {
        shouldBeOn = true;
    } else if (currentTemp > (_config.targetTemp + _config.hysteresis)) {
        shouldBeOn = false;
    }

    if (shouldBeOn != _relayOn) {
        setRelay(shouldBeOn, "AUTO_HYST");
    }
}

// ============================================================
// setRelay -- internal relay control with min cycle enforcement
// ============================================================
bool RelayController::setRelay(bool on, const char* reason) {
    if (on == _relayOn) return false;

    // Min cycle time enforcement (skip for freeze protection)
    if (isCycleLocked() && !_freezeActive) {
        unsigned long elapsed;
        if (_relayOn) {
            elapsed = (millis() - _relayOnSince) / 1000;
        } else {
            elapsed = (millis() - _relayOffSince) / 1000;
        }
        Serial.print(F("[RELAY] Cycle locked -- "));
        Serial.print(elapsed);
        Serial.print(F("/"));
        Serial.print(_config.minCycleTime);
        Serial.println(F("s elapsed"));
        return false;
    }

    _relayOn = on;
    digitalWrite(RELAY_PIN, on ? RELAY_ON_STATE : RELAY_OFF_STATE);

    if (on) {
        _relayOnSince = millis();
    } else {
        _relayOffSince = millis();
    }

    logTransition(on, reason);
    return true;
}

// ============================================================
// forceOn / forceOff -- bypass mode check (for boost mode)
// Still respects max runtime safety
// ============================================================
void RelayController::forceOn(const char* reason) {
    if (_maxRuntimeCool) {
        Serial.println(F("[RELAY] forceOn blocked -- max runtime cooldown active"));
        return;
    }
    if (!_relayOn) {
        _relayOn = true;
        digitalWrite(RELAY_PIN, RELAY_ON_STATE);
        _relayOnSince = millis();
        logTransition(true, reason);
    }
}

void RelayController::forceOff(const char* reason) {
    if (_relayOn) {
        _relayOn = false;
        digitalWrite(RELAY_PIN, RELAY_OFF_STATE);
        _relayOffSince = millis();
        logTransition(false, reason);
    }
}

// ============================================================
// loop -- call every iteration, handles safety timers
// ============================================================
void RelayController::loop() {
    checkMaxRuntime();
}

// ============================================================
// checkMaxRuntime -- force OFF after continuous max runtime
// ============================================================
void RelayController::checkMaxRuntime() {
    if (_maxRuntimeCool) {
        unsigned long coolElapsed = (millis() - _maxRuntimeCoolStart) / 1000;
        if (coolElapsed >= _config.maxRuntimeCooldown) {
            _maxRuntimeCool = false;
            Serial.println(F("[RELAY] Max runtime cooldown complete -- resuming normal operation"));
        }
        return;
    }

    if (!_relayOn) return;

    unsigned long runSeconds = (millis() - _relayOnSince) / 1000;
    if (runSeconds >= _config.maxRuntime) {
        Serial.print(F("[RELAY] SAFETY: Max runtime reached ("));
        Serial.print(runSeconds);
        Serial.print(F("s) -- forcing OFF for "));
        Serial.print(_config.maxRuntimeCooldown);
        Serial.println(F("s cooldown"));

        _relayOn = false;
        digitalWrite(RELAY_PIN, RELAY_OFF_STATE);
        _relayOffSince = millis();
        _maxRuntimeCool = true;
        _maxRuntimeCoolStart = millis();

        logTransition(false, "MAX_RUNTIME_SAFETY");
    }
}

// ============================================================
// checkFreezeProtection -- force ON below freeze threshold
// ============================================================
void RelayController::checkFreezeProtection(float temp) {
    if (temp < _config.freezeThreshold) {
        if (!_freezeActive) {
            _freezeActive = true;
            Serial.print(F("[RELAY] FREEZE PROTECTION ACTIVE -- temp "));
            Serial.print(temp, 1);
            Serial.print(F("C < "));
            Serial.print(_config.freezeThreshold, 1);
            Serial.println(F("C threshold"));
        }
        if (!_relayOn) {
            _relayOn = true;
            digitalWrite(RELAY_PIN, RELAY_ON_STATE);
            _relayOnSince = millis();
            logTransition(true, "FREEZE_PROTECT");
        }
    } else if (_freezeActive && temp > (_config.freezeThreshold + 2.0f)) {
        _freezeActive = false;
        Serial.print(F("[RELAY] Freeze protection deactivated -- temp "));
        Serial.print(temp, 1);
        Serial.println(F("C"));
    }
}

// ============================================================
// isCycleLocked -- check if min cycle time has not elapsed
// ============================================================
bool RelayController::isCycleLocked() const {
    if (_config.minCycleTime <= 0) return false;  // 0 = kilit devre disi
    unsigned long now = millis();
    if (_relayOn) {
        return ((now - _relayOnSince) / 1000) < (unsigned long)_config.minCycleTime;
    } else {
        return ((now - _relayOffSince) / 1000) < (unsigned long)_config.minCycleTime;
    }
}

// ============================================================
// Mode control
// ============================================================
void RelayController::setMode(RelayMode m) {
    _mode = m;
    Serial.print(F("[RELAY] Mode set to: "));
    Serial.println(m == RELAY_MODE_AUTO ? F("AUTO") : F("MANUAL"));
}

void RelayController::setManual(bool on) {
    _mode = RELAY_MODE_MANUAL;
    // Manuel komutlar cycle lock'u bypass eder
    if (on) {
        forceOn("MANUAL_CMD");
    } else {
        forceOff("MANUAL_CMD");
    }
}

void RelayController::setTarget(float target) {
    if (target < 5.0f || target > 35.0f) {
        Serial.print(F("[RELAY] Target rejected (out of range): "));
        Serial.println(target, 1);
        return;
    }
    _config.targetTemp = target;
    Serial.print(F("[RELAY] Target updated: "));
    Serial.print(target, 1);
    Serial.println(F("C"));
}

void RelayController::setHysteresis(float hyst) {
    if (hyst < 0.1f || hyst > 3.0f) return;
    _config.hysteresis = hyst;
    Serial.print(F("[RELAY] Hysteresis updated: "));
    Serial.print(hyst, 2);
    Serial.println(F("C"));
}

void RelayController::updateConfig(const RelayConfig& cfg) {
    _config = cfg;
    _mode = (RelayMode)cfg.mode;
    Serial.println(F("[RELAY] Config updated from server"));
}

// ============================================================
// getStatus -- snapshot of current state
// ============================================================
RelayStatus RelayController::getStatus() const {
    RelayStatus s;
    s.relayOn       = _relayOn;
    s.mode          = _mode;
    s.targetTemp    = _config.targetTemp;
    s.lastTemp      = _lastTemp;
    s.freezeProtect = _freezeActive;
    s.maxRuntimeHit = _maxRuntimeCool;
    s.cycleLocked   = isCycleLocked();
    s.relayOnSince  = _relayOn ? (uint32_t)((millis() - _relayOnSince) / 1000) : 0;
    s.relayOffSince = !_relayOn ? (uint32_t)((millis() - _relayOffSince) / 1000) : 0;
    s.uptime        = (uint32_t)(millis() / 1000);
    return s;
}

// ============================================================
// logTransition -- structured relay state change log
// ============================================================
void RelayController::logTransition(bool on, const char* reason) const {
    Serial.print(F("[RELAY] "));
    Serial.print(on ? F("ON") : F("OFF"));
    Serial.print(F(" | reason="));
    Serial.print(reason);
    Serial.print(F(" | mode="));
    Serial.print(_mode == RELAY_MODE_AUTO ? F("AUTO") : F("MANUAL"));
    Serial.print(F(" | target="));
    Serial.print(_config.targetTemp, 1);
    if (!isnan(_lastTemp)) {
        Serial.print(F(" | temp="));
        Serial.print(_lastTemp, 1);
    }
    Serial.println();
}
