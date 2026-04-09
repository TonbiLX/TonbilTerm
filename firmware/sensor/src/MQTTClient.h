#pragma once

#include <Arduino.h>
#include <WiFiClient.h>
#include <PubSubClient.h>
#include "ConfigStore.h"
#include "BME280Reader.h"

// ============================================================
// MQTT config mesaji callback
// ============================================================
typedef void (*MQTTConfigCallback)(const char* topic, const char* payload, unsigned int length);

// ============================================================
// MQTTClient -- PubSubClient wrapper (ESP8266)
//
// Topic yapisi:
//   tonbil/devices/{deviceId}/telemetry  -- sensor verileri (publish)
//   tonbil/devices/{deviceId}/status     -- online/offline (publish + LWT)
//   tonbil/devices/{deviceId}/config     -- uzak config (subscribe)
// ============================================================

class MQTTClient {
public:
    bool begin(const char* server, uint16_t port, const String& deviceId);

    void setCredentials(const char* username, const char* password);

    typedef void (*AuthFailureCallback)();
    void onAuthFailure(AuthFailureCallback callback);

    void loop();

    bool publishTelemetry(const SensorReading& reading);

    // NOTE: PubSubClient::connected() is NOT const on ESP8266
    bool isConnected();

    void onConfigMessage(MQTTConfigCallback callback);

    void updateServer(const char* server, uint16_t port);

private:
    WiFiClient   _wifiClient;
    PubSubClient _mqttClient;
    String       _deviceId;
    char         _server[64] = "";
    uint16_t     _port = 1883;

    // Topic'ler
    String _topicTelemetry;
    String _topicStatus;
    String _topicConfig;

    // Reconnect exponential backoff
    unsigned long _lastReconnectAttempt = 0;
    unsigned long _reconnectDelay       = 1000;
    static const unsigned long MAX_RECONNECT_DELAY = 30000;

    uint32_t _publishCount = 0;

    // MQTT auth
    char _username[64] = "";
    char _password[64] = "";
    bool _hasCredentials = false;

    // Callback
    MQTTConfigCallback _configCallback = nullptr;
    AuthFailureCallback _authFailureCallback = nullptr;

    uint8_t _consecutiveAuthFailures = 0;

    bool connectInternal();
    void buildTopics();

    static MQTTClient* _instance;
    static void mqttCallback(char* topic, byte* payload, unsigned int length);
};
