// ============================================================
// TermostatOtonom -- ESP8266 Combo Firmware
//
// Combined sensor (BME280) + relay controller in one device.
// Intended for rooms where both temperature sensing and boiler
// control happen on the same ESP8266 (e.g., kitchen/mutfak).
//
// Features:
//   - BME280 temperature/humidity/pressure reading
//   - Relay control for gas boiler (GPIO14/D5, active-LOW)
//   - MQTT telemetry publishing (sensor + relay + boost)
//   - MQTT command subscription (relay, target, mode, boost)
//   - Boost mode: force relay ON for N minutes
//   - Local fallback with hysteresis when server disconnects
//   - Fixed cycle fallback when no server + extended period
//   - Safety: min cycle time, max runtime, freeze protection
//   - Boot-glitch prevention on GPIO14
//   - OTA firmware updates
//   - WiFiManager provisioning with MQTT config
//
// Hardware:
//   - NodeMCU v3 (ESP8266 ESP-12E)
//   - BME280 on I2C (SDA=D2/GPIO4, SCL=D1/GPIO5)
//   - 5V relay module on D5/GPIO14 (active-LOW)
//
// Device ID format: "combo-XXXX" (ChipId last 4 hex)
// AP name for provisioning: "TonbilCombo-XXXX"
// ============================================================

#include <Arduino.h>
#include "ConfigStore.h"
#include "WiFiProvisioning.h"
#include "BME280Reader.h"
#include "RelayController.h"
#include "MQTTClient.h"
#include "LocalFallback.h"
#include "BoostMode.h"
#include "OTAUpdater.h"
#include "Provisioning.h"

// ---- Module instances ----
ConfigStore      configStore;
WiFiProvisioning wifi;
BME280Reader     bme;
RelayController  relay;
MQTTClient       mqtt;
LocalFallback    fallback;
BoostMode        boostMode;
OTAUpdater       ota;
Provisioning     provisioning;

// ---- Runtime state ----
RelayConfig      relayConfig;
SensorConfig     sensorConfig;
BME280Data       lastBmeData;

unsigned long    lastTelemetryMs   = 0;
unsigned long    lastSensorReadMs  = 0;
unsigned long    lastStatusLogMs   = 0;
unsigned long    lastProvisionAttempt = 0;
static unsigned long lastSuccessfulPublish = 0;

static const unsigned long TELEMETRY_INTERVAL       = 10000;  // 10s
static const unsigned long SENSOR_READ_INTERVAL     = 5000;   // 5s
static const unsigned long STATUS_LOG_INTERVAL      = 60000;  // 1 min
static const unsigned long PROVISION_RETRY_INTERVAL = 300000;  // 5 dakika (30s cok sik)
static const unsigned long WATCHDOG_TIMEOUT         = 300000; // 5 min publish watchdog

// ---- Forward declarations ----
void handleRelayCommand(bool on);
void handleTargetCommand(float target);
void handleModeCommand(const char* mode);
void handleConfigUpdate(const RelayConfig& cfg);
void handleBoostCommand(int minutes);
void handleBoostCancel();
void handleSensorConfig(const char* topic, const char* payload, unsigned int length);
void onMQTTAuthFailure();
void runLocalFallbackLogic();
void publishTelemetry();
void logPeriodicStatus();

