#pragma once

// ============================================================
// WiFiProvisioning.h -- WiFiManager-based provisioning (ESP8266)
//
// On first boot: opens AP "TonbilRelay-XXXX" (ChipId last 4)
// ============================================================

#include <Arduino.h>
#include <WiFiManager.h>
#include "ConfigStore.h"

class WiFiProvisioning {
public:
    bool begin(ConfigStore& configStore);
    void loop();
    const char* getDeviceId() const;
    bool isConnected() const;
    void resetCredentials();

private:
    char _deviceId[16] = {0};
    char _apName[24]   = {0};

    char _mqttServerParam[64] = "192.168.1.100";
    char _mqttPortParam[8]    = "1883";

    unsigned long _lastReconnectAttempt = 0;
    bool _connected = false;

    void buildDeviceId();
};
