#!/usr/bin/env bash
# ===========================================================================
# Tonbil Termostat - Yedekleme Scripti
# Kullanim: ./scripts/backup.sh
# Cron ornegi: 0 3 * * * /path/to/server/scripts/backup.sh >> /var/log/tonbil-backup.log 2>&1
# ===========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_DIR/backups}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RETENTION_DAYS=7

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

log "=== Tonbil Yedekleme Basladi ==="
mkdir -p "$BACKUP_DIR"

# --- PostgreSQL Yedekleme ---
log "PostgreSQL yedekleniyor..."
PG_BACKUP_FILE="$BACKUP_DIR/postgres_${TIMESTAMP}.sql.gz"
docker exec tonbil-postgres pg_dump \
    -U "${POSTGRES_USER:-tonbil}" \
    -d "${POSTGRES_DB:-tonbil}" \
    --no-owner \
    --no-privileges \
    | gzip > "$PG_BACKUP_FILE"

PG_SIZE=$(du -h "$PG_BACKUP_FILE" | cut -f1)
log "PostgreSQL yedegi tamamlandi: $PG_BACKUP_FILE ($PG_SIZE)"

# --- InfluxDB Yedekleme ---
log "InfluxDB yedekleniyor..."
INFLUX_BACKUP_DIR="$BACKUP_DIR/influxdb_${TIMESTAMP}"
mkdir -p "$INFLUX_BACKUP_DIR"
docker exec tonbil-influxdb influx backup \
    /tmp/influx-backup \
    --org "${INFLUXDB_ORG:-tonbil}" \
    --token "${INFLUXDB_ADMIN_TOKEN:-tonbil-influx-admin-token-change-me}" \
    2>/dev/null

docker cp tonbil-influxdb:/tmp/influx-backup/. "$INFLUX_BACKUP_DIR/"
docker exec tonbil-influxdb rm -rf /tmp/influx-backup

# InfluxDB yedegini sıkistir
INFLUX_ARCHIVE="$BACKUP_DIR/influxdb_${TIMESTAMP}.tar.gz"
tar -czf "$INFLUX_ARCHIVE" -C "$BACKUP_DIR" "influxdb_${TIMESTAMP}"
rm -rf "$INFLUX_BACKUP_DIR"

INFLUX_SIZE=$(du -h "$INFLUX_ARCHIVE" | cut -f1)
log "InfluxDB yedegi tamamlandi: $INFLUX_ARCHIVE ($INFLUX_SIZE)"

# --- Mosquitto Persistence Yedekleme ---
log "Mosquitto verileri yedekleniyor..."
MOSQUITTO_BACKUP="$BACKUP_DIR/mosquitto_${TIMESTAMP}.tar.gz"
docker cp tonbil-mosquitto:/mosquitto/data/. /tmp/mosquitto-backup 2>/dev/null || true
if [ -d /tmp/mosquitto-backup ]; then
    tar -czf "$MOSQUITTO_BACKUP" -C /tmp mosquitto-backup
    rm -rf /tmp/mosquitto-backup
    log "Mosquitto yedegi tamamlandi: $MOSQUITTO_BACKUP"
else
    log "Mosquitto data klasoru bos, atlanıyor"
fi

# --- Eski Yedekleri Temizle ---
log "Son $RETENTION_DAYS gunden eski yedekler temizleniyor..."
DELETED_COUNT=0
while IFS= read -r -d '' file; do
    rm -f "$file"
    DELETED_COUNT=$((DELETED_COUNT + 1))
done < <(find "$BACKUP_DIR" -type f -name "*.gz" -mtime +$RETENTION_DAYS -print0 2>/dev/null)
log "Silinen eski yedek: $DELETED_COUNT dosya"

# --- Ozet ---
TOTAL_SIZE=$(du -sh "$BACKUP_DIR" | cut -f1)
BACKUP_COUNT=$(find "$BACKUP_DIR" -type f -name "*.gz" | wc -l)
log "=== Yedekleme Tamamlandi ==="
log "Toplam yedek: $BACKUP_COUNT dosya, $TOTAL_SIZE"
