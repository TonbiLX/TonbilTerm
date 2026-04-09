#pragma once

// ============================================================
// WiFiProvisioning.h -- WiFiManager-based provisioning for Combo
//
// On first boot: opens AP "TonbilCombo-XXXX" (ChipId last 4)
// User connects, enters WiFi credentials + MQTT server/port
// Credentials saved via WiFiManager library
// MQTT params saved to our ConfigStore (LittleFS)
// ============================================================

#include <Arduino.h>
#include <WiFiManager.h>
#include "ConfigStore.h"

class WiFiProvisioning {
public:
    // Initialize WiFi -- returns true if connected to STA
    bool begin(ConfigStore& configStore);

    // Call in loop -- handles reconnection
    void loop();

    // Get device ID (combo-XXXX)
    const char* getDeviceId() const;

    // Check connection status
    bool isConnected() const;

    // Force reset WiFi credentials (factory reset)
    void resetCredentials();

private:
    char _deviceId[16] = {0};
    char _apName[24]   = {0};

    // Custom parameters for WiFiManager portal
    char _mqttServerParam[64] = "192.168.1.100";
    char _mqttPortParam[8]    = "1883";

    unsigned long _lastReconnectAttempt = 0;
    bool _connected = false;

    void buildNames(const char* deviceId);
};
