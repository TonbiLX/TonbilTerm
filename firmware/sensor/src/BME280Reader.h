#pragma once

#include <Arduino.h>

// ============================================================
// BME280 sensor okuma sonucu
// ============================================================
struct SensorReading {
    float temp;    // Sicaklik (C) -- kalibrasyon ofseti uygulanmis
    float hum;     // Nem (%)      -- kalibrasyon ofseti uygulanmis
    float pres;    // Basinc (hPa)
    bool  valid;   // Okuma basarili mi?
};

// ============================================================
// BME280Reader -- BME280 + BMP280 destekli sensor wrapper
// I2C: SDA=D2/GPIO4, SCL=D1/GPIO5
// BME280: sicaklik + nem + basinc
// BMP280: sicaklik + basinc (nem yok)
// ============================================================
class BME280Reader {
public:
    // I2C baslat, sensor tara (BME280 ve BMP280, 0x76 ve 0x77 dener)
    // sda/scl: I2C pinleri (ESP8266: GPIO4, GPIO5)
    bool begin(uint8_t sda = 4, uint8_t scl = 5);

    // Sensor oku, kalibrasyon ofseti uygula
    SensorReading read();

    // Kalibrasyon ofsetlerini ayarla
    void setCalibration(float tempOffset, float humOffset);

    // Sensor bagli mi?
    bool isConnected() const;

    // BMP280 mi (nem destegi yok)?
    bool isBMP280() const;

    // Son basarili okuma zamani (millis)
    unsigned long lastReadTime() const;

private:
    bool  _connected     = false;
    bool  _isBMP280      = false;  // true ise nem okumasi 0 doner
    float _calOffsetTemp = 0.0f;
    float _calOffsetHum  = 0.0f;
    unsigned long _lastReadMs = 0;

    // Art arda kac okuma basarisiz oldu
    uint8_t _failCount = 0;
    static const uint8_t MAX_FAIL_COUNT = 5;

    // Okunan degerlerin mantikli aralikta olup olmadigini kontrol et
    bool isReasonable(float temp, float hum, float pres);
};
