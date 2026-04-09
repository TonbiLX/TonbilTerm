@echo off
chcp 65001 >nul 2>nul
setlocal enabledelayedexpansion

:: TonbilTerm - Seri Monitor (Debug)
:: ESP32'den gelen seri verileri goruntuler

echo.
echo ===================================
echo  TonbilTerm Seri Monitor
echo ===================================
echo.

:: ----------------------------------
:: PlatformIO Kontrolu
:: ----------------------------------
where pio >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [HATA] PlatformIO bulunamadi!
    echo Cozum: once setup-platformio.bat calistirin
    pause
    exit /b 1
)

:: ----------------------------------
:: ESP32 COM Port Tespiti
:: ----------------------------------
echo ESP32 cihazi araniyor...

set PORT=
set PORT_COUNT=0

for /f "tokens=*" %%p in ('pio device list --serial 2^>nul ^| findstr /i /r "^COM[0-9]"') do (
    set /a PORT_COUNT+=1
    for /f "tokens=1" %%q in ("%%p") do (
        set "LAST_PORT=%%q"
    )
)

if %PORT_COUNT% EQU 0 (
    echo.
    echo [HATA] ESP32 bulunamadi! USB kablo takili mi?
    pause
    exit /b 1
)

if %PORT_COUNT% GTR 1 (
    echo.
    echo Bulunan portlar:
    pio device list --serial 2>nul | findstr /i /r "^COM[0-9]"
    echo.
    echo %LAST_PORT% kullanilacak.
    echo.
)

set PORT=%LAST_PORT%
echo [OK] ESP32 bulundu: %PORT%

:: ----------------------------------
:: Seri Monitor Baslat
:: ----------------------------------
echo.
echo Seri monitor baslatiliyor (115200 baud)...
echo Cikmak icin: Ctrl+C
echo.
echo ───────────────────────────────────
echo.

pio device monitor --port %PORT% --baud 115200

pause
