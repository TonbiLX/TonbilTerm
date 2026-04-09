#include "OTAUpdater.h"
#include <ArduinoOTA.h>

// ============================================================
// begin() -- ArduinoOTA baslat
// ============================================================
void OTAUpdater::begin(const String& deviceId, const char* password, uint8_t ledPin) {
    _ledPin = ledPin;
    pinMode(_ledPin, OUTPUT);
    digitalWrite(_ledPin, HIGH); // LED kapali (active-low on ESP8266)

    Serial.print(F("[OTA] Baslatiyor, hostname: "));
    Serial.println(deviceId);

    ArduinoOTA.setHostname(deviceId.c_str());
    ArduinoOTA.setPassword(password);

    // Port: varsayilan 8266 (ESP8266)

    ArduinoOTA.onStart([this]() {
        _updating = true;
        String type;
        if (ArduinoOTA.getCommand() == U_FLASH) {
            type = "firmware";
        } else {
            type = "filesystem";
        }
        Serial.print(F("[OTA] Guncelleme basladi: "));
        Serial.println(type);
    });

    ArduinoOTA.onEnd([this]() {
        _updating = false;
        digitalWrite(_ledPin, HIGH); // LED kapat
        Serial.println(F("\n[OTA] Guncelleme tamamlandi! Yeniden baslatiliyor..."));
    });

    ArduinoOTA.onProgress([this](unsigned int progress, unsigned int total) {
        unsigned int percent = (progress / (total / 100));
        // Her %10'da bir log bas
        if (percent % 10 == 0) {
            Serial.print(F("[OTA] Ilerleme: "));
            Serial.print(percent);
            Serial.println(F("%"));
        }
        blinkLED();
    });

    ArduinoOTA.onError([this](ota_error_t error) {
        _updating = false;
        digitalWrite(_ledPin, HIGH);

        Serial.print(F("[OTA] HATA #"));
        Serial.print(error);
        Serial.print(F(": "));

        switch (error) {
            case OTA_AUTH_ERROR:
                Serial.println(F("Kimlik dogrulama basarisiz"));
                break;
            case OTA_BEGIN_ERROR:
                Serial.println(F("Baslangic hatasi"));
                break;
            case OTA_CONNECT_ERROR:
                Serial.println(F("Baglanti hatasi"));
                break;
            case OTA_RECEIVE_ERROR:
                Serial.println(F("Alma hatasi"));
                break;
            case OTA_END_ERROR:
                Serial.println(F("Bitis hatasi"));
                break;
            default:
                Serial.println(F("Bilinmeyen hata"));
                break;
        }
    });

    ArduinoOTA.begin();
    Serial.println(F("[OTA] Hazir, guncelleme bekleniyor"));
}

// ============================================================
// handle() -- OTA loop
// ============================================================
void OTAUpdater::handle() {
    ArduinoOTA.handle();
}

// ============================================================
// isUpdating()
// ============================================================
bool OTAUpdater::isUpdating() const {
    return _updating;
}

// ============================================================
// blinkLED() -- guncelleme sirasinda LED blink
// ============================================================
void OTAUpdater::blinkLED() {
    static unsigned long lastBlink = 0;
    static bool ledState = false;

    unsigned long now = millis();
    if (now - lastBlink > 100) {
        ledState = !ledState;
        digitalWrite(_ledPin, ledState ? LOW : HIGH);
        lastBlink = now;
    }
}
