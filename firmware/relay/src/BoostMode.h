#pragma once

// ============================================================
// BoostMode.h -- Forced heating for a specified duration
//
// Boost mode overrides auto/manual mode and forces the relay ON
// for a set number of minutes. When the timer expires, the relay
// is turned OFF and the system returns to the previous mode.
//
// Safety: Boost is still subject to max runtime (4h) safety.
//         Maximum boost duration is 120 minutes.
//
// MQTT commands:
//   {"cmd": "boost",       "minutes": 30}  -- activate
//   {"cmd": "boostCancel"}                  -- cancel
//
// Telemetry fields:
//   "boost": true, "boostRemaining": 15, "boostTotal": 30
// ============================================================

#include <Arduino.h>
#include "RelayController.h"

class BoostMode {
public:
    // Activate boost: force relay ON for N minutes (10-120)
    void activate(int minutes, RelayController& relay);

    // Cancel boost: turn relay OFF, return to previous mode
    void cancel(RelayController& relay);

    // Call in loop -- check timer, turn off when expired
    void loop(RelayController& relay);

    // Status queries
    bool isActive() const;
    int  remainingMinutes() const;
    int  totalMinutes() const;
    int  remainingSeconds() const;

private:
    bool          _active         = false;
    unsigned long _startMs        = 0;
    int           _totalMinutes   = 0;
    uint8_t       _prevMode       = 0;  // 0=auto, 1=manual

    static const int MIN_BOOST_MINUTES = 10;
    static const int MAX_BOOST_MINUTES = 120;
};
