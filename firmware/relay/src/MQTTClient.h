#pragma once

// ============================================================
// MQTTClient.h -- MQTT wrapper for Relay device (ESP8266)
// ============================================================

#include <Arduino.h>
#include <WiFiClient.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include "RelayController.h"
#include "BME280Reader.h"
#include "ConfigStore.h"

typedef void (*RelayCommandCallback)(bool on);
typedef void (*TargetCommandCallback)(float target);
typedef void (*ModeCommandCallback)(const char* mode);
typedef void (*ConfigUpdateCallback)(const RelayConfig& cfg);
typedef void (*BoostCommandCallback)(int minutes);
typedef void (*BoostCancelCallback)();

class MQTTClient {
public:
    void begin(const char* server, uint16_t port, const char* deviceId);
    void loop();

    void setCredentials(const char* username, const char* password);

    typedef void (*AuthFailureCallback)();
    void onAuthFailure(AuthFailureCallback cb);

    void publishTelemetry(const RelayStatus& status, const BME280Data& bme,
                          bool localFallback = false, const char* fallbackLevel = nullptr,
                          bool boostActive = false, int boostRemaining = 0,
                          int boostTotal = 0);
    void publishOnline();

    // NOTE: PubSubClient::connected() is NOT const on ESP8266
    bool isConnected();
    unsigned long lastMessageTime() const;

    void onRelayCommand(RelayCommandCallback cb);
    void onTargetCommand(TargetCommandCallback cb);
    void onModeCommand(ModeCommandCallback cb);
    void onConfigUpdate(ConfigUpdateCallback cb);
    void onBoostCommand(BoostCommandCallback cb);
    void onBoostCancel(BoostCancelCallback cb);

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

    unsigned long _lastReconnectAttempt = 0;
    uint32_t      _reconnectInterval   = 1000;
    static const uint32_t MAX_RECONNECT_INTERVAL = 60000;

    unsigned long _lastTelemetryMs = 0;
    static const unsigned long TELEMETRY_INTERVAL = 10000;

    unsigned long _lastMessageMs = 0;

    char _username[64] = {0};
    char _password[64] = {0};
    bool _hasCredentials = false;

    RelayCommandCallback  _onRelayCb       = nullptr;
    TargetCommandCallback _onTargetCb      = nullptr;
    ModeCommandCallback   _onModeCb        = nullptr;
    ConfigUpdateCallback  _onConfigCb      = nullptr;
    BoostCommandCallback  _onBoostCb       = nullptr;
    BoostCancelCallback   _onBoostCancelCb = nullptr;
    AuthFailureCallback   _onAuthFailCb    = nullptr;

    uint8_t _consecutiveAuthFailures = 0;

    void buildTopics();
    bool reconnect();
    void onMessage(char* topic, byte* payload, unsigned int length);

    static MQTTClient* _instance;
};
