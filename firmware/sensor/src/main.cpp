#include <Arduino.h>
#include <ArduinoJson.h>
#include <ESP8266WiFi.h>

#include "ConfigStore.h"
#include "WiFiProvisioning.h"
#include "BME280Reader.h"
#include "MQTTClient.h"
#include "OTAUpdater.h"
#include "Provisioning.h"

// ============================================================
// Global nesneler
// ============================================================
static ConfigStore       configStore;
static WiFiProvisioning  wifiProv;
static BME280Reader      bmeReader;
static MQTTClient        mqttClient;
static OTAUpdater        otaUpdater;
static Provisioning      provisioning;

// Sensor okuma zamanlayici
static unsigned long lastSensorRead   = 0;
static uint32_t      sensorIntervalMs = 5000;

// Watchdog: sensor uzun sure veri gonderemediyse restart
static unsigned long lastSuccessPublish = 0;
static const unsigned long WATCHDOG_TIMEOUT = 300000; // 5 dakika

// Provisioning retry
static unsigned long lastProvisionAttempt = 0;
static const unsigned long PROVISION_RETRY_INTERVAL = 30000; // 30s

// Forward declaration
void onMQTTAuthFailure();

// ============================================================
// MQTT config callback -- uzaktan ayar degisikligi
// ============================================================
void onMQTTConfig(const char* topic, const char* payload, unsigned int length) {
    Serial.print(F("[SENSOR] Config mesaji alindi: "));
    Serial.println(payload);

    // JSON parse
    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, payload, length);
    if (err) {
        Serial.print(F("[SENSOR] Config parse hatasi: "));
        Serial.println(err.c_str());
        return;
    }

    SensorConfig sensorCfg = configStore.loadSensor();
    bool changed = false;

    // Kalibrasyon ofseti
    if (!doc["calibOffsetTemp"].isNull()) {
        float val = doc["calibOffsetTemp"].as<float>();
        if (val >= -10.0f && val <= 10.0f) {
            sensorCfg.calibOffsetTemp = val;
            changed = true;
            Serial.print(F("[SENSOR] calibOffsetTemp -> "));
            Serial.println(val, 2);
        }
    }

    if (!doc["calibOffsetHum"].isNull()) {
        float val = doc["calibOffsetHum"].as<float>();
        if (val >= -20.0f && val <= 20.0f) {
            sensorCfg.calibOffsetHum = val;
            changed = true;
            Serial.print(F("[SENSOR] calibOffsetHum -> "));
            Serial.println(val, 2);
        }
    }

    // Okuma araligi
    if (!doc["readIntervalMs"].isNull()) {
        uint32_t val = doc["readIntervalMs"].as<uint32_t>();
        if (val >= 1000 && val <= 60000) {
            sensorCfg.readIntervalMs = val;
            sensorIntervalMs = val;
            changed = true;
            Serial.print(F("[SENSOR] readIntervalMs -> "));
            Serial.println(val);
        }
    }

    if (changed) {
        configStore.saveSensor(sensorCfg);
        bmeReader.setCalibration(sensorCfg.calibOffsetTemp, sensorCfg.calibOffsetHum);
        Serial.println(F("[SENSOR] Config guncellendi ve kaydedildi"));
    }
}

