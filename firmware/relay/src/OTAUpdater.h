#pragma once

// ============================================================
// OTAUpdater.h -- ArduinoOTA wrapper for ESP8266
//
// Hostname set to device ID (relay-XXXX) for mDNS discovery.
// ============================================================

#include <Arduino.h>

class OTAUpdater {
public:
    void begin(const char* hostname);
    void loop();
    bool isUpdating() const;

private:
    bool _updating = false;
};
