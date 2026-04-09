#pragma once

// ============================================================
// MQTTClient.h -- MQTT wrapper for Combo device (ESP8266)
//
// Publishes: sensor telemetry + relay status + boost status
// Subscribes: relay commands + boost commands + config updates
//
// Topics:
//   PUB: tonbil/devices/{id}/telemetry -- combined data
//   SUB: tonbil/devices/{id}/command   -- relay + boost commands
//   SUB: tonbil/devices/{id}/config    -- config updates
//   LWT: tonbil/devices/{id}/status    -- "offline" on disconnect
// ============================================================

#include <Arduino.h>
#include <WiFiClient.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include "RelayController.h"
#include "BME280Reader.h"
#include "ConfigStore.h"

// Callback types for command handling
typedef void (*RelayCommandCallback)(bool on);
typedef void (*TargetCommandCallback)(float target);
typedef void (*ModeCommandCallback)(const char* mode);
typedef void (*ConfigUpdateCallback)(const RelayConfig& cfg);
typedef void (*BoostCommandCallback)(int minutes);
typedef void (*BoostCancelCallback)();
typedef void (*SensorConfigCallback)(const char* topic, const char* payload, unsigned int length);

class MQTTClient {
public:
    void begin(const char* server, uint16_t port, const char* deviceId);
    void loop();

    // Set MQTT auth credentials
    void setCredentials(const char* username, const char* password);

    // Auth failure callback
    typedef void (*AuthFailureCallback)();
    void onAuthFailure(AuthFailureCallback cb);

    // Publishing -- combined telemetry with boost fields
    void publishTelemetry(const RelayStatus& status, const BME280Data& bme,
                          bool localFallback = false, const char* fallbackLevel = nullptr,
                          bool boostActive = false, int boostRemaining = 0,
                          int boostTotal = 0);
    void publishOnline();

    // Connection state
    // NOTE: PubSubClient::connected() is NOT const on ESP8266
    bool isConnected();
    unsigned long lastMessageTime() const;

    // Command callbacks
    void onRelayCommand(RelayCommandCallback cb);
    void onTargetCommand(TargetCommandCallback cb);
    void onModeCommand(ModeCommandCallback cb);
    void onConfigUpdate(ConfigUpdateCallback cb);
    void onBoostCommand(BoostCommandCallback cb);
    void onBoostCancel(BoostCancelCallback cb);
    void onSensorConfig(SensorConfigCallback cb);

private:
    WiFiClient   _wifiClient;
    PubSubClient _mqtt;

    char _deviceId[16]        = {0};
    char _topicCommand[64]    = {0};
    char _topicConfig[64]     = {0};
    char _topicTelemetry[64]  = {0};
    char _topicStatus[64]     = {0};

    char _server[64]          = {0};
    uint16_t _port            = 1883;

    // Reconnect backoff
    unsigned long _lastReconnectAttempt = 0;
    uint32_t      _reconnectInterval   = 1000;
    static const uint32_t MAX_RECONNECT_INTERVAL = 60000;

    unsigned long _lastMessageMs = 0;

    // MQTT auth
    char _username[64] = {0};
    char _password[64] = {0};
    bool _hasCredentials = false;

    // Callbacks
    RelayCommandCallback   _onRelayCb       = nullptr;
    TargetCommandCallback  _onTargetCb      = nullptr;
    ModeCommandCallback    _onModeCb        = nullptr;
    ConfigUpdateCallback   _onConfigCb      = nullptr;
    BoostCommandCallback   _onBoostCb       = nullptr;
    BoostCancelCallback    _onBoostCancelCb = nullptr;
    SensorConfigCallback   _onSensorCfgCb   = nullptr;
    AuthFailureCallback    _onAuthFailCb    = nullptr;

    uint8_t _consecutiveAuthFailures = 0;

    void buildTopics();
    bool reconnect();
    void onMessage(char* topic, byte* payload, unsigned int length);

    static MQTTClient* _instance;
};
