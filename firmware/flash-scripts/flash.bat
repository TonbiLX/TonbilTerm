@echo off
chcp 65001 >nul 2>nul
setlocal enabledelayedexpansion

:: =====================================================================
:: TonbilTerm - Tek Tusla ESP8266 Flash & Dogrulama
::
:: Kullanim:
::   flash.bat              (interaktif menu)
::   flash.bat sensor       (direkt sensor flash)
::   flash.bat combo        (direkt combo flash)
::   flash.bat relay        (direkt relay flash)
:: =====================================================================

title TonbilTerm ESP8266 Flash Tool

echo.
echo  ╔═══════════════════════════════════════════════╗
echo  ║  TonbilTerm ESP8266 Flash Tool                ║
echo  ║  Sensor / Combo / Relay firmware yukleyici     ║
echo  ╚═══════════════════════════════════════════════╝
echo.

:: ------------------------------------------------------------------
:: 1. PlatformIO kontrolu
:: ------------------------------------------------------------------
where pio >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo  [HATA] PlatformIO bulunamadi!
    echo.
    echo  Cozum:  pip install platformio
    echo.
    pause
    exit /b 1
)
echo  [OK] PlatformIO hazir

:: ------------------------------------------------------------------
:: 2. CH340/CP2102 COM port tespiti
:: ------------------------------------------------------------------
echo.
echo  ESP8266 araniyor...

set "CH340_PORT="
set "PORT_COUNT=0"

:: PowerShell ile CH340/CP2102 portunu bul
for /f "tokens=*" %%a in ('powershell -Command "(Get-WmiObject Win32_PnPEntity | Where-Object { $_.Name -match 'CH340|CP210' } | ForEach-Object { if ($_.Name -match 'COM(\d+)') { 'COM' + $Matches[1] } })" 2^>nul') do (
    set "CH340_PORT=%%a"
    set /a PORT_COUNT+=1
)

if "%CH340_PORT%"=="" (
    echo.
    echo  [HATA] ESP8266 bulunamadi!
    echo.
    echo  Kontrol edin:
    echo    - USB kablo takili mi?
    echo    - Kablo VERI destekli mi? (sarj kablosu olmaz^)
    echo    - CH340 driver yuklendi mi?
    echo    - Aygit Yoneticisi'nde COM portu goruyor musunuz?
    echo.
    pause
    exit /b 1
)

if %PORT_COUNT% GTR 1 (
    echo  [UYARI] Birden fazla ESP8266 bulundu. Ilki kullanilacak: %CH340_PORT%
) else (
    echo  [OK] ESP8266 bulundu: %CH340_PORT%
)

:: ------------------------------------------------------------------
:: 3. Firmware tipi secimi
:: ------------------------------------------------------------------
set "FW_TYPE=%~1"

if "%FW_TYPE%"=="" (
    echo.
    echo  ┌─────────────────────────────────────────────┐
    echo  │  Firmware tipi secin:                        │
    echo  │                                              │
    echo  │  1. SENSOR  - Sadece BME280 sicaklik/nem     │
    echo  │               (oda sensoru olarak kullanilir) │
    echo  │                                              │
    echo  │  2. COMBO   - Sensor + Role kontrolu          │
    echo  │               (kombi odasina kurulur)         │
    echo  │                                              │
    echo  │  3. RELAY   - Sadece role kontrolu             │
    echo  │               (ayri sensor ile kullanilir)    │
    echo  └─────────────────────────────────────────────┘
    echo.
    set /p "FW_CHOICE=  Seciminiz (1/2/3): "

    if "!FW_CHOICE!"=="1" set "FW_TYPE=sensor"
    if "!FW_CHOICE!"=="2" set "FW_TYPE=combo"
    if "!FW_CHOICE!"=="3" set "FW_TYPE=relay"
)

if "%FW_TYPE%"=="" (
    echo  [HATA] Gecersiz secim!
    pause
    exit /b 1
)

:: Firmware dizinini ayarla
set "FW_DIR=%~dp0..\%FW_TYPE%"
if not exist "%FW_DIR%\platformio.ini" (
    echo  [HATA] %FW_TYPE% firmware bulunamadi: %FW_DIR%
    pause
    exit /b 1
)

echo.
echo  [INFO] Firmware: %FW_TYPE%
echo  [INFO] Port:     %CH340_PORT%
echo  [INFO] Dizin:    %FW_DIR%
echo.

:: ------------------------------------------------------------------
:: 4. Derleme
:: ------------------------------------------------------------------
echo  ══════════════════════════════════════════════════
echo   ADIM 1/3: Derleniyor...
echo  ══════════════════════════════════════════════════
echo.

cd /d "%FW_DIR%"
pio run -e esp8266-serial 2>&1

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo  [HATA] Derleme basarisiz!
    pause
    exit /b 1
)

echo.
echo  [OK] Derleme basarili
echo.

:: ------------------------------------------------------------------
:: 5. Flash
:: ------------------------------------------------------------------
echo  ══════════════════════════════════════════════════
echo   ADIM 2/3: Flash yukleniyor (%CH340_PORT%)...
echo  ══════════════════════════════════════════════════
echo.