// ============================================================
// setup
// ============================================================
void setup() {
    Serial.begin(115200);
    delay(100);

    Serial.println(F(""));
    Serial.println(F("========================================"));
    Serial.println(F("[COMBO] TermostatOtonom Combo Firmware"));
    Serial.println(F("[COMBO] Sensor + Relay Combined (ESP8266)"));
    Serial.println(F("[COMBO] Version: 1.0.0"));
    Serial.println(F("========================================"));

    // 1. RELAY FIRST -- boot-glitch prevention is time-critical
    Serial.println(F("[COMBO] Phase 1: Relay pin init"));
    digitalWrite(RELAY_PIN, RELAY_OFF_STATE);
    pinMode(RELAY_PIN, OUTPUT);
    digitalWrite(RELAY_PIN, RELAY_OFF_STATE);

    // 2. Config store -- load persistent settings (LittleFS)
    Serial.println(F("[COMBO] Phase 2: Config store"));
    configStore.begin();
    relayConfig  = configStore.loadRelay();
    sensorConfig = configStore.loadSensor();

    // 3. Full relay controller init with config
    Serial.println(F("[COMBO] Phase 3: Relay controller"));
    relay.begin(relayConfig);

    // 4. BME280 sensor
    Serial.println(F("[COMBO] Phase 4: BME280 sensor"));
    bool sensorOk = bme.begin();
    if (sensorOk) {
        bme.setCalibration(sensorConfig.calibOffsetTemp, sensorConfig.calibOffsetHum);
        lastBmeData = bme.read();
        if (lastBmeData.valid) {
            Serial.print(F("[COMBO] Initial temp: "));
            Serial.print(lastBmeData.temperature, 1);
            Serial.println(F("C"));
        }
    }

    // 5. WiFi provisioning -- may block for portal
    Serial.println(F("[COMBO] Phase 5: WiFi provisioning"));
    bool wifiOk = wifi.begin(configStore);

    if (!wifiOk) {
        Serial.println(F("[COMBO] WiFi not connected -- will retry"));
    }

    // 6. Provisioning -- get MQTT credentials from API
    Serial.println(F("[COMBO] Phase 6: Device provisioning"));
    relayConfig = configStore.loadRelay();  // Reload -- MQTT params may have changed
    provisioning.begin(wifi.getDeviceId(), "combo", "1.0.0");

    if (!provisioning.isProvisioned() && wifiOk) {
        const char* apiHost = strlen(relayConfig.mqttServer) > 0
            ? relayConfig.mqttServer
            : "192.168.1.9";
        Serial.println(F("[COMBO] Attempting API provisioning..."));
        provisioning.provision(apiHost, 8091);
    }

    // 7. MQTT client -- use provisioned credentials if available
    Serial.println(F("[COMBO] Phase 7: MQTT client"));
    if (provisioning.isProvisioned()) {
        const MQTTCredentials& creds = provisioning.getCredentials();
        mqtt.begin(creds.host, creds.port, wifi.getDeviceId());
        mqtt.setCredentials(creds.username, creds.password);
        mqtt.onAuthFailure(onMQTTAuthFailure);
    } else {
        Serial.println(F("[COMBO] UYARI: Provisioning basarisiz, fallback credentials ile baglaniyor"));
        const char* srv = strlen(relayConfig.mqttServer) > 0 ? relayConfig.mqttServer : "192.168.1.9";
        uint16_t port = relayConfig.mqttPort > 0 ? relayConfig.mqttPort : 1883;
        provisioning.applyFallbackCredentials(srv, port);
        const MQTTCredentials& creds = provisioning.getCredentials();
        mqtt.begin(creds.host, creds.port, wifi.getDeviceId());
        mqtt.setCredentials(creds.username, creds.password);
        mqtt.onAuthFailure(onMQTTAuthFailure);
    }

    // Register all command callbacks
    mqtt.onRelayCommand(handleRelayCommand);
    mqtt.onTargetCommand(handleTargetCommand);
    mqtt.onModeCommand(handleModeCommand);
    mqtt.onConfigUpdate(handleConfigUpdate);
    mqtt.onBoostCommand(handleBoostCommand);
    mqtt.onBoostCancel(handleBoostCancel);
    mqtt.onSensorConfig(handleSensorConfig);

    // 8. Local fallback monitor
    Serial.println(F("[COMBO] Phase 8: Fallback monitor"));
    fallback.begin(relayConfig);

    // 9. OTA updates
    Serial.println(F("[COMBO] Phase 9: OTA updater"));
    if (wifiOk) {
        ota.begin(wifi.getDeviceId());
    }

    Serial.println(F(""));
    Serial.println(F("========================================"));
    Serial.print(F("[COMBO] Device ID: "));
    Serial.println(wifi.getDeviceId());
    Serial.print(F("[COMBO] Sensor: "));
    Serial.println(bme.isAvailable() ? F("BME280 detected") : F("NOT DETECTED"));
    Serial.print(F("[COMBO] Mode: "));
    Serial.println(relayConfig.mode == 0 ? F("AUTO") : F("MANUAL"));
    Serial.println(F("[COMBO] Boot complete"));
    Serial.println(F("========================================"));
    Serial.println(F(""));

    configStore.printConfig();
}

