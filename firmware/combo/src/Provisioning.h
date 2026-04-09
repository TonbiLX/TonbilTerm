#pragma once

// ============================================================
// Provisioning.h -- API-based MQTT credential provisioning
//
// On first boot (no stored credentials):
//   1. POST /api/provisioning/register with device_id, type="combo"
//   2. Parse response: mqtt_username, mqtt_password, mqtt_host, etc.
//   3. Store credentials in LittleFS
//   4. Use for MQTT connection
//
// On MQTT auth failure:
//   1. Clear stored credentials
//   2. Re-provision on next loop
// ============================================================

#include <Arduino.h>

struct MQTTCredentials {
    char username[64]  = {0};
    char password[64]  = {0};
    char host[64]      = {0};
    uint16_t port      = 1883;
    char token[256]    = {0};   // Device JWT token
    bool valid         = false;
};

class Provisioning {
public:
    bool begin(const char* deviceId, const char* deviceType,
               const char* firmwareVersion);

    const MQTTCredentials& getCredentials() const;
    bool isProvisioned() const;

    bool provision(const char* apiHost, uint16_t apiPort = 8091);
    void clearCredentials();
    void onAuthFailure();
    bool needsReprovisioning() const;
    void applyFallbackCredentials(const char* mqttHost, uint16_t mqttPort = 1883);
    bool usingFallback() const;

    static const char* FALLBACK_MQTT_USER;
    static const char* FALLBACK_MQTT_PASS;

private:
    char _deviceId[16]         = {0};
    char _deviceType[10]       = {0};
    char _firmwareVersion[16]  = {0};
    MQTTCredentials _creds;
    bool _needReprovision = false;
    bool _usingFallback   = false;

    static const char* PROV_FILE;

    bool loadFromFS();
    bool saveToFS();
    void clearFS();
};
