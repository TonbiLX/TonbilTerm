#pragma once

// ============================================================
// BME280Reader.h -- Optional local BME280 sensor (ESP8266)
//
// I2C: SDA=D2/GPIO4, SCL=D1/GPIO5 (Wire.begin(4, 5))
// ============================================================

#include <Arduino.h>
#include <Adafruit_BME280.h>

struct BME280Data {
    float temperature = NAN;
    float humidity    = NAN;
    float pressure    = NAN;
    bool  valid       = false;
};

class BME280Reader {
public:
    bool begin(uint8_t addr = 0x76);
    BME280Data read();
    bool isAvailable() const;
    unsigned long lastReadTime() const;

private:
    Adafruit_BME280 _bme;
    bool            _available     = false;
    unsigned long   _lastReadMs    = 0;
    uint8_t         _consecutiveFails = 0;
    static const uint8_t MAX_FAILS = 5;
};
