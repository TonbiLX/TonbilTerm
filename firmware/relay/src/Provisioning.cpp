#include "Provisioning.h"
#include <LittleFS.h>
#include <ESP8266HTTPClient.h>
#include <ArduinoJson.h>
#include <ESP8266WiFi.h>

const char* Provisioning::PROV_FILE = "/provision.json";
const char* Provisioning::FALLBACK_MQTT_USER = "device_default";
const char* Provisioning::FALLBACK_MQTT_PASS = "tonbil_device_2024";

// ============================================================
// begin() -- load stored credentials from LittleFS
// ============================================================
bool Provisioning::begin(const char* deviceId, const char* deviceType,
                          const char* firmwareVersion) {
    strncpy(_deviceId, deviceId, sizeof(_deviceId) - 1);
    strncpy(_deviceType, deviceType, sizeof(_deviceType) - 1);
    strncpy(_firmwareVersion, firmwareVersion, sizeof(_firmwareVersion) - 1);
    _needReprovision = false;

    Serial.print(F("[PROV] Initializing for device: "));
    Serial.println(_deviceId);

    if (loadFromFS()) {
        Serial.println(F("[PROV] Credentials loaded from LittleFS"));
        Serial.print(F("[PROV]   MQTT user: "));
        Serial.println(_creds.username);
        Serial.print(F("[PROV]   MQTT host: "));
        Serial.print(_creds.host);
        Serial.print(F(":"));
        Serial.println(_creds.port);
        return true;
    }

    Serial.println(F("[PROV] No stored credentials, provisioning required"));
    return false;
}

const MQTTCredentials& Provisioning::getCredentials() const {
    return _creds;
}

bool Provisioning::isProvisioned() const {
    return _creds.valid;
}

// ============================================================
// provision() -- call API to register device and get credentials
// ============================================================
bool Provisioning::provision(const char* apiHost, uint16_t apiPort) {
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println(F("[PROV] WiFi not connected, cannot provision"));
        return false;
    }

    Serial.print(F("[PROV] Provisioning via API: http://"));
    Serial.print(apiHost);
    Serial.print(F(":"));
    Serial.println(apiPort);

    JsonDocument reqDoc;
    reqDoc["device_id"] = _deviceId;
    reqDoc["type"] = _deviceType;
    reqDoc["firmware_version"] = _firmwareVersion;

    char reqBody[256];
    serializeJson(reqDoc, reqBody, sizeof(reqBody));

    WiFiClient client;
    HTTPClient http;
    String url = "http://" + String(apiHost) + ":" + String(apiPort)
                 + "/api/provisioning/register";

    http.begin(client, url);
    http.addHeader("Content-Type", "application/json");
    http.setTimeout(15000);

    Serial.print(F("[PROV] POST "));
    Serial.println(url);

    int httpCode = http.POST(reqBody);

    if (httpCode <= 0) {
        Serial.print(F("[PROV] HTTP error: "));
        Serial.println(http.errorToString(httpCode));
        http.end();
        return false;
    }

    Serial.print(F("[PROV] HTTP response: "));
    Serial.println(httpCode);

    if (httpCode == 409) {
        Serial.println(F("[PROV] Device already provisioned on server"));
        http.end();
        return false;
    }

    if (httpCode != 200) {
        Serial.print(F("[PROV] Unexpected status: "));
        Serial.println(httpCode);
        String body = http.getString();
        Serial.println(body.substring(0, 200));
        http.end();
        return false;
    }

    String responseBody = http.getString();
    http.end();

    JsonDocument resDoc;
    DeserializationError err = deserializeJson(resDoc, responseBody);
    if (err) {
        Serial.print(F("[PROV] JSON parse error: "));
        Serial.println(err.c_str());
        return false;
    }

    bool success = resDoc["success"] | false;
    if (!success) {
        Serial.println(F("[PROV] API returned success=false"));
        const char* error = resDoc["error"] | "unknown";
        Serial.print(F("[PROV] Error: "));
        Serial.println(error);
        return false;
    }

    JsonObject data = resDoc["data"];
    if (data.isNull()) {
        Serial.println(F("[PROV] No data in response"));
        return false;
    }

    const char* mqttUser = data["mqtt_username"] | "";
    const char* mqttPass = data["mqtt_password"] | "";
    const char* mqttHost = data["mqtt_host"] | "";
    uint16_t mqttPort    = data["mqtt_port"] | 1883;
    const char* devToken = data["device_token"] | "";

    if (strlen(mqttUser) == 0 || strlen(mqttPass) == 0 || strlen(mqttHost) == 0) {
        Serial.println(F("[PROV] Incomplete credentials in response"));
        return false;
    }

    strncpy(_creds.username, mqttUser, sizeof(_creds.username) - 1);
    strncpy(_creds.password, mqttPass, sizeof(_creds.password) - 1);
    strncpy(_creds.host, mqttHost, sizeof(_creds.host) - 1);
    _creds.port = mqttPort;
    strncpy(_creds.token, devToken, sizeof(_creds.token) - 1);
    _creds.valid = true;

    if (!saveToFS()) {
        Serial.println(F("[PROV] WARNING: Failed to save credentials to LittleFS"));
    }

    _needReprovision = false;
    _usingFallback = false;

    Serial.println(F("[PROV] Provisioning successful!"));
    Serial.print(F("[PROV]   MQTT user: "));
    Serial.println(_creds.username);
    Serial.print(F("[PROV]   MQTT host: "));
    Serial.print(_creds.host);
    Serial.print(F(":"));
    Serial.println(_creds.port);

    return true;
}