// ============================================================
// setup()
// ============================================================
void setup() {
    Serial.begin(115200);
    delay(1000); // Serial baglantisi icin bekle

    Serial.println();
    Serial.println(F("========================================="));
    Serial.println(F("  TermostatOtonom -- ESP8266 Sensor Node"));
    Serial.println(F("  BME280 + MQTT"));
    Serial.println(F("========================================="));

    // --- 1. Config Store (LittleFS) ---
    if (!configStore.begin()) {
        Serial.println(F("[SENSOR] KRITIK: ConfigStore baslatilamadi!"));
    }

    String deviceId = configStore.getDeviceId();
    Serial.print(F("[SENSOR] Device ID: "));
    Serial.println(deviceId);

    // --- 2. BME280 Sensor ---
    // ESP8266: SDA=GPIO4(D2), SCL=GPIO5(D1)
    if (!bmeReader.begin(4, 5)) {
        Serial.println(F("[SENSOR] UYARI: BME280 bulunamadi, sensorsuz devam ediliyor"));
    }

    // Kalibrasyon ofsetini uygula
    SensorConfig sensorCfg = configStore.loadSensor();
    bmeReader.setCalibration(sensorCfg.calibOffsetTemp, sensorCfg.calibOffsetHum);
    sensorIntervalMs = sensorCfg.readIntervalMs;

    // --- 3. WiFi Provisioning ---
    if (!wifiProv.begin(configStore, deviceId)) {
        Serial.println(F("[SENSOR] WiFi baslatma basarisiz"));
        return;
    }

    // --- 4. Provisioning (MQTT credential management) ---
    provisioning.begin(deviceId, "sensor", "1.0.0");

    if (!provisioning.isProvisioned()) {
        MQTTConfig mqttCfg = configStore.loadMQTT();
        const char* apiHost = strlen(mqttCfg.server) > 0
            ? mqttCfg.server
            : "192.168.1.9";

        Serial.println(F("[SENSOR] Attempting API provisioning..."));
        provisioning.provision(apiHost, 8091);
    }

    // --- 5. MQTT Client (with provisioned credentials) ---
    if (provisioning.isProvisioned()) {
        const MQTTCredentials& creds = provisioning.getCredentials();
        mqttClient.setCredentials(creds.username, creds.password);
        mqttClient.onAuthFailure(onMQTTAuthFailure);
        mqttClient.begin(creds.host, creds.port, deviceId);
        mqttClient.onConfigMessage(onMQTTConfig);
    } else {
        MQTTConfig mqttCfg = configStore.loadMQTT();
        const char* srv = strlen(mqttCfg.server) > 0 ? mqttCfg.server : "192.168.1.9";
        uint16_t port = mqttCfg.port > 0 ? mqttCfg.port : 1883;

        Serial.println(F("[SENSOR] UYARI: Provisioning basarisiz, fallback credentials ile baglaniyor"));
        provisioning.applyFallbackCredentials(srv, port);
        const MQTTCredentials& creds = provisioning.getCredentials();
        mqttClient.setCredentials(creds.username, creds.password);
        mqttClient.onAuthFailure(onMQTTAuthFailure);
        mqttClient.begin(creds.host, creds.port, deviceId);
        mqttClient.onConfigMessage(onMQTTConfig);
    }

    // --- 6. OTA Updater ---
    otaUpdater.begin(deviceId);

    // --- 7. Hazir ---
    Serial.println();
    Serial.println(F("========================================="));
    Serial.println(F("  Sistem hazir!"));
    Serial.print(F("  Sensor okuma araligi: "));
    Serial.print(sensorIntervalMs);
    Serial.println(F(" ms"));
    Serial.print(F("  Free heap: "));
    Serial.println(ESP.getFreeHeap());
    Serial.println(F("========================================="));
    Serial.println();

    lastSuccessPublish = millis();
    configStore.printConfig();
}

// ============================================================
// onMQTTAuthFailure() -- MQTT auth basarisiz, re-provision gerek
// ============================================================
void onMQTTAuthFailure() {
    Serial.println(F("[SENSOR] MQTT auth failure, clearing credentials for re-provision"));
    provisioning.onAuthFailure();
}

// ============================================================
// loop()
// ============================================================
void loop() {
    // OTA guncelleme sirasinda baska is yapma
    otaUpdater.handle();
    if (otaUpdater.isUpdating()) {
        return;
    }

    // WiFi baglanti kontrolu
    wifiProv.checkConnection();

    // If provisioning needed, retry periodically
    if (provisioning.needsReprovisioning() && WiFi.status() == WL_CONNECTED) {
        unsigned long now = millis();
        if (now - lastProvisionAttempt >= PROVISION_RETRY_INTERVAL) {
            lastProvisionAttempt = now;

            MQTTConfig mqttCfg = configStore.loadMQTT();
            const char* apiHost = strlen(mqttCfg.server) > 0
                ? mqttCfg.server
                : "192.168.1.9";

            Serial.println(F("[SENSOR] Re-provisioning attempt..."));
            if (provisioning.provision(apiHost, 8091)) {
                const MQTTCredentials& creds = provisioning.getCredentials();
                mqttClient.setCredentials(creds.username, creds.password);
                mqttClient.updateServer(creds.host, creds.port);
                Serial.println(F("[SENSOR] Re-provisioned, MQTT will reconnect"));
            }
        }
    }

    // MQTT loop (reconnect + message handling)
    mqttClient.loop();

    // Sensor okuma zamanlayici
    unsigned long now = millis();
    if (now - lastSensorRead >= sensorIntervalMs) {
        lastSensorRead = now;

        // BME280 oku
        SensorReading reading = bmeReader.read();

        if (reading.valid) {
            // MQTT'ye gonder
            if (mqttClient.publishTelemetry(reading)) {
                lastSuccessPublish = now;
            }
        } else {
            // Sensor bagli degilse yeniden baglanti dene
            if (!bmeReader.isConnected()) {
                Serial.println(F("[SENSOR] BME280 yeniden baglanmaya calisiyor..."));
                bmeReader.begin(4, 5);  // ESP8266: SDA=GPIO4, SCL=GPIO5

                SensorConfig cfg = configStore.loadSensor();
                bmeReader.setCalibration(cfg.calibOffsetTemp, cfg.calibOffsetHum);
            }
        }
    }

    // Watchdog: 5 dakikadir basarili publish yoksa restart
    if (now - lastSuccessPublish > WATCHDOG_TIMEOUT) {
        if (mqttClient.isConnected() && bmeReader.isConnected()) {
            Serial.println(F("[SENSOR] WATCHDOG: 5 dakikadir basarili publish yok, restart!"));
            delay(1000);
            ESP.restart();
        }
    }
}
