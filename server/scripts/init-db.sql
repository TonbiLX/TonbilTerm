-- ===========================================================================
-- Tonbil Termostat - PostgreSQL Initial Schema
-- Bu dosya sadece ilk kurulumda calisir (docker-entrypoint-initdb.d)
-- ===========================================================================

-- Extension'lar
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ===========================================================================
-- Tablolar
-- ===========================================================================

-- Odalar
CREATE TABLE rooms (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    weight      FLOAT NOT NULL DEFAULT 1.0,
    min_temp    FLOAT NOT NULL DEFAULT 5.0,
    icon        VARCHAR(50) DEFAULT 'room',
    sort_order  INT DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Kullanicilar
CREATE TABLE users (
    id              SERIAL PRIMARY KEY,
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    role            VARCHAR(20) NOT NULL DEFAULT 'user',
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Cihazlar
CREATE TABLE devices (
    id                  SERIAL PRIMARY KEY,
    device_id           VARCHAR(50) UNIQUE NOT NULL,   -- ESP32 MAC veya UUID
    name                VARCHAR(100),
    room_id             INT REFERENCES rooms(id) ON DELETE SET NULL,
    type                VARCHAR(20) NOT NULL CHECK (type IN ('sensor', 'relay', 'combo')),
    mqtt_user           VARCHAR(100),
    mqtt_username       VARCHAR(100) UNIQUE,           -- Provisioning: MQTT auth username
    mqtt_password_hash  VARCHAR(255),                  -- Provisioning: bcrypt hash of MQTT password
    device_token        VARCHAR(255) UNIQUE,           -- Provisioning: JWT token prefix for identification
    provisioned_at      TIMESTAMP WITH TIME ZONE,      -- When device was provisioned
    last_seen           TIMESTAMP WITH TIME ZONE,
    firmware_version    VARCHAR(50),
    ip_address          VARCHAR(45),
    is_online           BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Cihaz token'lari (provisioning token lifecycle)
CREATE TABLE device_tokens (
    id          SERIAL PRIMARY KEY,
    device_id   VARCHAR(50) NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at  TIMESTAMP WITH TIME ZONE,
    is_active   BOOLEAN DEFAULT TRUE
);

-- Isitma konfigurasyonu (tek satir)
CREATE TABLE heating_config (
    id              SERIAL PRIMARY KEY,
    target_temp     FLOAT NOT NULL DEFAULT 22.0,
    hysteresis      FLOAT NOT NULL DEFAULT 0.5,
    min_cycle_min   INT NOT NULL DEFAULT 3,
    mode            VARCHAR(20) NOT NULL DEFAULT 'auto' CHECK (mode IN ('auto', 'manual', 'manual_on', 'manual_off', 'schedule')),
    strategy        VARCHAR(30) NOT NULL DEFAULT 'weighted_avg' CHECK (strategy IN ('weighted_avg', 'coldest_room', 'hottest_room', 'single_room')),
    gas_price_per_m3    FLOAT DEFAULT 7.0,
    floor_area_m2       FLOAT DEFAULT 100.0,
    boiler_power_kw     FLOAT DEFAULT 30.0,
    flow_temp           FLOAT DEFAULT 60.0,
    boiler_brand        VARCHAR(100) DEFAULT 'ECA Proteus Premix',
    boiler_model        VARCHAR(50) DEFAULT '30kW',
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Haftalik zamanlama
CREATE TABLE schedules (
    id              SERIAL PRIMARY KEY,
    day_of_week     INT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),  -- 0=Pazartesi
    hour            INT NOT NULL CHECK (hour BETWEEN 0 AND 23),
    minute          INT NOT NULL DEFAULT 0 CHECK (minute BETWEEN 0 AND 59),
    target_temp     FLOAT NOT NULL,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Isitma profilleri
CREATE TABLE heating_profiles (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    icon        VARCHAR(30) DEFAULT 'thermostat',
    target_temp FLOAT NOT NULL,
    hysteresis  FLOAT NOT NULL DEFAULT 0.5,
    is_default  BOOLEAN DEFAULT FALSE,
    sort_order  INT DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Denetim logu
CREATE TABLE audit_log (
    id          SERIAL PRIMARY KEY,
    device_id   VARCHAR(50),
    action      VARCHAR(100) NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ===========================================================================
-- Index'ler
-- ===========================================================================

CREATE INDEX idx_devices_room_id ON devices(room_id);
CREATE INDEX idx_devices_device_id ON devices(device_id);
CREATE INDEX idx_devices_type ON devices(type);
CREATE INDEX idx_devices_last_seen ON devices(last_seen);
CREATE INDEX idx_schedules_day_hour ON schedules(day_of_week, hour, minute);
CREATE INDEX idx_schedules_enabled ON schedules(enabled) WHERE enabled = TRUE;
CREATE INDEX idx_audit_log_device_id ON audit_log(device_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_devices_mqtt_username ON devices(mqtt_username);
CREATE INDEX idx_device_tokens_device_id ON device_tokens(device_id);
CREATE INDEX idx_device_tokens_active ON device_tokens(is_active) WHERE is_active = TRUE;

-- ===========================================================================
-- Varsayilan Veriler
-- ===========================================================================

-- Varsayilan admin kullanici
-- Sifre: admin123 (bcrypt hash)
INSERT INTO users (email, password_hash, display_name, role)
VALUES (
    'admin@tonbil.local',
    '$2b$12$C6J7QGBV4UHF8Fo2MJ3Y8u7UitCb285uLp44q4D6lObylEXNiwO0m',
    'Admin',
    'admin'
);

-- Varsayilan isitma konfigurasyonu
INSERT INTO heating_config (target_temp, hysteresis, min_cycle_min, mode, strategy)
VALUES (22.0, 0.5, 3, 'auto', 'weighted_avg');

-- Varsayilan isitma profilleri
INSERT INTO heating_profiles (name, icon, target_temp, hysteresis, is_default, sort_order) VALUES
    ('ECO',    'eco',      20.0, 1.0, TRUE, 1),
    ('Konfor', 'whatshot',  23.0, 0.3, TRUE, 2);

-- Ornek odalar
INSERT INTO rooms (name, weight, min_temp, icon, sort_order) VALUES
    ('Salon',       1.5,  10.0, 'sofa',     1),
    ('Yatak Odasi', 1.0,  8.0,  'bed',      2),
    ('Mutfak',      0.8,  5.0,  'kitchen',  3),
    ('Banyo',       1.2,  12.0, 'bath',     4);
