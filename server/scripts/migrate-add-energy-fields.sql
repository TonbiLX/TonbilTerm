-- ===========================================================================
-- Migration: Enerji hesaplama icin heating_config tablosuna yeni kolonlar
-- Tarih: 2026-03-22
-- Geri alinabilir: EVET (DROP COLUMN ile)
-- ===========================================================================

-- Radyator akis sicakligi (kullanici kombi panelinden ayarlar)
-- Bu deger gaz tuketimi ve verimlilik hesaplarinda kullanilir
ALTER TABLE heating_config ADD COLUMN IF NOT EXISTS flow_temp FLOAT DEFAULT 60.0;

-- Kombi marka/model bilgisi (hesaplama referansi)
ALTER TABLE heating_config ADD COLUMN IF NOT EXISTS boiler_brand VARCHAR(100) DEFAULT 'ECA Proteus Premix';
ALTER TABLE heating_config ADD COLUMN IF NOT EXISTS boiler_model VARCHAR(50) DEFAULT '30kW';

-- ===========================================================================
-- Rollback:
-- ALTER TABLE heating_config DROP COLUMN IF EXISTS flow_temp;
-- ALTER TABLE heating_config DROP COLUMN IF EXISTS boiler_brand;
-- ALTER TABLE heating_config DROP COLUMN IF EXISTS boiler_model;
-- ===========================================================================