pio run -e esp8266-serial -t upload --upload-port %CH340_PORT% 2>&1

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo  [HATA] Flash basarisiz!
    echo.
    echo  Deneyin:
    echo    - ESP8266 uzerindeki FLASH/BOOT tusuna basili tutup RESET'e basin
    echo    - USB kabloyu degistirin
    echo    - COM portunu kapatan uygulamalari kapatin (Serial Monitor vb.)
    echo.
    pause
    exit /b 1
)

echo.
echo  [OK] Flash basarili!
echo.

:: ------------------------------------------------------------------
:: 6. Dogrulama (Serial Monitor 15 saniye)
:: ------------------------------------------------------------------
echo  ══════════════════════════════════════════════════
echo   ADIM 3/3: Cihaz dogrulamasi (15 saniye)...
echo  ══════════════════════════════════════════════════
echo.
echo  Serial ciktisi izleniyor. Cihaz boot ediyor...
echo  (WiFi AP modu acilacak, BME280 kontrol edilecek)
echo.

:: 15 saniye serial monitor, timeout ile
powershell -Command "$port = New-Object System.IO.Ports.SerialPort('%CH340_PORT%', 115200); $port.ReadTimeout = 1000; $port.Open(); $end = (Get-Date).AddSeconds(15); $ok = $false; while ((Get-Date) -lt $end) { try { $line = $port.ReadLine(); Write-Host ('  ' + $line); if ($line -match 'WiFi|AP mode|BME280|MQTT|COMBO|SENSOR|RELAY') { $ok = $true } } catch {} }; $port.Close(); if ($ok) { Write-Host ''; Write-Host '  [OK] Cihaz basariyla boot etti!' -ForegroundColor Green } else { Write-Host ''; Write-Host '  [UYARI] Boot ciktisi yakalanamiyor, cihazi kontrol edin' -ForegroundColor Yellow }" 2>nul

echo.
echo  ══════════════════════════════════════════════════
echo   TAMAMLANDI!
echo  ══════════════════════════════════════════════════
echo.
echo  Sonraki adimlar:
echo.

if /i "%FW_TYPE%"=="sensor" (
    echo    1. ESP8266'yi USB'den cikarin
    echo    2. BME280 sensoru baglayin:
    echo         SDA = D2 (GPIO4)
    echo         SCL = D1 (GPIO5)
    echo         VCC = 3V3
    echo         GND = GND
    echo    3. Guc verin (5V USB veya UPS modul)
    echo    4. Telefondan "TonbilSensor-XXXX" WiFi agina baglanin
    echo    5. 192.168.4.1 adresinde:
    echo         - Ev WiFi bilgilerinizi girin
    echo         - MQTT Server: 192.168.1.9
    echo         - MQTT Port: 1883
    echo    6. Kaydedin, cihaz otomatik baglanacak
    echo    7. Panelde (http://192.168.1.9:8091) yeni sensor gorunecek
)

if /i "%FW_TYPE%"=="combo" (
    echo    1. ESP8266'yi USB'den cikarin
    echo    2. BME280 sensoru baglayin:
    echo         SDA = D2 (GPIO4), SCL = D1 (GPIO5)
    echo         VCC = 3V3, GND = GND
    echo    3. Role modulu baglayin:
    echo         IN1 = D5 (GPIO14)
    echo         VCC = VIN (5V), GND = GND
    echo    4. Guc verin
    echo    5. Telefondan "TonbilCombo-XXXX" WiFi agina baglanin
    echo    6. 192.168.4.1 adresinde:
    echo         - Ev WiFi bilgilerinizi girin
    echo         - MQTT Server: 192.168.1.9
    echo         - MQTT Port: 1883
    echo    7. Panelde otomatik gorunecek
)

if /i "%FW_TYPE%"=="relay" (
    echo    1. ESP8266'yi USB'den cikarin
    echo    2. Role modulu baglayin:
    echo         IN1 = D5 (GPIO14)
    echo         VCC = VIN (5V), GND = GND
    echo    3. Guc verin
    echo    4. Telefondan "TonbilRelay-XXXX" WiFi agina baglanin
    echo    5. 192.168.4.1 adresinde:
    echo         - Ev WiFi bilgilerinizi girin
    echo         - MQTT Server: 192.168.1.9
    echo         - MQTT Port: 1883
    echo    6. Panelde otomatik gorunecek
)

echo.
echo  Pin Semasi:
echo    ┌──────────────────────────┐
echo    │  NodeMCU v3 (ESP8266)    │
echo    │                          │
echo    │  D2 (GPIO4)  ── BME SDA  │
echo    │  D1 (GPIO5)  ── BME SCL  │
echo    │  3V3         ── BME VCC  │
echo    │  D5 (GPIO14) ── Relay IN │
echo    │  VIN (5V)    ── Relay VCC│
echo    │  GND         ── Ortak GND│
echo    └──────────────────────────┘
echo.

pause
exit /b 0