// ============================================================
// loop
// ============================================================
void loop() {
    unsigned long now = millis();

    // OTA takes priority
    if (ota.isUpdating()) {
        ota.loop();
        return;
    }

    // WiFi reconnection
    wifi.loop();

    // If provisioning needed, retry periodically
    if (provisioning.needsReprovisioning() && wifi.isConnected()) {
        if (now - lastProvisionAttempt >= PROVISION_RETRY_INTERVAL) {
            lastProvisionAttempt = now;

            RelayConfig cfg = configStore.loadRelay();
            const char* apiHost = strlen(cfg.mqttServer) > 0
                ? cfg.mqttServer
                : "192.168.1.9";

            Serial.println(F("[COMBO] Re-provisioning attempt..."));
            if (provisioning.provision(apiHost, 8091)) {
                const MQTTCredentials& creds = provisioning.getCredentials();
                mqtt.setCredentials(creds.username, creds.password);
                Serial.println(F("[COMBO] Re-provisioned, MQTT will reconnect"));
            }
        }
    }

    // MQTT client (handles reconnect internally)
    mqtt.loop();

    // OTA handler
    ota.loop();

    // Relay safety checks (max runtime, etc.)
    relay.loop();

    // Boost mode timer check
    boostMode.loop(relay);

    // Track server connectivity for fallback
    if (mqtt.isConnected()) {
        fallback.onServerMessage();
    }

    // Fallback state machine
    fallback.loop(mqtt.isConnected(), bme.isAvailable());

    // ---- Sensor reading (every 5s or configured interval) ----
    unsigned long sensorInterval = sensorConfig.readIntervalMs;
    if (bme.isAvailable() && (now - lastSensorReadMs >= sensorInterval)) {
        lastSensorReadMs = now;
        lastBmeData = bme.read();
    }

    // ---- Control logic ----
    // Boost mode overrides everything (except max runtime safety)
    if (boostMode.isActive()) {
        // Boost mode handles relay directly, skip normal control
        // But still feed temperature for freeze protection
        if (bme.isAvailable() && lastBmeData.valid) {
            relay.evaluate(lastBmeData.temperature);
        }
    } else if (fallback.isInFallback()) {
        runLocalFallbackLogic();
    } else {
        // Normal mode: server controls relay via MQTT commands
        // Local sensor provides hysteresis + freeze protection
        if (bme.isAvailable() && lastBmeData.valid) {
            relay.evaluate(lastBmeData.temperature);
        }
    }

    // ---- Telemetry publishing (every 10s) ----
    if (mqtt.isConnected() && (now - lastTelemetryMs >= TELEMETRY_INTERVAL)) {
        lastTelemetryMs = now;
        publishTelemetry();
        lastSuccessfulPublish = now;
    }

    // ---- Periodic status log (every 60s) ----
    if (now - lastStatusLogMs >= STATUS_LOG_INTERVAL) {
        lastStatusLogMs = now;
        logPeriodicStatus();
    }

    // ---- Watchdog: 5 dakika publish yoksa restart ----
    if (lastSuccessfulPublish > 0 && millis() - lastSuccessfulPublish > WATCHDOG_TIMEOUT) {
        Serial.println(F("[WDT] 5dk publish yok, restart..."));
        delay(100);
        ESP.restart();
    }
}

