#pragma once

// ============================================================
// BME280Reader.h -- BME280 sensor for combo device (ESP8266)
//
// I2C: SDA=D2/GPIO4, SCL=D1/GPIO5 (Wire.begin(4, 5))
// Provides BME280Data for MQTT telemetry and relay controller
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
    // Initialize sensor -- returns true if detected on I2C bus
    bool begin(uint8_t addr = 0x76);

    // Read sensor data -- returns invalid data if sensor not present
    BME280Data read();

    // Check if sensor was detected at boot
    bool isAvailable() const;

    // Set calibration offsets
    void setCalibration(float tempOffset, float humOffset);

    // Get last successful reading timestamp
    unsigned long lastReadTime() const;

private:
    Adafruit_BME280 _bme;
    bool            _available     = false;
    float           _calOffsetTemp = 0.0f;
    float           _calOffsetHum  = 0.0f;
    unsigned long   _lastReadMs    = 0;
    uint8_t         _consecutiveFails = 0;
    static const uint8_t MAX_FAILS = 5;
};
