#!/bin/bash
# TonbilTerm - PlatformIO Kurulum Scripti
# Bu script gerekli gelistirme araclarini kurar

set -e

echo ""
echo "==================================="
echo " TonbilTerm - PlatformIO Kurulumu"
echo "==================================="
echo ""

# ----------------------------------
# Python Kontrolu
# ----------------------------------
echo "[1/4] Python kontrol ediliyor..."

PYTHON_CMD=""
if command -v python3 &> /dev/null; then
    PYTHON_CMD="python3"
elif command -v python &> /dev/null; then
    PYTHON_CMD="python"
fi

if [ -z "$PYTHON_CMD" ]; then
    echo ""
    echo "[HATA] Python bulunamadi!"
    echo ""
    echo "Kurulum:"
    echo "  Ubuntu/Debian: sudo apt install python3 python3-pip"
    echo "  Fedora:        sudo dnf install python3 python3-pip"
    echo "  macOS:         brew install python3"
    echo "  Genel:         https://www.python.org/downloads/"
    echo ""
    exit 1
fi

PYTHON_VER=$($PYTHON_CMD --version 2>&1)
echo "[OK] $PYTHON_VER bulundu."

# ----------------------------------
# pip Kontrolu
# ----------------------------------
echo ""
echo "[2/4] pip kontrol ediliyor..."

if ! $PYTHON_CMD -m pip --version &> /dev/null; then
    echo "pip bulunamadi, kuruluyor..."
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        echo "sudo apt install python3-pip ile kurmayi deneyin."
    fi
    $PYTHON_CMD -m ensurepip --default-pip || {
        echo "[HATA] pip kurulamadi!"
        echo "Manuel: sudo apt install python3-pip (Ubuntu)"
        exit 1
    }
fi
echo "[OK] pip mevcut."

# ----------------------------------
# PlatformIO Kurulumu
# ----------------------------------
echo ""
echo "[3/4] PlatformIO kuruluyor/guncelleniyor..."

if command -v pio &> /dev/null; then
    echo "PlatformIO zaten kurulu, guncelleniyor..."
    $PYTHON_CMD -m pip install --upgrade platformio
else
    echo "PlatformIO kuruluyor (bu biraz zaman alabilir)..."
    $PYTHON_CMD -m pip install platformio
fi

# PATH kontrolu
if ! command -v pio &> /dev/null; then
    echo ""
    echo "[UYARI] pio komutu PATH'te bulunamadi."
    echo "Asagidaki satiri ~/.bashrc veya ~/.zshrc dosyaniza ekleyin:"
    SCRIPTS_DIR=$($PYTHON_CMD -c "import sysconfig; print(sysconfig.get_path('scripts'))" 2>/dev/null || echo "$HOME/.local/bin")
    echo "  export PATH=\"$SCRIPTS_DIR:\$PATH\""
    echo ""
    echo "Sonra: source ~/.bashrc"
    # Hemen ekle (gecici)
    export PATH="$SCRIPTS_DIR:$PATH"
fi

# ----------------------------------
# ESP32 Platform Kurulumu
# ----------------------------------
echo ""
echo "[4/4] ESP32 platformu kuruluyor..."

pio platform install espressif32 || {
    echo ""
    echo "[UYARI] ESP32 platformu simdiden kurulamadi."
    echo "Ilk firmware yuklemesinde otomatik kurulacaktir."
}

echo "[OK] ESP32 platformu kuruldu."

# ----------------------------------
# Linux: dialout grubu kontrolu
# ----------------------------------
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    echo ""
    if groups | grep -q dialout; then
        echo "[OK] Kullanici dialout grubunda."
    else
        echo "[UYARI] Kullanici dialout grubunda degil!"
        echo "ESP32 ile iletisim icin gerekli."
        echo "Calistirin: sudo usermod -aG dialout $USER"
        echo "Sonra: logout ve tekrar login"
    fi
fi

# ----------------------------------
# Ozet
# ----------------------------------
echo ""
echo "==================================="
echo " Kurulum Tamamlandi!"
echo "==================================="
echo ""
echo "Simdi yapabilirsiniz:"
echo "  - Sensor firmware yuklemek: ./flash-sensor.sh"
echo "  - Relay firmware yuklemek:  ./flash-relay.sh"
echo "  - Seri monitor acmak:      ./monitor.sh"
echo ""
