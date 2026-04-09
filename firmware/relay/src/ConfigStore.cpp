#include "ConfigStore.h"
#include <LittleFS.h>
#include <ArduinoJson.h>

const char* ConfigStore::CONFIG_FILE = "/relay_cfg.json";

// ============================================================
// begin -- initialize LittleFS, create defaults if first boot
// ============================================================
bool ConfigStore::begin() {
    if (!LittleFS.begin()) {
        Serial.println(F("[CONFIG] LittleFS mount failed, formatting..."));
        LittleFS.format();
        if (!LittleFS.begin()) {
            Serial.println(F("[CONFIG] LittleFS format+mount failed"));
            return false;
        }
    }

    if (!LittleFS.exists(CONFIG_FILE)) {
        Serial.println(F("[CONFIG] First boot -- writing defaults"));
        RelayConfig defaults;
        save(defaults);
    }

    Serial.println(F("[CONFIG] LittleFS initialized"));
    return true;
}

// ============================================================
// load -- read all fields from LittleFS JSON
// ============================================================
RelayConfig ConfigStore::load() {
    RelayConfig cfg;

    if (!LittleFS.exists(CONFIG_FILE)) {
        Serial.println(F("[CONFIG] load: No config file -- returning defaults"));
        return cfg;
    }

    File f = LittleFS.open(CONFIG_FILE, "r");
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
// save -- write all fields to LittleFS JSON
// ============================================================
bool ConfigStore::save(const RelayConfig& cfg) {
    File f = LittleFS.open(CONFIG_FILE, "w");
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

    Serial.println(F("[CONFIG] Saved to LittleFS"));
    return true;
}

// ============================================================
// Individual field updates -- load, modify, save
// ============================================================
void ConfigStore::setTargetTemp(float t) {
    if (t < 5.0f || t > 35.0f) return;
    RelayConfig cfg = load();
    cfg.targetTemp = t;
    save(cfg);
    Serial.print(F("[CONFIG] Target temp updated: "));
    Serial.println(t, 1);
}

void ConfigStore::setMode(uint8_t m) {
    if (m > 1) return;
    RelayConfig cfg = load();
    cfg.mode = m;
    save(cfg);
    Serial.print(F("[CONFIG] Mode updated: "));
    Serial.println(m == 0 ? "auto" : "manual");
}

void ConfigStore::setMqttServer(const char* server, uint16_t port) {
    RelayConfig cfg = load();
    strncpy(cfg.mqttServer, server, sizeof(cfg.mqttServer) - 1);
    cfg.mqttServer[sizeof(cfg.mqttServer) - 1] = '\0';
    cfg.mqttPort = port;
    save(cfg);
    Serial.print(F("[CONFIG] MQTT server updated: "));
    Serial.print(server);
    Serial.print(F(":"));
    Serial.println(port);
}

// ============================================================
// print -- dump config to Serial
// ============================================================
void ConfigStore::print(const RelayConfig& cfg) const {
    Serial.println(F("--- Relay Config ---"));
    Serial.print(F("  MQTT Server  : ")); Serial.print(cfg.mqttServer);
    Serial.print(F(":")); Serial.println(cfg.mqttPort);
    Serial.print(F("  Target Temp  : ")); Serial.print(cfg.targetTemp, 1); Serial.println(F(" C"));
    Serial.print(F("  Hysteresis   : ")); Serial.print(cfg.hysteresis, 2); Serial.println(F(" C"));
    Serial.print(F("  Min Cycle    : ")); Serial.print(cfg.minCycleTime); Serial.println(F(" s"));
    Serial.print(F("  Max Runtime  : ")); Serial.print(cfg.maxRuntime); Serial.println(F(" s"));
    Serial.print(F("  Freeze Thresh: ")); Serial.print(cfg.freezeThreshold, 1); Serial.println(F(" C"));
    Serial.print(F("  Mode         : ")); Serial.println(cfg.mode == 0 ? F("AUTO") : F("MANUAL"));
    Serial.print(F("  FB Timeout   : ")); Serial.print(cfg.fallbackTimeout); Serial.println(F(" s"));
    Serial.println(F("--------------------"));
}
