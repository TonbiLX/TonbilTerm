-- Heating Profiles tablosu
CREATE TABLE IF NOT EXISTS heating_profiles (
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

-- Varsayilan profiller
INSERT INTO heating_profiles (name, icon, target_temp, hysteresis, is_default, sort_order) VALUES
    ('ECO', 'eco', 20.0, 1.0, TRUE, 1),
    ('Konfor', 'whatshot', 23.0, 0.3, TRUE, 2)
ON CONFLICT DO NOTHING;
