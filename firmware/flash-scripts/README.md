# TonbilTerm - Firmware Yukleme Kilavuzu

ESP32 tabanli termostat firmware'ini derlemek ve yuklemek icin gereken scriptler.

## On Gereksinimler

1. **Python 3.8+** - [python.org](https://www.python.org/downloads/)
   - Windows: Kurulumda "Add Python to PATH" secenegini isaretleyin
2. **PlatformIO CLI** - Otomatik kurulum scripti mevcuttur
3. **USB Kablo** - Veri destekli USB kablo (sadece sarj kablosu OLMAMALI)
4. **ESP32 Driver** - CP2102 veya CH340 (genellikle otomatik yuklenir)

## Kurulum

### Windows

```
setup-platformio.bat
```

### Linux / macOS

```bash
chmod +x *.sh
./setup-platformio.sh
```

Linux kullanicilari icin ek adim:
```bash
sudo usermod -aG dialout $USER
# Logout yapip tekrar login olun
```

## Firmware Yukleme

### Sensor Firmware

Sicaklik sensoru modulu icin:

| Platform | Komut |
|----------|-------|
| Windows  | `flash-sensor.bat` |
| Linux/Mac | `./flash-sensor.sh` |

Basarili yuklemeden sonra:
1. ESP32'yi cikarin ve sensor modulune yerlestirin
2. Guc verin
3. Telefonunuzdan "TonbilSensor-XXXX" WiFi agina baglanin
4. Tarayicida `192.168.4.1` adresine gidin
5. Ev WiFi bilgilerinizi girin

### Relay (Role) Firmware

Role kontrol modulu icin:

| Platform | Komut |
|----------|-------|
| Windows  | `flash-relay.bat` |
| Linux/Mac | `./flash-relay.sh` |

Basarili yuklemeden sonra:
1. ESP32'yi cikarin ve role modulune yerlestirin
2. Guc verin
3. Telefonunuzdan "TonbilRelay-XXXX" WiFi agina baglanin
4. Tarayicida `192.168.4.1` adresine gidin
5. Ev WiFi bilgilerinizi girin

## Seri Monitor (Debug)

ESP32'den gelen debug mesajlarini gormek icin:

| Platform | Komut |
|----------|-------|
| Windows  | `monitor.bat` |
| Linux/Mac | `./monitor.sh` |

Cikmak icin: `Ctrl+C`

## Sorun Giderme

### ESP32 bulunamadi

- USB kabloyu kontrol edin (veri destekli olmali, sadece sarj kablosu calismaz)
- Farkli USB porta takin
- Driver kurulumunu kontrol edin:
  - CP2102: [silabs.com](https://www.silabs.com/developers/usb-to-uart-bridge-vcp-drivers)
  - CH340: [wch-ic.com](http://www.wch-ic.com/downloads/CH341SER_ZIP.html)
- Windows: Aygit Yoneticisi > Baglantilar (COM ve LPT) altinda gorunmeli

### Yukleme basarisiz

- ESP32 uzerindeki **BOOT** tusuna basili tutarak tekrar deneyin
- Seri monitoru veya baska terminal programini kapatin (port mesgul olabilir)
- USB kabloyu degistirmeyi deneyin
- ESP32'yi resetleyin (EN/RST tusuna basin)

### PlatformIO bulunamadi

- `setup-platformio.bat` (veya `.sh`) scriptini calistirin
- Terminal'i kapatip tekrar acin (PATH guncellenmesi gerekebilir)
- Manuel: `pip install platformio`

### Linux: Erisim izni hatasi

```bash
sudo usermod -aG dialout $USER
# Logout yapip tekrar login olun
```

### Derleme hatasi

- Internet baglantinizi kontrol edin (ilk derlemede kutuphane indirmesi gerekir)
- `pio platform install espressif32` komutunu calistirin
- `.pio` klasorunu silip tekrar deneyin

## Dosya Yapisi

```
firmware/
  flash-scripts/          # Bu klasor - yukleme scriptleri
    flash-sensor.bat/sh   # Sensor firmware yukle
    flash-relay.bat/sh    # Relay firmware yukle
    monitor.bat/sh        # Seri monitor
    setup-platformio.bat/sh # PlatformIO kurulumu
  sensor/                 # Sensor firmware kaynak kodu
    platformio.ini
    src/
  relay/                  # Relay firmware kaynak kodu
    platformio.ini
    src/
```
