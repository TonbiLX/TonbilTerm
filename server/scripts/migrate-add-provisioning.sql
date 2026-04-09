-- ===========================================================================
-- Migration: Add device provisioning columns and device_tokens table
-- Run manually on existing databases:
--   docker exec -i tonbil-postgres psql -U tonbil -d tonbil < migrate-add-provisioning.sql
-- ===========================================================================

-- Add provisioning columns to devices table (idempotent)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'devices' AND column_name = 'mqtt_username'
    ) THEN
        ALTER TABLE devices ADD COLUMN mqtt_username VARCHAR(100) UNIQUE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'devices' AND column_name = 'mqtt_password_hash'
    ) THEN
        ALTER TABLE devices ADD COLUMN mqtt_password_hash VARCHAR(255);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'devices' AND column_name = 'device_token'
    ) THEN
        ALTER TABLE devices ADD COLUMN device_token VARCHAR(255) UNIQUE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'devices' AND column_name = 'provisioned_at'
    ) THEN
        ALTER TABLE devices ADD COLUMN provisioned_at TIMESTAMP WITH TIME ZONE;
    END IF;
END $$;

-- Create device_tokens table (idempotent)
CREATE TABLE IF NOT EXISTS device_tokens (
    id          SERIAL PRIMARY KEY,
    device_id   VARCHAR(50) NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at  TIMESTAMP WITH TIME ZONE,
    is_active   BOOLEAN DEFAULT TRUE
);

-- Indexes (idempotent)
CREATE INDEX IF NOT EXISTS idx_devices_mqtt_username ON devices(mqtt_username);
CREATE INDEX IF NOT EXISTS idx_device_tokens_device_id ON device_tokens(device_id);
CREATE INDEX IF NOT EXISTS idx_device_tokens_active ON device_tokens(is_active) WHERE is_active = TRUE;

-- Verify
SELECT 'Migration complete. Columns added:' AS status;
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'devices'
  AND column_name IN ('mqtt_username', 'mqtt_password_hash', 'device_token', 'provisioned_at');

SELECT 'device_tokens table:' AS status;
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'device_tokens';
