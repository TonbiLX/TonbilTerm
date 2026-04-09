#include "ConfigStore.h"
#include <LittleFS.h>
#include <ArduinoJson.h>
#include <ESP8266WiFi.h>

const char* ConfigStore::FILE_RELAY  = "/relay_cfg.json";
const char* ConfigStore::FILE_SENSOR = "/sensor_cfg.json";
const char* ConfigStore::FILE_DEVICE = "/device.json";

// ============================================================
// begin -- initialize LittleFS, create defaults if first boot
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

    buildDeviceId();

    // Check if relay config exists
    if (!LittleFS.exists(FILE_RELAY)) {
        Serial.println(F("[CONFIG] First boot -- writing defaults"));
        RelayConfig defaults;
        saveRelay(defaults);
    }

    Serial.println(F("[CONFIG] LittleFS initialized"));
    return true;
}

// ============================================================
// buildDeviceId -- generate "combo-XXXX" from ESP8266 ChipId
// ============================================================
void ConfigStore::buildDeviceId() {
    if (strlen(_deviceId) > 0) return;

    // Try loading from file first
    if (LittleFS.exists(FILE_DEVICE)) {
        File f = LittleFS.open(FILE_DEVICE, "r");
        if (f) {
            JsonDocument doc;
            DeserializationError err = deserializeJson(doc, f);
            f.close();
            if (!err && doc["id"].is<const char*>()) {
                const char* id = doc["id"];
                if (strlen(id) > 0) {
                    strncpy(_deviceId, id, sizeof(_deviceId) - 1);
                    return;
                }
            }
        }
    }

    // Generate from ChipId
    uint32_t chipId = ESP.getChipId();
    snprintf(_deviceId, sizeof(_deviceId), "combo-%04X", (uint16_t)(chipId & 0xFFFF));

    // Save to file
    File f = LittleFS.open(FILE_DEVICE, "w");
    if (f) {
        JsonDocument doc;
        doc["id"] = _deviceId;
        serializeJson(doc, f);
        f.close();
        Serial.print(F("[CONFIG] Device ID olusturuldu: "));
        Serial.println(_deviceId);
    }
}

// ============================================================
// getDeviceId
// ============================================================
const char* ConfigStore::getDeviceId() {
    if (strlen(_deviceId) == 0) {
        buildDeviceId();
    }
    return _deviceId;
}

// ============================================================
// loadRelay -- read relay config from LittleFS JSON
// ============================================================
RelayConfig ConfigStore::loadRelay() {
    RelayConfig cfg;

    if (!LittleFS.exists(FILE_RELAY)) {
        Serial.println(F("[CONFIG] load: No relay config file -- returning defaults"));
        return cfg;
    }

    File f = LittleFS.open(FILE_RELAY, "r");
    if (!f) {
        Serial.println(F("[CONFIG] load: File open failed -- returning defaults"));
        return cfg;
    }

    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, f);
    f.close();

    if (err) {
        Serial.print(F("[CONFIG] load: JSON parse error: "));
        Serial.println(err.c_str());
        return cfg;
    }

    // MQTT
    if (doc["mqttServer"].is<const char*>()) {
        strncpy(cfg.mqttServer, doc["mqttServer"].as<const char*>(), sizeof(cfg.mqttServer) - 1);
        cfg.mqttServer[sizeof(cfg.mqttServer) - 1] = '\0';
    }
    if (doc["mqttPort"].is<uint16_t>()) cfg.mqttPort = doc["mqttPort"];

    // Thermostat
    if (doc["targetTemp"].is<float>())  cfg.targetTemp  = doc["targetTemp"];
    if (doc["hysteresis"].is<float>())  cfg.hysteresis  = doc["hysteresis"];

    // Timing
    if (doc["minCycleTime"].is<uint32_t>())       cfg.minCycleTime       = doc["minCycleTime"];
    if (doc["maxRuntime"].is<uint32_t>())          cfg.maxRuntime         = doc["maxRuntime"];
    if (doc["maxRuntimeCooldown"].is<uint32_t>())  cfg.maxRuntimeCooldown = doc["maxRuntimeCooldown"];

    // Safety
    if (doc["freezeThreshold"].is<float>()) cfg.freezeThreshold = doc["freezeThreshold"];

    // Fallback
    if (doc["fallbackTimeout"].is<uint32_t>())  cfg.fallbackTimeout  = doc["fallbackTimeout"];
    if (doc["fallbackCycleOn"].is<uint32_t>())  cfg.fallbackCycleOn  = doc["fallbackCycleOn"];
    if (doc["fallbackCycleOff"].is<uint32_t>()) cfg.fallbackCycleOff = doc["fallbackCycleOff"];

    // Mode
    if (doc["mode"].is<uint8_t>()) cfg.mode = doc["mode"];

    // Validation -- clamp to safe ranges
    if (cfg.targetTemp < 5.0f || cfg.targetTemp > 35.0f) cfg.targetTemp = 22.0f;
    if (cfg.hysteresis < 0.1f || cfg.hysteresis > 3.0f)  cfg.hysteresis = 0.3f;
    if (cfg.minCycleTime < 60 || cfg.minCycleTime > 600) cfg.minCycleTime = 180;
    if (cfg.maxRuntime < 3600 || cfg.maxRuntime > 28800)  cfg.maxRuntime = 14400;
    if (cfg.freezeThreshold < 0.0f || cfg.freezeThreshold > 10.0f) cfg.freezeThreshold = 5.0f;
    if (cfg.mode > 1) cfg.mode = 0;

    return cfg;
}