void Provisioning::clearCredentials() {
    Serial.println(F("[PROV] Clearing stored credentials"));
    clearFS();
    memset(&_creds, 0, sizeof(_creds));
    _creds.port = 1883;
    _creds.valid = false;
}

void Provisioning::onAuthFailure() {
    Serial.println(F("[PROV] MQTT auth failure detected, clearing credentials"));
    clearCredentials();
    _needReprovision = true;
}

bool Provisioning::needsReprovisioning() const {
    return _needReprovision || !_creds.valid || _usingFallback;
}

void Provisioning::applyFallbackCredentials(const char* mqttHost, uint16_t mqttPort) {
    Serial.println(F("[MQTT] Using fallback credentials (device_default)"));
    strncpy(_creds.username, FALLBACK_MQTT_USER, sizeof(_creds.username) - 1);
    strncpy(_creds.password, FALLBACK_MQTT_PASS, sizeof(_creds.password) - 1);
    strncpy(_creds.host, mqttHost, sizeof(_creds.host) - 1);
    _creds.port = mqttPort;
    _creds.token[0] = '\0';
    _creds.valid = true;
    _usingFallback = true;
}

bool Provisioning::usingFallback() const {
    return _usingFallback;
}

// ============================================================
// LittleFS Operations
// ============================================================
bool Provisioning::loadFromFS() {
    if (!LittleFS.exists(PROV_FILE)) return false;

    File f = LittleFS.open(PROV_FILE, "r");
    if (!f) {
        Serial.println(F("[PROV] File open failed"));
        return false;
    }

    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, f);
    f.close();

    if (err) return false;

    const char* user  = doc["mqtt_user"] | "";
    const char* pass  = doc["mqtt_pass"] | "";
    const char* host  = doc["mqtt_host"] | "";
    uint16_t port     = doc["mqtt_port"] | 1883;
    const char* token = doc["dev_token"] | "";

    if (strlen(user) == 0 || strlen(pass) == 0 || strlen(host) == 0) {
        return false;
    }

    strncpy(_creds.username, user, sizeof(_creds.username) - 1);
    strncpy(_creds.password, pass, sizeof(_creds.password) - 1);
    strncpy(_creds.host, host, sizeof(_creds.host) - 1);
    _creds.port = port;
    strncpy(_creds.token, token, sizeof(_creds.token) - 1);
    _creds.valid = true;

    return true;
}

bool Provisioning::saveToFS() {
    File f = LittleFS.open(PROV_FILE, "w");
    if (!f) {
        Serial.println(F("[PROV] File write open failed"));
        return false;
    }

    JsonDocument doc;
    doc["mqtt_user"] = _creds.username;
    doc["mqtt_pass"] = _creds.password;
    doc["mqtt_host"] = _creds.host;
    doc["mqtt_port"] = _creds.port;
    doc["dev_token"] = _creds.token;

    serializeJson(doc, f);
    f.close();

    Serial.println(F("[PROV] Credentials saved to LittleFS"));
    return true;
}

void Provisioning::clearFS() {
    if (LittleFS.exists(PROV_FILE)) {
        LittleFS.remove(PROV_FILE);
        Serial.println(F("[PROV] Credentials file removed"));
    }
}