// ============================================================
// MQTT Auth Failure Handler
// ============================================================
void onMQTTAuthFailure() {
    Serial.println(F("[COMBO] MQTT auth failure, clearing credentials for re-provision"));
    provisioning.onAuthFailure();
}

// ============================================================
// MQTT Command Handlers
// ============================================================

void handleRelayCommand(bool on) {
    if (boostMode.isActive()) {
        Serial.println(F("[COMBO] Relay command ignored -- boost mode active"));
        return;
    }
    relay.setManual(on);
    configStore.setMode(1);  // Manual mode
    Serial.print(F("[COMBO] Server command: relay "));
    Serial.println(on ? F("ON") : F("OFF"));
}

void handleTargetCommand(float target) {
    relay.setTarget(target);
    configStore.setTargetTemp(target);
    Serial.print(F("[COMBO] Server command: target "));
    Serial.print(target, 1);
    Serial.println(F("C"));
}

void handleModeCommand(const char* mode) {
    if (boostMode.isActive()) {
        Serial.println(F("[COMBO] Mode command ignored -- boost mode active"));
        return;
    }
    if (strcmp(mode, "auto") == 0) {
        relay.setMode(RELAY_MODE_AUTO);
        configStore.setMode(0);
    } else if (strcmp(mode, "manual") == 0) {
        relay.setMode(RELAY_MODE_MANUAL);
        configStore.setMode(1);
    } else {
        Serial.print(F("[COMBO] Unknown mode: "));
        Serial.println(mode);
        return;
    }
    Serial.print(F("[COMBO] Server command: mode "));
    Serial.println(mode);
}

void handleConfigUpdate(const RelayConfig& cfg) {
    RelayConfig merged = relayConfig;
    merged.targetTemp        = cfg.targetTemp;
    merged.hysteresis        = cfg.hysteresis;
    merged.minCycleTime      = cfg.minCycleTime;
    merged.maxRuntime        = cfg.maxRuntime;
    merged.freezeThreshold   = cfg.freezeThreshold;
    merged.mode              = cfg.mode;

    relayConfig = merged;
    relay.updateConfig(relayConfig);
    fallback.updateConfig(relayConfig);
    configStore.saveRelay(relayConfig);

    Serial.println(F("[COMBO] Full config update from server -- saved"));
}

void handleBoostCommand(int minutes) {
    boostMode.activate(minutes, relay);
}

void handleBoostCancel() {
    boostMode.cancel(relay);
}

void handleSensorConfig(const char* topic, const char* payload, unsigned int length) {
    Serial.print(F("[COMBO] Sensor config mesaji: "));
    Serial.println(payload);

    // Parse JSON
    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, payload, length);
    if (err) {
        Serial.print(F("[COMBO] Config parse hatasi: "));
        Serial.println(err.c_str());
        return;
    }

    bool changed = false;

    if (!doc["calibOffsetTemp"].isNull()) {
        float val = doc["calibOffsetTemp"].as<float>();
        if (val >= -10.0f && val <= 10.0f) {
            sensorConfig.calibOffsetTemp = val;
            changed = true;
        }
    }

    if (!doc["calibOffsetHum"].isNull()) {
        float val = doc["calibOffsetHum"].as<float>();
        if (val >= -20.0f && val <= 20.0f) {
            sensorConfig.calibOffsetHum = val;
            changed = true;
        }
    }

    if (!doc["readIntervalMs"].isNull()) {
        uint32_t val = doc["readIntervalMs"].as<uint32_t>();
        if (val >= 1000 && val <= 60000) {
            sensorConfig.readIntervalMs = val;
            changed = true;
        }
    }

    if (changed) {
        configStore.saveSensor(sensorConfig);
        bme.setCalibration(sensorConfig.calibOffsetTemp, sensorConfig.calibOffsetHum);
        Serial.println(F("[COMBO] Sensor config guncellendi ve kaydedildi"));
    }
}

