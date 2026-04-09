#!/bin/bash
# TonbilTerm - Combo Firmware Flash Script
# Bilgisayara ESP32 takin ve bu scripti calistirin

set -e

echo ""
echo "==================================="
echo " TonbilTerm Combo Firmware Yukleyici"
echo " (Sensor + Relay Birlesik)"
echo "==================================="
echo ""

# ----------------------------------
# PlatformIO Kontrolu
# ----------------------------------
if ! command -v pio &> /dev/null; then
    echo "[HATA] PlatformIO bulunamadi!"
    echo ""
    echo "Cozum:"
    echo "  1. once ./setup-platformio.sh calistirin"
    echo "  2. veya manuel: pip install platformio"
    echo ""
    exit 1
fi
echo "[OK] PlatformIO bulundu."

# ----------------------------------
# ESP32 Port Tespiti
# ----------------------------------
echo ""
echo "ESP32 cihazi araniyor..."

PORT=""

SERIAL_OUTPUT=$(pio device list --serial 2>/dev/null || true)

if [ -z "$PORT" ]; then
    PORT=$(echo "$SERIAL_OUTPUT" | grep -o '/dev/ttyUSB[0-9]*' | head -1 || true)
fi
if [ -z "$PORT" ]; then
    PORT=$(echo "$SERIAL_OUTPUT" | grep -o '/dev/ttyACM[0-9]*' | head -1 || true)
fi
if [ -z "$PORT" ]; then
    PORT=$(echo "$SERIAL_OUTPUT" | grep -o '/dev/cu\.[A-Za-z0-9_.-]*' | head -1 || true)
fi

if [ -z "$PORT" ]; then
    echo ""
    echo "[HATA] ESP32 bulunamadi!"
    echo ""
    echo "Kontrol edin:"
    echo "  - USB kablo takili mi?"
    echo "  - Kablo veri destekli mi? (sadece sarj kablosu olmasin)"
    echo "  - Kullanici dialout grubunda mi? (sudo usermod -aG dialout \$USER)"
    echo "  - Linux'ta: ls /dev/ttyUSB* veya /dev/ttyACM*"
    echo "  - macOS'ta: ls /dev/cu.*"
    echo ""
    exit 1
fi

PORT_COUNT=$(echo "$SERIAL_OUTPUT" | grep -c '/dev/tty\|/dev/cu\.' || echo "0")

if [ "$PORT_COUNT" -gt 1 ]; then
    echo ""
    echo "[UYARI] Birden fazla seri cihaz bulundu ($PORT_COUNT adet)."
    echo "Bulunan portlar:"
    echo "$SERIAL_OUTPUT" | grep '/dev/tty\|/dev/cu\.'
    echo ""
    echo "$PORT kullanilacak."
    read -p "Devam etmek istiyor musunuz? (E/H): " CONFIRM
    if [[ ! "$CONFIRM" =~ ^[Ee]$ ]]; then
        echo "Iptal edildi."
        exit 0
    fi
fi

echo "[OK] ESP32 bulundu: $PORT"

# ----------------------------------
# Port erisim kontrolu
# ----------------------------------
if [ ! -w "$PORT" ]; then
    echo ""
    echo "[UYARI] $PORT icin yazma izni yok."
    echo "Cozum: sudo usermod -aG dialout \$USER && logout"
    echo "veya: sudo chmod 666 $PORT (gecici)"
    echo ""
    echo "sudo ile devam ediliyor..."
    SUDO_PREFIX="sudo"
else
    SUDO_PREFIX=""
fi

# ----------------------------------
# Derleme ve Yukleme
# ----------------------------------
echo ""
echo "Combo firmware derleniyor ve yukleniyor..."
echo "Bu islem 1-3 dakika surebilir, lutfen bekleyin..."
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMBO_DIR="$SCRIPT_DIR/../combo"

if [ ! -f "$COMBO_DIR/platformio.ini" ]; then
    echo "[HATA] platformio.ini bulunamadi!"
    echo "Combo firmware kaynak dosyalari eksik."
    echo "Beklenen konum: $COMBO_DIR/"
    exit 1
fi

cd "$COMBO_DIR"
$SUDO_PREFIX pio run --target upload --upload-port "$PORT"

if [ $? -eq 0 ]; then
    echo ""
    echo "==================================="
    echo " BASARILI! Combo firmware yuklendi"
    echo "==================================="
    echo ""
    echo "Sonraki adimlar:"
    echo "  1. ESP32'yi USB'den cikarin"
    echo "  2. BME280 sensor ve role modulune yerlestirin"
    echo "  3. Guc verin"
    echo "  4. WiFi ayari icin 'TonbilCombo-XXXX' agina baglanin"
    echo "  5. Tarayicida 192.168.4.1 adresine gidin"
    echo ""
else
    echo ""
    echo "[HATA] Yukleme basarisiz!"
    echo ""
    echo "Olasi sebepler:"
    echo "  - ESP32 BOOT modunda degil (BOOT tusuna basili tutun)"
    echo "  - Port baska uygulama tarafindan kullaniliyor"
    echo "  - USB kablo arizali"
    echo "  - Erisim izni problemi"
    echo ""
    exit 1
fi
