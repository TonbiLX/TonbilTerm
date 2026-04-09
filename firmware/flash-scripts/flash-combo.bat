@echo off
chcp 65001 >nul 2>nul
setlocal enabledelayedexpansion

:: TonbilTerm - Combo Firmware Flash Script
:: Bilgisayara ESP32 takin ve bu scripti calistirin

echo.
echo ===================================
echo  TonbilTerm Combo Firmware Yukleyici
echo  (Sensor + Relay Birlesik)
echo ===================================
echo.

:: ----------------------------------
:: PlatformIO Kontrolu
:: ----------------------------------
where pio >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [HATA] PlatformIO bulunamadi!
    echo.
    echo Cozum:
    echo   1. once setup-platformio.bat calistirin
    echo   2. veya manuel: pip install platformio
    echo.
    pause
    exit /b 1
)
echo [OK] PlatformIO bulundu.

:: ----------------------------------
:: ESP32 COM Port Tespiti
:: ----------------------------------
echo.
echo ESP32 cihazi araniyor...

set PORT=
set PORT_COUNT=0

for /f "tokens=*" %%p in ('pio device list --serial 2^>nul ^| findstr /i /r "^COM[0-9]"') do (
    set /a PORT_COUNT+=1
    for /f "tokens=1" %%q in ("%%p") do (
        set "LAST_PORT=%%q"
    )
)

:: Hic port bulunamazsa
if %PORT_COUNT% EQU 0 (
    echo.
    echo [HATA] ESP32 bulunamadi!
    echo.
    echo Kontrol edin:
    echo   - USB kablo takili mi?
    echo   - Kablo veri destekli mi? (sadece sarj kablosu olmasin)
    echo   - ESP32 driver yuklendi mi? (CP2102 veya CH340)
    echo   - Aygit Yoneticisi'nde COM portu goruyor musunuz?
    echo.
    pause
    exit /b 1
)

:: Birden fazla port varsa uyar
if %PORT_COUNT% GTR 1 (
    echo.
    echo [UYARI] Birden fazla seri cihaz bulundu (%PORT_COUNT% adet).
    echo Bulunan portlar:
    pio device list --serial 2>nul | findstr /i /r "^COM[0-9]"
    echo.
    echo Son bulunan port kullanilacak: %LAST_PORT%
    echo.
    set /p "CONFIRM=Devam etmek istiyor musunuz? (E/H): "
    if /i "!CONFIRM!" NEQ "E" (
        echo Iptal edildi.
        pause
        exit /b 0
    )
)

set PORT=%LAST_PORT%
echo [OK] ESP32 bulundu: %PORT%

:: ----------------------------------
:: Derleme ve Yukleme
:: ----------------------------------
echo.
echo Combo firmware derleniyor ve yukleniyor...
echo Bu islem 1-3 dakika surebilir, lutfen bekleyin...
echo.

cd /d "%~dp0..\combo"
if not exist "platformio.ini" (
    echo [HATA] platformio.ini bulunamadi!
    echo Combo firmware kaynak dosyalari eksik.
    echo Beklenen konum: %~dp0..\combo\
    pause
    exit /b 1
)

pio run --target upload --upload-port %PORT%

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===================================
    echo  BASARILI! Combo firmware yuklendi
    echo ===================================
    echo.
    echo Sonraki adimlar:
    echo   1. ESP32'yi USB'den cikarin
    echo   2. BME280 sensor ve role modulune yerlestirin
    echo   3. Guc verin
    echo   4. WiFi ayari icin "TonbilCombo-XXXX" agina baglanin
    echo   5. Tarayicida 192.168.4.1 adresine gidin
    echo.
) else (
    echo.
    echo [HATA] Yukleme basarisiz!
    echo.
    echo Olasi sebepler:
    echo   - ESP32 BOOT modunda degil (BOOT tusuna basili tutun)
    echo   - COM portu baska uygulama tarafindan kullaniliyor
    echo   - USB kablo arizali
    echo   - Driver problemi
    echo.
    echo Hata kodu: %ERRORLEVEL%
)

pause
exit /b %ERRORLEVEL%
