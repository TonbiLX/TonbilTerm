@echo off
chcp 65001 >nul 2>nul
setlocal

:: TonbilTerm - PlatformIO Kurulum Scripti
:: Bu script gerekli gelistirme araclarini kurar

echo.
echo ===================================
echo  TonbilTerm - PlatformIO Kurulumu
echo ===================================
echo.

:: ----------------------------------
:: Python Kontrolu
:: ----------------------------------
echo [1/4] Python kontrol ediliyor...

where python >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    where python3 >nul 2>nul
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo [HATA] Python bulunamadi!
        echo.
        echo Python'u indirin ve kurun:
        echo   https://www.python.org/downloads/
        echo.
        echo ONEMLI: Kurulum sirasinda "Add Python to PATH" secenegini isaretleyin!
        echo Kurulumdan sonra bu scripti tekrar calistirin.
        echo.
        pause
        exit /b 1
    )
    set PYTHON_CMD=python3
) else (
    set PYTHON_CMD=python
)

:: Python versiyonunu goster
for /f "tokens=*" %%v in ('%PYTHON_CMD% --version 2^>^&1') do echo [OK] %%v bulundu.

:: ----------------------------------
:: pip Kontrolu
:: ----------------------------------
echo.
echo [2/4] pip kontrol ediliyor...

%PYTHON_CMD% -m pip --version >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo pip bulunamadi, kuruluyor...
    %PYTHON_CMD% -m ensurepip --default-pip
    if %ERRORLEVEL% NEQ 0 (
        echo [HATA] pip kurulamadi!
        pause
        exit /b 1
    )
)
echo [OK] pip mevcut.

:: ----------------------------------
:: PlatformIO Kurulumu
:: ----------------------------------
echo.
echo [3/4] PlatformIO kuruluyor/guncelleniyor...

where pio >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo PlatformIO zaten kurulu, guncelleniyor...
    %PYTHON_CMD% -m pip install --upgrade platformio
) else (
    echo PlatformIO kuruluyor (bu biraz zaman alabilir)...
    %PYTHON_CMD% -m pip install platformio
)

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [HATA] PlatformIO kurulamadi!
    echo Manuel denemek icin: pip install platformio
    pause
    exit /b 1
)

:: pio'nun PATH'te oldugundan emin ol
where pio >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [UYARI] pio komutu PATH'te bulunamadi.
    echo Terminal'i kapatip tekrar acmayi deneyin.
    echo Hala calismiyorsa, Python Scripts klasorunu PATH'e ekleyin:
    for /f "tokens=*" %%s in ('%PYTHON_CMD% -c "import sysconfig; print(sysconfig.get_path(\"scripts\"))"') do (
        echo   %%s
    )
    echo.
)

:: ----------------------------------
:: ESP32 Platform Kurulumu
:: ----------------------------------
echo.
echo [4/4] ESP32 platformu kuruluyor...

pio platform install espressif32
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [UYARI] ESP32 platformu simdiden kurulamadi.
    echo Ilk firmware yuklemesinde otomatik kurulacaktir.
) else (
    echo [OK] ESP32 platformu kuruldu.
)

:: ----------------------------------
:: Ozet
:: ----------------------------------
echo.
echo ===================================
echo  Kurulum Tamamlandi!
echo ===================================
echo.
echo Simdi yapabilirsiniz:
echo   - Sensor firmware yuklemek: flash-sensor.bat
echo   - Relay firmware yuklemek:  flash-relay.bat
echo   - Seri monitor acmak:      monitor.bat
echo.

pause
