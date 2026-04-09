#include "ConfigStore.h"
#include <LittleFS.h>
#include <ArduinoJson.h>
#include <ESP8266WiFi.h>

const char* ConfigStore::FILE_MQTT   = "/mqtt_cfg.json";
const char* ConfigStore::FILE_SENSOR = "/sensor_cfg.json";
const char* ConfigStore::FILE_DEVICE = "/device.json";

// ============================================================
// begin() -- LittleFS baslat, device ID olustur
// ============================================================
bool ConfigStore::begin() {
    Serial.println(F("[CONFIG] LittleFS baslatiyor..."));

    if (!LittleFS.begin()) {
        Serial.println(F("[CONFIG] LittleFS mount failed, formatting..."));
        LittleFS.format();
        if (!LittleFS.begin()) {
            Serial.println(F("[CONFIG] LittleFS format+mount failed"));
            return false;
        }
    }

    // Device ID'yi yukle veya olustur
    _deviceId = getDeviceId();

    Serial.print(F("[CONFIG] Device ID: "));
    Serial.println(_deviceId);

    return true;
}

// ============================================================
// MQTT Config
// ============================================================
MQTTConfig ConfigStore::loadMQTT() {
    MQTTConfig cfg;

    if (!LittleFS.exists(FILE_MQTT)) {
        Serial.println(F("[CONFIG] MQTT dosyasi yok, default kullaniliyor"));
        return cfg;
    }

    File f = LittleFS.open(FILE_MQTT, "r");
    if (!f) return cfg;

    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, f);
    f.close();

    if (err) {
        Serial.println(F("[CONFIG] MQTT JSON parse hatasi"));
        return cfg;
    }

    if (doc["server"].is<const char*>()) {
        const char* server = doc["server"];
        if (strlen(server) > 0 && strlen(server) < sizeof(cfg.server)) {
            strncpy(cfg.server, server, sizeof(cfg.server) - 1);
            cfg.server[sizeof(cfg.server) - 1] = '\0';
        }
    }
    if (doc["port"].is<uint16_t>()) cfg.port = doc["port"];

    Serial.print(F("[CONFIG] MQTT yuklendi: "));
    Serial.print(cfg.server);
    Serial.print(F(":"));
    Serial.println(cfg.port);

    return cfg;
}

bool ConfigStore::saveMQTT(const MQTTConfig& cfg) {
    File f = LittleFS.open(FILE_MQTT, "w");
    if (!f) {
        Serial.println(F("[CONFIG] MQTT dosya yazma acilamadi"));
        return false;
    }

    JsonDocument doc;
    doc["server"] = cfg.server;
    doc["port"]   = cfg.port;

    serializeJson(doc, f);
    f.close();

    Serial.print(F("[CONFIG] MQTT kaydedildi: "));
    Serial.print(cfg.server);
    Serial.print(F(":"));
    Serial.println(cfg.port);

    return true;
}

// ============================================================
// Sensor Config
// ============================================================
SensorConfig ConfigStore::loadSensor() {
    SensorConfig cfg;

    if (!LittleFS.exists(FILE_SENSOR)) return cfg;

    File f = LittleFS.open(FILE_SENSOR, "r");
    if (!f) return cfg;

    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, f);
    f.close();

    if (err) return cfg;

    if (doc["calTemp"].is<float>())      cfg.calibOffsetTemp = doc["calTemp"];
    if (doc["calHum"].is<float>())       cfg.calibOffsetHum  = doc["calHum"];
    if (doc["interval"].is<uint32_t>())  cfg.readIntervalMs  = doc["interval"];

    Serial.print(F("[CONFIG] Sensor yuklendi: calT="));
    Serial.print(cfg.calibOffsetTemp, 2);
    Serial.print(F(" calH="));
    Serial.print(cfg.calibOffsetHum, 2);
    Serial.print(F(" interval="));
    Serial.println(cfg.readIntervalMs);

    return cfg;
}

bool ConfigStore::saveSensor(const SensorConfig& cfg) {
    File f = LittleFS.open(FILE_SENSOR, "w");
    if (!f) {
        Serial.println(F("[CONFIG] Sensor dosya yazma acilamadi"));
        return false;
    }

    JsonDocument doc;
    doc["calTemp"]  = cfg.calibOffsetTemp;
    doc["calHum"]   = cfg.calibOffsetHum;
    doc["interval"] = cfg.readIntervalMs;

    serializeJson(doc, f);
    f.close();

    Serial.println(F("[CONFIG] Sensor ayarlari kaydedildi"));
    return true;
}

// ============================================================
// Device ID -- ChipId'den uretilir, LittleFS'de saklanir
// ============================================================
String ConfigStore::getDeviceId() {
    if (_deviceId.length() > 0) {
        return _deviceId;
    }

    // Try loading from file
    if (LittleFS.exists(FILE_DEVICE)) {
        File f = LittleFS.open(FILE_DEVICE, "r");
        if (f) {
            JsonDocument doc;
            DeserializationError err = deserializeJson(doc, f);
            f.close();
            if (!err && doc["id"].is<const char*>()) {
                _deviceId = String(doc["id"].as<const char*>());
                if (_deviceId.length() > 0) return _deviceId;
            }
        }
    }

    // Ilk boot -- ChipId'den uret ve kaydet
    _deviceId = generateDeviceId();

    File f = LittleFS.open(FILE_DEVICE, "w");
    if (f) {
        JsonDocument doc;
        doc["id"] = _deviceId;
        serializeJson(doc, f);
        f.close();
        Serial.print(F("[CONFIG] Device ID olusturuldu ve kaydedildi: "));
        Serial.println(_deviceId);
    }

    return _deviceId;
}

String ConfigStore::generateDeviceId() {
    uint32_t chipId = ESP.getChipId();
    char id[16];
    snprintf(id, sizeof(id), "sensor-%04X", (uint16_t)(chipId & 0xFFFF));
    return String(id);
}

// ============================================================
// printConfig() -- debug dump
// ============================================================
void ConfigStore::printConfig() {
    Serial.println(F("--- ConfigStore ---"));

    Serial.print(F("  Device ID : "));
    Serial.println(getDeviceId());

    MQTTConfig mqtt = loadMQTT();
    Serial.print(F("  MQTT      : "));
    Serial.print(mqtt.server);
    Serial.print(F(":"));
    Serial.println(mqtt.port);

    SensorConfig sensor = loadSensor();
    Serial.print(F("  CalibTemp : "));
    Serial.println(sensor.calibOffsetTemp, 2);
    Serial.print(F("  CalibHum  : "));
    Serial.println(sensor.calibOffsetHum, 2);
    Serial.print(F("  Interval  : "));
    Serial.print(sensor.readIntervalMs);
    Serial.println(F(" ms"));

    Serial.println(F("-------------------"));
}
