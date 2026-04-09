-- ===========================================================================
-- FCM Push Notification Tablolari
-- Migration: FCM token saklama ve bildirim tercihleri
-- Tarih: 2026-03-22
-- ===========================================================================

-- FCM device token'lari
CREATE TABLE IF NOT EXISTS fcm_tokens (
    id          SERIAL PRIMARY KEY,
    user_id     INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(512) UNIQUE NOT NULL,
    device_name VARCHAR(128),
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fcm_tokens_user_id ON fcm_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_fcm_tokens_token ON fcm_tokens(token);

-- Bildirim tercihleri (kullanici basina tek satir)
CREATE TABLE IF NOT EXISTS notification_preferences (
    id                      SERIAL PRIMARY KEY,
    user_id                 INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    temp_low_alert          BOOLEAN NOT NULL DEFAULT TRUE,
    temp_high_alert         BOOLEAN NOT NULL DEFAULT TRUE,
    relay_change_alert      BOOLEAN NOT NULL DEFAULT TRUE,
    sensor_offline_alert    BOOLEAN NOT NULL DEFAULT TRUE,
    temp_low_threshold      FLOAT NOT NULL DEFAULT 16.0,
    temp_high_threshold     FLOAT NOT NULL DEFAULT 28.0,
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_notification_prefs_user UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_notification_prefs_user_id ON notification_preferences(user_id);

-- ===========================================================================
-- Rollback (geri alma):
-- DROP TABLE IF EXISTS notification_preferences;
-- DROP TABLE IF EXISTS fcm_tokens;
-- ===========================================================================