// ============================================================
// saveRelay -- write all relay fields to LittleFS JSON
// ============================================================
bool ConfigStore::saveRelay(const RelayConfig& cfg) {
    File f = LittleFS.open(FILE_RELAY, "w");
    if (!f) {
        Serial.println(F("[CONFIG] save: File open failed"));
        return false;
    }

    JsonDocument doc;
    doc["mqttServer"]         = cfg.mqttServer;
    doc["mqttPort"]           = cfg.mqttPort;
    doc["targetTemp"]         = cfg.targetTemp;
    doc["hysteresis"]         = cfg.hysteresis;
    doc["minCycleTime"]       = cfg.minCycleTime;
    doc["maxRuntime"]         = cfg.maxRuntime;
    doc["maxRuntimeCooldown"] = cfg.maxRuntimeCooldown;
    doc["freezeThreshold"]    = cfg.freezeThreshold;
    doc["fallbackTimeout"]    = cfg.fallbackTimeout;
    doc["fallbackCycleOn"]    = cfg.fallbackCycleOn;
    doc["fallbackCycleOff"]   = cfg.fallbackCycleOff;
    doc["mode"]               = cfg.mode;

    serializeJson(doc, f);
    f.close();

    Serial.println(F("[CONFIG] Relay config saved"));
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

    if (doc["calibOffsetTemp"].is<float>())    cfg.calibOffsetTemp = doc["calibOffsetTemp"];
    if (doc["calibOffsetHum"].is<float>())     cfg.calibOffsetHum  = doc["calibOffsetHum"];
    if (doc["readIntervalMs"].is<uint32_t>())  cfg.readIntervalMs  = doc["readIntervalMs"];

    return cfg;
}

bool ConfigStore::saveSensor(const SensorConfig& cfg) {
    File f = LittleFS.open(FILE_SENSOR, "w");
    if (!f) {
        Serial.println(F("[CONFIG] Sensor file yazma acilamadi"));
        return false;
    }

    JsonDocument doc;
    doc["calibOffsetTemp"] = cfg.calibOffsetTemp;
    doc["calibOffsetHum"]  = cfg.calibOffsetHum;
    doc["readIntervalMs"]  = cfg.readIntervalMs;

    serializeJson(doc, f);
    f.close();

    Serial.println(F("[CONFIG] Sensor ayarlari kaydedildi"));
    return true;
}

// ============================================================
// Individual field updates -- load, modify, save
// ============================================================
void ConfigStore::setTargetTemp(float t) {
    if (t < 5.0f || t > 35.0f) return;
    RelayConfig cfg = loadRelay();
    cfg.targetTemp = t;
    saveRelay(cfg);
    Serial.print(F("[CONFIG] Target temp updated: "));
    Serial.println(t, 1);
}

void ConfigStore::setMode(uint8_t m) {
    if (m > 1) return;
    RelayConfig cfg = loadRelay();
    cfg.mode = m;
    saveRelay(cfg);
    Serial.print(F("[CONFIG] Mode updated: "));
    Serial.println(m == 0 ? "auto" : "manual");
}

void ConfigStore::setMqttServer(const char* server, uint16_t port) {
    RelayConfig cfg = loadRelay();
    strncpy(cfg.mqttServer, server, sizeof(cfg.mqttServer) - 1);
    cfg.mqttServer[sizeof(cfg.mqttServer) - 1] = '\0';
    cfg.mqttPort = port;
    saveRelay(cfg);
    Serial.print(F("[CONFIG] MQTT server updated: "));
    Serial.print(server);
    Serial.print(F(":"));
    Serial.println(port);
}

// ============================================================
// printConfig -- dump config to Serial
// ============================================================
void ConfigStore::printConfig() {
    Serial.println(F("--- Combo Config ---"));
    Serial.print(F("  Device ID    : "));
    Serial.println(_deviceId);

    RelayConfig r = loadRelay();
    Serial.print(F("  MQTT Server  : ")); Serial.print(r.mqttServer);
    Serial.print(F(":")); Serial.println(r.mqttPort);
    Serial.print(F("  Target Temp  : ")); Serial.print(r.targetTemp, 1); Serial.println(F(" C"));
    Serial.print(F("  Hysteresis   : ")); Serial.print(r.hysteresis, 2); Serial.println(F(" C"));
    Serial.print(F("  Min Cycle    : ")); Serial.print(r.minCycleTime); Serial.println(F(" s"));
    Serial.print(F("  Max Runtime  : ")); Serial.print(r.maxRuntime); Serial.println(F(" s"));
    Serial.print(F("  Freeze Thresh: ")); Serial.print(r.freezeThreshold, 1); Serial.println(F(" C"));
    Serial.print(F("  Mode         : ")); Serial.println(r.mode == 0 ? F("AUTO") : F("MANUAL"));
    Serial.print(F("  FB Timeout   : ")); Serial.print(r.fallbackTimeout); Serial.println(F(" s"));

    SensorConfig s = loadSensor();
    Serial.print(F("  CalibTemp    : ")); Serial.println(s.calibOffsetTemp, 2);
    Serial.print(F("  CalibHum     : ")); Serial.println(s.calibOffsetHum, 2);
    Serial.print(F("  ReadInterval : ")); Serial.print(s.readIntervalMs); Serial.println(F(" ms"));

    Serial.println(F("--------------------"));
}
