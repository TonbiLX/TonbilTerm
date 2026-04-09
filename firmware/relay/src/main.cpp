// ============================================================
// TermostatOtonom -- ESP8266 Relay Firmware
//
// Controls a 5V relay for gas boiler (kombi) via MQTT.
// Features:
//   - MQTT command control (setRelay, setTarget, setMode)
//   - Local fallback with BME280 sensor (optional)
//   - Fixed cycle fallback when no sensor + no server
//   - Safety: min cycle time, max runtime, freeze protection
//   - Boot-glitch prevention on GPIO14/D5 (active-LOW relay)
//   - OTA firmware updates
//   - WiFiManager provisioning with MQTT config
//
// Hardware:
//   - NodeMCU v3 (ESP8266 ESP-12E)
//   - 5V relay module on D5/GPIO14 (active-LOW)
//   - Optional BME280 on I2C (SDA=D2/GPIO4, SCL=D1/GPIO5)
// ============================================================

#include <Arduino.h>
#include "ConfigStore.h"
#include "WiFiProvisioning.h"
#include "RelayController.h"
#include "MQTTClient.h"
#include "BME280Reader.h"
#include "LocalFallback.h"
#include "BoostMode.h"
#include "OTAUpdater.h"
#include "Provisioning.h"

// ---- Module instances ----
ConfigStore      configStore;
WiFiProvisioning wifi;
RelayController  relay;
MQTTClient       mqtt;
BME280Reader     bme;
LocalFallback    fallback;
BoostMode        boostMode;
OTAUpdater       ota;
Provisioning     provisioning;

// ---- Runtime state ----
RelayConfig      config;
unsigned long    lastTelemetryMs   = 0;
unsigned long    lastSensorReadMs  = 0;
unsigned long    lastStatusLogMs   = 0;
BME280Data       lastBmeData;

static const unsigned long TELEMETRY_INTERVAL   = 10000;  // 10s
static const unsigned long SENSOR_READ_INTERVAL = 5000;   // 5s
static const unsigned long STATUS_LOG_INTERVAL  = 60000;  // 1 min
static const unsigned long PROVISION_RETRY_INTERVAL = 30000; // 30s
static const unsigned long WATCHDOG_TIMEOUT         = 300000; // 5 min publish watchdog

unsigned long lastProvisionAttempt = 0;
static unsigned long lastSuccessfulPublish = 0;

