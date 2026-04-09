#pragma once

#include <Arduino.h>

// ============================================================
// OTAUpdater -- ArduinoOTA wrapper (ESP8266)
//
// - Hostname: deviceId (sensor-XXXX)
// - Sifre korumali
// - Guncelleme sirasinda LED blink
// ============================================================

class OTAUpdater {
public:
    // OTA baslat
    // deviceId: hostname olarak kullanilir
    // password: OTA sifresi
    // ledPin: durum LED'i (varsayilan: GPIO2 = onboard LED on ESP8266)
    void begin(const String& deviceId, const char* password = "tonbil2024", uint8_t ledPin = 2);

    // Loop'ta cagir -- OTA isteklerini dinle
    void handle();

    // Guncelleme devam ediyor mu?
    bool isUpdating() const;

private:
    bool    _updating = false;
    uint8_t _ledPin   = 2;

    void blinkLED();
};
