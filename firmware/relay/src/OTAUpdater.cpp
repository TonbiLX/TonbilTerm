#include "OTAUpdater.h"
#include <ArduinoOTA.h>

// ============================================================
// begin — setup ArduinoOTA with callbacks
// ============================================================
void OTAUpdater::begin(const char* hostname) {
    ArduinoOTA.setHostname(hostname);

    ArduinoOTA.onStart([this]() {
        _updating = true;
        String type = (ArduinoOTA.getCommand() == U_FLASH) ? "firmware" : "filesystem";
        Serial.print(F("[OTA] Update starting: "));
        Serial.println(type);
    });

    ArduinoOTA.onEnd([this]() {
        _updating = false;
        Serial.println(F("\n[OTA] Update complete — rebooting"));
    });

    ArduinoOTA.onProgress([](unsigned int progress, unsigned int total) {
        static uint8_t lastPercent = 255;
        uint8_t percent = (progress / (total / 100));
        if (percent != lastPercent && percent % 10 == 0) {
            lastPercent = percent;
            Serial.print(F("[OTA] Progress: "));
            Serial.print(percent);
            Serial.println(F("%"));
        }
    });

    ArduinoOTA.onError([this](ota_error_t error) {
        _updating = false;
        Serial.print(F("[OTA] Error["));
        Serial.print(error);
        Serial.print(F("]: "));
        switch (error) {
            case OTA_AUTH_ERROR:    Serial.println(F("Auth Failed")); break;
            case OTA_BEGIN_ERROR:   Serial.println(F("Begin Failed")); break;
            case OTA_CONNECT_ERROR: Serial.println(F("Connect Failed")); break;
            case OTA_RECEIVE_ERROR: Serial.println(F("Receive Failed")); break;
            case OTA_END_ERROR:     Serial.println(F("End Failed")); break;
            default:               Serial.println(F("Unknown")); break;
        }
    });

    ArduinoOTA.begin();

    Serial.print(F("[OTA] Ready — hostname: "));
    Serial.print(hostname);
    Serial.println(F(".local"));
}

// ============================================================
// loop — handle OTA requests
// ============================================================
void OTAUpdater::loop() {
    ArduinoOTA.handle();
}

// ============================================================
// isUpdating — check if OTA is in progress
// ============================================================
bool OTAUpdater::isUpdating() const {
    return _updating;
}