// ---- Forward declarations ----
void handleRelayCommand(bool on);
void handleTargetCommand(float target);
void handleModeCommand(const char* mode);
void handleConfigUpdate(const RelayConfig& cfg);
void handleBoostCommand(int minutes);
void handleBoostCancel();
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
    Serial.println(F("[RELAY] TermostatOtonom Relay Firmware"));
    Serial.println(F("[RELAY] ESP8266 | Version: 1.0.0"));
    Serial.println(F("========================================"));

    // 1. RELAY FIRST -- boot-glitch prevention is time-critical
    Serial.println(F("[RELAY] Phase 1: Relay pin init"));
    digitalWrite(RELAY_PIN, RELAY_OFF_STATE);
    pinMode(RELAY_PIN, OUTPUT);
    digitalWrite(RELAY_PIN, RELAY_OFF_STATE);

    // 2. Config store -- load persistent settings (LittleFS)
    Serial.println(F("[RELAY] Phase 2: Config store"));
    configStore.begin();
    config = configStore.load();
    configStore.print(config);

    // 3. Full relay controller init with config
    Serial.println(F("[RELAY] Phase 3: Relay controller"));
    relay.begin(config);

    // 4. WiFi provisioning -- may block for portal
    Serial.println(F("[RELAY] Phase 4: WiFi provisioning"));
    bool wifiOk = wifi.begin(configStore);

    if (!wifiOk) {
        Serial.println(F("[RELAY] WiFi not connected -- will retry"));
    }

    // 5. Optional BME280 sensor
    Serial.println(F("[RELAY] Phase 5: BME280 sensor"));
    bool sensorOk = bme.begin();
    if (sensorOk) {
        lastBmeData = bme.read();
        if (lastBmeData.valid) {
            Serial.print(F("[RELAY] Initial temp: "));
            Serial.print(lastBmeData.temperature, 1);
            Serial.println(F("C"));
        }
    }

    // 6. Provisioning -- get MQTT credentials from API
    Serial.println(F("[RELAY] Phase 6: Device provisioning"));
    config = configStore.load();  // Reload
    provisioning.begin(wifi.getDeviceId(), "relay", "1.0.0");

    if (!provisioning.isProvisioned() && wifiOk) {
        const char* apiHost = strlen(config.mqttServer) > 0
            ? config.mqttServer
            : "192.168.1.9";
        Serial.println(F("[RELAY] Attempting API provisioning..."));
        provisioning.provision(apiHost, 8091);
    }

    // 7. MQTT client -- use provisioned credentials if available
    Serial.println(F("[RELAY] Phase 7: MQTT client"));
    if (provisioning.isProvisioned()) {
        const MQTTCredentials& creds = provisioning.getCredentials();
        mqtt.begin(creds.host, creds.port, wifi.getDeviceId());
        mqtt.setCredentials(creds.username, creds.password);
        mqtt.onAuthFailure(onMQTTAuthFailure);
    } else {
        Serial.println(F("[RELAY] UYARI: Provisioning basarisiz, fallback credentials ile baglaniyor"));
        const char* srv = strlen(config.mqttServer) > 0 ? config.mqttServer : "192.168.1.9";
        uint16_t port = config.mqttPort > 0 ? config.mqttPort : 1883;
        provisioning.applyFallbackCredentials(srv, port);
        const MQTTCredentials& creds = provisioning.getCredentials();
        mqtt.begin(creds.host, creds.port, wifi.getDeviceId());
        mqtt.setCredentials(creds.username, creds.password);
        mqtt.onAuthFailure(onMQTTAuthFailure);
    }
    mqtt.onRelayCommand(handleRelayCommand);
    mqtt.onTargetCommand(handleTargetCommand);
    mqtt.onModeCommand(handleModeCommand);
    mqtt.onConfigUpdate(handleConfigUpdate);
    mqtt.onBoostCommand(handleBoostCommand);
    mqtt.onBoostCancel(handleBoostCancel);

    // 8. Local fallback monitor
    Serial.println(F("[RELAY] Phase 8: Fallback monitor"));
    fallback.begin(config);

    // 9. OTA updates
    Serial.println(F("[RELAY] Phase 9: OTA updater"));
    if (wifiOk) {
        ota.begin(wifi.getDeviceId());
    }

    Serial.println(F(""));
    Serial.println(F("========================================"));
    Serial.print(F("[RELAY] Device ID: "));
    Serial.println(wifi.getDeviceId());
    Serial.print(F("[RELAY] Sensor: "));
    Serial.println(bme.isAvailable() ? F("BME280 detected") : F("NOT DETECTED"));
    Serial.print(F("[RELAY] Mode: "));
    Serial.println(config.mode == 0 ? F("AUTO") : F("MANUAL"));
    Serial.print(F("[RELAY] Free heap: "));
    Serial.println(ESP.getFreeHeap());
    Serial.println(F("[RELAY] Boot complete"));
    Serial.println(F("========================================"));
    Serial.println(F(""));
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

            RelayConfig cfg = configStore.load();
            const char* apiHost = strlen(cfg.mqttServer) > 0
                ? cfg.mqttServer
                : "192.168.1.9";

            Serial.println(F("[RELAY] Re-provisioning attempt..."));
            if (provisioning.provision(apiHost, 8091)) {
                const MQTTCredentials& creds = provisioning.getCredentials();
                mqtt.setCredentials(creds.username, creds.password);
                Serial.println(F("[RELAY] Re-provisioned, MQTT will reconnect"));
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

    // ---- Sensor reading (every 5s) ----
    if (bme.isAvailable() && (now - lastSensorReadMs >= SENSOR_READ_INTERVAL)) {
        lastSensorReadMs = now;
        lastBmeData = bme.read();
    }

    // ---- Control logic ----
    if (boostMode.isActive()) {
        if (bme.isAvailable() && lastBmeData.valid) {
            relay.evaluate(lastBmeData.temperature);
        }
    } else if (fallback.isInFallback()) {
        runLocalFallbackLogic();
    } else {
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
    Serial.println(F("[RELAY] MQTT auth failure, clearing credentials for re-provision"));
    provisioning.onAuthFailure();
}

// ============================================================
// MQTT Command Handlers
// ============================================================

void handleRelayCommand(bool on) {
    if (boostMode.isActive()) {
        Serial.println(F("[RELAY] Relay command ignored -- boost mode active"));
        return;
    }
    relay.setManual(on);
    configStore.setMode(1);
    Serial.print(F("[RELAY] Server command: relay "));
    Serial.println(on ? F("ON") : F("OFF"));
}

void handleTargetCommand(float target) {
    relay.setTarget(target);
    configStore.setTargetTemp(target);
    Serial.print(F("[RELAY] Server command: target "));
    Serial.print(target, 1);
    Serial.println(F("C"));
}

void handleModeCommand(const char* mode) {
    if (boostMode.isActive()) {
        Serial.println(F("[RELAY] Mode command ignored -- boost mode active"));
        return;
    }
    if (strcmp(mode, "auto") == 0) {
        relay.setMode(RELAY_MODE_AUTO);
        configStore.setMode(0);
    } else if (strcmp(mode, "manual") == 0) {
        relay.setMode(RELAY_MODE_MANUAL);
        configStore.setMode(1);
    } else {
        Serial.print(F("[RELAY] Unknown mode: "));
        Serial.println(mode);
        return;
    }
    Serial.print(F("[RELAY] Server command: mode "));
    Serial.println(mode);
}

void handleBoostCommand(int minutes) {
    boostMode.activate(minutes, relay);
}

void handleBoostCancel() {
    boostMode.cancel(relay);
}

void handleConfigUpdate(const RelayConfig& cfg) {
    RelayConfig merged = config;
    merged.targetTemp        = cfg.targetTemp;
    merged.hysteresis        = cfg.hysteresis;
    merged.minCycleTime      = cfg.minCycleTime;
    merged.maxRuntime        = cfg.maxRuntime;
    merged.freezeThreshold   = cfg.freezeThreshold;
    merged.mode              = cfg.mode;

    config = merged;
    relay.updateConfig(config);
    fallback.updateConfig(config);
    configStore.save(config);

    Serial.println(F("[RELAY] Full config update from server -- saved"));
}

// ============================================================
// Local Fallback Logic
// ============================================================
void runLocalFallbackLogic() {
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
// Telemetry
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
    Serial.println(F("--- Status ---"));
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
    Serial.println(F("--------------"));
    Serial.println(F(""));
}
