#include <Arduino.h>
#include <Wire.h>

void setup() {
    Serial.begin(115200);
    delay(2000);
    Wire.begin(4, 5); // D2=SDA, D1=SCL
    Serial.println("\n=== I2C Scanner (D2=SDA, D1=SCL) ===");
}

void loop() {
    byte count = 0;
    Serial.println("Taraniyor...");
    for (byte addr = 1; addr < 127; addr++) {
        Wire.beginTransmission(addr);
        if (Wire.endTransmission() == 0) {
            Serial.print("  Cihaz bulundu: 0x");
            if (addr < 16) Serial.print("0");
            Serial.println(addr, HEX);
            count++;
        }
    }
    if (count == 0) Serial.println("  HICBIR I2C CIHAZ BULUNAMADI!");
    else { Serial.print("  Toplam: "); Serial.println(count); }
    Serial.println("---");
    delay(5000);
}