// ============================================================
// Local Fallback Logic
// ============================================================
void runLocalFallbackLogic() {
    if (boostMode.isActive()) return;  // Boost overrides fallback

    FallbackLevel level = fallback.getLevel();

    if (level == FB_LOCAL) {
        if (bme.isAvailable() && lastBmeData.valid) {
            relay.evaluate(lastBmeData.temperature);
        }
    } else if (level == FB_FIXED_CYCLE) {
        bool shouldBeOn = fallback.fixedCycleRelayState();
        RelayStatus status = relay.getStatus();
        if (shouldBeOn != status.relayOn) {
            if (shouldBeOn) {
                relay.setManual(true);
            } else {
                relay.setManual(false);
            }
        }
    }
}

// ============================================================
// Telemetry -- combined sensor + relay + boost
// ============================================================
void publishTelemetry() {
    RelayStatus status = relay.getStatus();

    const char* fbLevel = nullptr;
    if (fallback.isInFallback()) {
        fbLevel = (fallback.getLevel() == FB_LOCAL) ? "local" : "fixedCycle";
    }

    mqtt.publishTelemetry(
        status, lastBmeData,
        fallback.isInFallback(), fbLevel,
        boostMode.isActive(),
        boostMode.remainingMinutes(),
        boostMode.totalMinutes()
    );
}

// ============================================================
// Periodic Status Log
// ============================================================
void logPeriodicStatus() {
    RelayStatus status = relay.getStatus();

    Serial.println(F(""));
    Serial.println(F("--- Combo Status ---"));
    Serial.print(F("  Relay    : ")); Serial.println(status.relayOn ? F("ON") : F("OFF"));
    Serial.print(F("  Mode     : ")); Serial.println(status.mode == RELAY_MODE_AUTO ? F("AUTO") : F("MANUAL"));
    Serial.print(F("  Target   : ")); Serial.print(status.targetTemp, 1); Serial.println(F("C"));
    Serial.print(F("  MQTT     : ")); Serial.println(mqtt.isConnected() ? F("CONNECTED") : F("DISCONNECTED"));
    Serial.print(F("  WiFi     : ")); Serial.println(wifi.isConnected() ? F("CONNECTED") : F("DISCONNECTED"));
    Serial.print(F("  Fallback : "));
    switch (fallback.getLevel()) {
        case FB_NORMAL:      Serial.println(F("NORMAL")); break;
        case FB_LOCAL:       Serial.println(F("LOCAL SENSOR")); break;
        case FB_FIXED_CYCLE: Serial.println(F("FIXED CYCLE")); break;
    }
    Serial.print(F("  Sensor   : ")); Serial.println(bme.isAvailable() ? F("OK") : F("N/A"));
    if (lastBmeData.valid) {
        Serial.print(F("  Temp     : ")); Serial.print(lastBmeData.temperature, 1); Serial.println(F("C"));
        Serial.print(F("  Humidity : ")); Serial.print(lastBmeData.humidity, 1); Serial.println(F("%"));
        Serial.print(F("  Pressure : ")); Serial.print(lastBmeData.pressure, 1); Serial.println(F(" hPa"));
    }
    if (boostMode.isActive()) {
        Serial.print(F("  ** BOOST : "));
        Serial.print(boostMode.remainingMinutes());
        Serial.print(F("/"));
        Serial.print(boostMode.totalMinutes());
        Serial.println(F(" min remaining **"));
    }
    Serial.print(F("  Uptime   : ")); Serial.print(status.uptime); Serial.println(F("s"));
    Serial.print(F("  FreeHeap : ")); Serial.println(ESP.getFreeHeap());
    if (status.freezeProtect) Serial.println(F("  ** FREEZE PROTECTION ACTIVE **"));
    if (status.maxRuntimeHit) Serial.println(F("  ** MAX RUNTIME COOLDOWN **"));
    if (status.cycleLocked)   Serial.println(F("  ** CYCLE LOCKED **"));
    Serial.println(F("--------------------"));
    Serial.println(F(""));
}
