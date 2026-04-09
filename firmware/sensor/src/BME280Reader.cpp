#include "BME280Reader.h"
#include <Wire.h>
#include <Adafruit_BME280.h>
#include <Adafruit_BMP280.h>

// Global sensor instance'lari
static Adafruit_BME280 bme;
static Adafruit_BMP280 bmp;

// ============================================================
// begin() -- I2C ve BME280/BMP280 baslat
// ESP8266: SDA=GPIO4(D2), SCL=GPIO5(D1)
// Once BME280 dener, bulamazsa BMP280 dener
// ============================================================
bool BME280Reader::begin(uint8_t sda, uint8_t scl) {
    Serial.println(F("[BME] Sensor baslatiyor..."));

    Wire.begin(sda, scl);  // ESP8266: Wire.begin(4, 5) REQUIRED

    // --- BME280 dene (sicaklik + nem + basinc) ---
    if (bme.begin(0x76, &Wire)) {
        Serial.println(F("[BME] BME280 bulundu (0x76)"));
        _connected = true;
        _isBMP280 = false;
    }
    else if (bme.begin(0x77, &Wire)) {
        Serial.println(F("[BME] BME280 bulundu (0x77)"));
        _connected = true;
        _isBMP280 = false;
    }
    // --- BMP280 dene (sicaklik + basinc, nem YOK) ---
    else if (bmp.begin(0x76)) {
        Serial.println(F("[BME] BMP280 bulundu (0x76) -- nem destegi YOK"));
        _connected = true;
        _isBMP280 = true;
    }
    else if (bmp.begin(0x77)) {
        Serial.println(F("[BME] BMP280 bulundu (0x77) -- nem destegi YOK"));
        _connected = true;
        _isBMP280 = true;
    }
    else {
        Serial.println(F("[BME] HATA: BME280/BMP280 bulunamadi! Kablolari kontrol edin."));
        _connected = false;
        return false;
    }

    if (_isBMP280) {
        bmp.setSampling(
            Adafruit_BMP280::MODE_FORCED,
            Adafruit_BMP280::SAMPLING_X2,   // sicaklik oversampling
            Adafruit_BMP280::SAMPLING_X16,  // basinc oversampling
            Adafruit_BMP280::FILTER_X16,    // IIR filtre
            Adafruit_BMP280::STANDBY_MS_500
        );
        Serial.println(F("[BME] BMP280 forced mode ayarlandi (X2/X16, IIR=16)"));
    } else {
        bme.setSampling(
            Adafruit_BME280::MODE_FORCED,
            Adafruit_BME280::SAMPLING_X2,   // sicaklik oversampling
            Adafruit_BME280::SAMPLING_X16,  // basinc oversampling
            Adafruit_BME280::SAMPLING_X1,   // nem oversampling
            Adafruit_BME280::FILTER_X16,    // IIR filtre
            Adafruit_BME280::STANDBY_MS_500
        );
        Serial.println(F("[BME] BME280 forced mode ayarlandi (X2/X16/X1, IIR=16)"));
    }

    _failCount = 0;
    return true;
}

// ============================================================
// read() -- sensor degerlerini oku
// ============================================================
SensorReading BME280Reader::read() {
    SensorReading result = {0.0f, 0.0f, 0.0f, false};

    if (!_connected) {
        Serial.println(F("[BME] Sensor bagli degil, okuma atlaniyor"));
        return result;
    }

    float temp, hum, pres;

    if (_isBMP280) {
        // BMP280: takeForcedMeasurement yok, direkt oku
        if (!bmp.takeForcedMeasurement()) {
            _failCount++;
            Serial.print(F("[BME] BMP280 okuma basarisiz (fail #"));
            Serial.print(_failCount);
            Serial.println(F(")"));
            if (_failCount >= MAX_FAIL_COUNT) {
                Serial.println(F("[BME] KRITIK: Art arda 5 basarisiz, sensor kopmus olabilir"));
                _connected = false;
            }
            return result;
        }
        temp = bmp.readTemperature();
        hum  = 0.0f;  // BMP280 nem desteklemiyor
        pres = bmp.readPressure() / 100.0f;
    } else {
        // BME280: forced mode
        if (!bme.takeForcedMeasurement()) {
            _failCount++;
            Serial.print(F("[BME] takeForcedMeasurement basarisiz (fail #"));
            Serial.print(_failCount);
            Serial.println(F(")"));
            if (_failCount >= MAX_FAIL_COUNT) {
                Serial.println(F("[BME] KRITIK: Art arda 5 basarisiz, sensor kopmus olabilir"));
                _connected = false;
            }
            return result;
        }
        temp = bme.readTemperature();
        hum  = bme.readHumidity();
        pres = bme.readPressure() / 100.0f;
    }

    // NaN kontrolu
    if (isnan(temp) || isnan(hum) || isnan(pres)) {
        _failCount++;
        Serial.println(F("[BME] NaN deger okundu"));
        return result;
    }

    // Mantikli aralik kontrolu
    if (!isReasonable(temp, hum, pres)) {
        _failCount++;
        Serial.print(F("[BME] Mantik disi deger: T="));
        Serial.print(temp, 1);
        Serial.print(F(" H="));
        Serial.print(hum, 1);
        Serial.print(F(" P="));
        Serial.println(pres, 1);
        return result;
    }

    // Kalibrasyon ofseti uygula
    result.temp  = temp + _calOffsetTemp;
    result.hum   = constrain(hum + _calOffsetHum, 0.0f, 100.0f);
    result.pres  = pres;
    result.valid = true;

    _failCount   = 0;
    _lastReadMs  = millis();

    return result;
}

// ============================================================
// setCalibration() -- ofset degerleri
// ============================================================
void BME280Reader::setCalibration(float tempOffset, float humOffset) {
    _calOffsetTemp = tempOffset;
    _calOffsetHum  = humOffset;

    Serial.print(F("[BME] Kalibrasyon: tempOffset="));
    Serial.print(tempOffset, 2);
    Serial.print(F(" humOffset="));
    Serial.println(humOffset, 2);
}

// ============================================================
// isConnected()
// ============================================================
bool BME280Reader::isConnected() const {
    return _connected;
}

// ============================================================
// isBMP280()
// ============================================================
bool BME280Reader::isBMP280() const {
    return _isBMP280;
}

// ============================================================
// lastReadTime()
// ============================================================
unsigned long BME280Reader::lastReadTime() const {
    return _lastReadMs;
}

// ============================================================
// isReasonable() -- degerler fiziksel olarak mantikli mi?
// ============================================================
bool BME280Reader::isReasonable(float temp, float hum, float pres) {
    // Sicaklik: -40..85 C (BME280 calisma araligi)
    if (temp < -40.0f || temp > 85.0f) return false;

    // Nem: 0..100% (BMP280'de hep 0 olur, kabul et)
    if (hum < 0.0f || hum > 100.0f) return false;

    // Basinc: 300..1100 hPa (BME280 calisma araligi)
    if (pres < 300.0f || pres > 1100.0f) return false;

    return true;
}
