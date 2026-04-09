#!/bin/bash
# TonbilTerm - Seri Monitor (Debug)
# ESP32'den gelen seri verileri goruntuler

echo ""
echo "==================================="
echo " TonbilTerm Seri Monitor"
echo "==================================="
echo ""

# ----------------------------------
# PlatformIO Kontrolu
# ----------------------------------
if ! command -v pio &> /dev/null; then
    echo "[HATA] PlatformIO bulunamadi!"
    echo "Cozum: once ./setup-platformio.sh calistirin"
    exit 1
fi

# ----------------------------------
# ESP32 Port Tespiti
# ----------------------------------
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
    echo "[HATA] ESP32 bulunamadi! USB kablo takili mi?"
    echo "  Linux: ls /dev/ttyUSB* veya /dev/ttyACM*"
    echo "  macOS: ls /dev/cu.*"
    exit 1
fi

echo "[OK] ESP32 bulundu: $PORT"

# ----------------------------------
# Seri Monitor Baslat
# ----------------------------------
echo ""
echo "Seri monitor baslatiliyor (115200 baud)..."
echo "Cikmak icin: Ctrl+C"
echo ""
echo "-----------------------------------"
echo ""

pio device monitor --port "$PORT" --baud 115200
