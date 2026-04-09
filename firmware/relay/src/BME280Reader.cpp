#include "BME280Reader.h"
#include <Wire.h>

// ============================================================
// begin -- probe I2C for BME280, configure if found
// ESP8266: SDA=GPIO4(D2), SCL=GPIO5(D1)
// ============================================================
bool BME280Reader::begin(uint8_t addr) {
    Wire.begin(4, 5);  // SDA=D2/GPIO4, SCL=D1/GPIO5 on ESP8266

    if (_bme.begin(addr, &Wire)) {
        _available = true;

        _bme.setSampling(
            Adafruit_BME280::MODE_NORMAL,
            Adafruit_BME280::SAMPLING_X2,
            Adafruit_BME280::SAMPLING_X16,
            Adafruit_BME280::SAMPLING_X1,
            Adafruit_BME280::FILTER_X16,
            Adafruit_BME280::STANDBY_MS_500
        );

        Serial.print(F("[BME] Sensor found at 0x"));
        Serial.println(addr, HEX);
        return true;
    }

    // Try alternate address
    if (addr == 0x76 && _bme.begin(0x77, &Wire)) {
        _available = true;
        _bme.setSampling(
            Adafruit_BME280::MODE_NORMAL,
            Adafruit_BME280::SAMPLING_X2,
            Adafruit_BME280::SAMPLING_X16,
            Adafruit_BME280::SAMPLING_X1,
            Adafruit_BME280::FILTER_X16,
            Adafruit_BME280::STANDBY_MS_500
        );
        Serial.println(F("[BME] Sensor found at 0x77"));
        return true;
    }

    _available = false;
    Serial.println(F("[BME] No sensor detected -- local readings disabled"));
    return false;
}

// ============================================================
// read -- get current sensor values
// ============================================================
BME280Data BME280Reader::read() {
    BME280Data data;

    if (!_available) {
        return data;
    }

    float t = _bme.readTemperature();
    float h = _bme.readHumidity();
    float p = _bme.readPressure() / 100.0f;

    if (isnan(t) || isnan(h) || isnan(p) ||
        t < -40.0f || t > 85.0f ||
        h < 0.0f   || h > 100.0f ||
        p < 300.0f || p > 1100.0f) {

        _consecutiveFails++;
        if (_consecutiveFails >= MAX_FAILS) {
            Serial.println(F("[BME] Too many consecutive failures -- marking unavailable"));
            _available = false;
        } else {
            Serial.print(F("[BME] Invalid reading (fail "));
            Serial.print(_consecutiveFails);
            Serial.print(F("/"));
            Serial.print(MAX_FAILS);
            Serial.println(F(")"));
        }
        return data;
    }

    _consecutiveFails = 0;
    _lastReadMs = millis();

    data.temperature = t;
    data.humidity    = h;
    data.pressure    = p;
    data.valid       = true;

    return data;
}

// ============================================================
// Accessors
// ============================================================
bool BME280Reader::isAvailable() const {
    return _available;
}

unsigned long BME280Reader::lastReadTime() const {
    return _lastReadMs;
}
