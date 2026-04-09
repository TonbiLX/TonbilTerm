"""MQTT subscriber - ESP32 cihazlardan gelen telemetri ve durum mesajlarini isle."""

import asyncio
import json
import logging
from datetime import datetime, timezone

import paho.mqtt.client as mqtt
from influxdb_client import InfluxDBClient, Point
from influxdb_client.client.write_api import SYNCHRONOUS
from sqlalchemy import select, update

from app.config import settings
from app.models.database import async_session, Device
from app.websocket.manager import ws_manager

logger = logging.getLogger("tonbil.mqtt")

# MQTT topic pattern'leri
TOPIC_TELEMETRY = "tonbil/devices/+/telemetry"
TOPIC_STATUS = "tonbil/devices/+/status"

# Reconnect backoff (saniye)
RECONNECT_MIN_DELAY = 5
RECONNECT_MAX_DELAY = 60


class MQTTService:
    """MQTT baglantisi ve mesaj isleme servisi."""

    def __init__(self):
        self._client = mqtt.Client(
            client_id="tonbil-api-server",
            protocol=mqtt.MQTTv5,
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        )
        self._client.username_pw_set(settings.mqtt_user, settings.mqtt_password)
        self._client.on_connect = self._on_connect
        self._client.on_message = self._on_message
        self._client.on_disconnect = self._on_disconnect
        # paho reconnect backoff (otomatik reconnect icin)
        self._client.reconnect_delay_set(
            min_delay=RECONNECT_MIN_DELAY,
            max_delay=RECONNECT_MAX_DELAY,
        )

        self._loop: asyncio.AbstractEventLoop | None = None
        self._running = False
        self._connected = False

        # InfluxDB client
        self._influx = InfluxDBClient(
            url=settings.influxdb_url,
            token=settings.influxdb_token,
            org=settings.influxdb_org,
        )
        self._influx_write = self._influx.write_api(write_options=SYNCHRONOUS)

        # Son role durumu
        self.relay_state: bool = False
        self._last_relay_change: datetime | None = None

    async def start(self):
        """MQTT baglantisini baslat ve mesajlari dinle."""
        self._loop = asyncio.get_running_loop()
        self._running = True

        logger.info("MQTT baglantisi kuruluyor: %s:%d", settings.mqtt_host, settings.mqtt_port)

        try:
            self._client.connect(settings.mqtt_host, settings.mqtt_port, keepalive=60)
            # loop_start() ayri thread'de network loop calistirir
            # Otomatik reconnect destegi vardir
            self._client.loop_start()

            # Task iptal edilene kadar bekle (30 saniye araliklarla)
            while self._running:
                await asyncio.sleep(30)
        except asyncio.CancelledError:
            logger.info("MQTT task iptal edildi")
        except Exception as e:
            logger.error("MQTT baglanti hatasi: %s", e)
            # Reconnect denemesi
            retry_delay = RECONNECT_MIN_DELAY
            while self._running:
                logger.info("MQTT yeniden baglanma denemesi (%d saniye sonra)...", retry_delay)
                await asyncio.sleep(retry_delay)
                try:
                    self._client.connect(settings.mqtt_host, settings.mqtt_port, keepalive=60)
                    self._client.loop_start()
                    while self._running:
                        await asyncio.sleep(30)
                    break
                except asyncio.CancelledError:
                    break
                except Exception as retry_err:
                    logger.error("MQTT reconnect basarisiz: %s", retry_err)
                    retry_delay = min(retry_delay * 2, RECONNECT_MAX_DELAY)

    def stop(self):
        """MQTT baglantisini kapat."""
        self._running = False
        self._client.loop_stop()
        self._client.disconnect()
        self._influx.close()
        logger.info("MQTT servisi durduruldu")

    def _on_connect(self, client, userdata, flags, reason_code, properties=None):
        """Baglanti kurulunca topic'lere subscribe ol."""
        # MQTTv5: reason_code bir ReasonCode nesnesi, int karsilastirmasi icin .value kullan
        rc = reason_code.value if hasattr(reason_code, "value") else int(reason_code)
        if rc == 0:
            self._connected = True
            logger.info("MQTT broker'a baglandi")
            client.subscribe(TOPIC_TELEMETRY, qos=1)
            client.subscribe(TOPIC_STATUS, qos=1)
            logger.info("Topic'lere subscribe olundu: %s, %s", TOPIC_TELEMETRY, TOPIC_STATUS)
        else:
            logger.error("MQTT baglanti hatasi, reason_code: %s", reason_code)

    def _on_disconnect(self, client, userdata, flags, reason_code, properties=None):
        """Baglanti kopunca logla. paho loop_start() otomatik reconnect yapar."""
        self._connected = False
        rc = reason_code.value if hasattr(reason_code, "value") else int(reason_code)
        if rc != 0:
            logger.warning(
                "MQTT baglantisi koptu (reason_code: %s). Otomatik reconnect denenecek.",
                reason_code,
            )
        else:
            logger.info("MQTT baglantisi temiz kapatildi")

    def _on_message(self, client, userdata, msg: mqtt.MQTTMessage):
        """Gelen mesaji isle (sync callback, async task olustur)."""
        try:
            topic_parts = msg.topic.split("/")
            if len(topic_parts) < 4:
                return

            device_id = topic_parts[2]
            message_type = topic_parts[3]
            raw = msg.payload.decode("utf-8").strip()

            if message_type == "status":
                status_payload = {"status": raw} if raw in ("online", "offline") else json.loads(raw)
                if self._loop and self._loop.is_running():
                    asyncio.run_coroutine_threadsafe(
                        self._handle_status(device_id, status_payload), self._loop
                    )
            elif message_type == "telemetry":
                payload = json.loads(raw)
                # InfluxDB'ye SENKRON yaz (paho thread'inde, blocking OK)
                self._write_influx_sync(device_id, payload)
                # Async isleri (DB update, WS broadcast, heating eval) event loop'a gonder
                if self._loop and self._loop.is_running():
                    asyncio.run_coroutine_threadsafe(
                        self._handle_telemetry(device_id, payload), self._loop
                    )
        except json.JSONDecodeError:
            logger.warning("Gecersiz JSON mesaji topic=%s", msg.topic)
        except Exception as e:
            logger.error("Mesaj isleme hatasi: %s", e)

    def _write_influx_sync(self, device_id: str, payload: dict):
        """InfluxDB'ye senkron yaz (paho thread'inden cagirilir)."""
        try:
            temperature = payload.get("temperature") or payload.get("temp")
            humidity = payload.get("humidity") or payload.get("hum")
            pressure = payload.get("pressure") or payload.get("pres")

            point = Point("sensor_data").tag("device_id", device_id).time(datetime.now(timezone.utc))
            if temperature is not None:
                point = point.field("temperature", float(temperature))
            if humidity is not None:
                point = point.field("humidity", float(humidity))
            if pressure is not None:
                point = point.field("pressure", float(pressure))

            self._influx_write.write(
                bucket=settings.influxdb_bucket,
                org=settings.influxdb_org,
                record=point,
            )
        except Exception as e:
            logger.error("InfluxDB sync yazma hatasi: %s", e)

    async def _handle_telemetry(self, device_id: str, payload: dict):
        """Sensor telemetri verisini isle.

        Beklenen payload:
        {
            "temperature": 22.5,
            "humidity": 45.2,
            "battery": 3.7,
            "rssi": -65
        }
        """
        logger.debug("Telemetri alindi: device=%s, data=%s", device_id, payload)

        temperature = payload.get("temperature") or payload.get("temp")
        humidity = payload.get("humidity") or payload.get("hum")
        pressure = payload.get("pressure") or payload.get("pres")
        battery = payload.get("battery")
        rssi = payload.get("rssi")

        # InfluxDB yazma artik _write_influx_sync'de (paho thread, blocking OK)

        # PostgreSQL - device last_seen guncelle veya yeni cihaz ekle
        try:
            async with async_session() as session:
                result = await session.execute(
                    update(Device)
                    .where(Device.device_id == device_id)
                    .values(
                        last_seen=datetime.now(timezone.utc),
                        is_online=True,
                        firmware_version=payload.get("firmware", None) or Device.firmware_version,
                    )
                )
                if result.rowcount == 0:
                    # Yeni cihaz — otomatik kaydet
                    device_type = payload.get("type", "sensor")
                    new_device = Device(
                        device_id=device_id,
                        name=device_id,
                        type=device_type,
                        is_online=True,
                        last_seen=datetime.now(timezone.utc),
                        firmware_version=payload.get("firmware", "1.0.0"),
                    )
                    session.add(new_device)
                    logger.info("Yeni cihaz otomatik kaydedildi: %s (tip: %s)", device_id, device_type)
                await session.commit()
        except Exception as e:
            logger.error("Device kayit/guncelleme hatasi: %s", e)

        # Relay/combo telemetrisinden boost ve relay durumunu isle
        relay_state = payload.get("relay")
        boost_active = payload.get("boost", False)
        boost_remaining = payload.get("boostRemaining", 0)
        boost_total = payload.get("boostTotal", 0)
        device_type = payload.get("type", "sensor")
        local_fallback = payload.get("localFallback", False)

        # Relay durumunu guncelle
        if relay_state is not None:
            self.relay_state = bool(relay_state)

        # WebSocket ile panel'e gonder
        ws_data = {
            "type": "sensor",
            "data": {
                "device_id": device_id,
                "deviceId": device_id,
                "temp": temperature,
                "hum": humidity,
                "pres": pressure,
                "temperature": temperature,
                "humidity": humidity,
                "pressure": pressure,
                "battery": battery,
                "rssi": rssi,
                "timestamp": datetime.now(timezone.utc).isoformat(),
            },
        }

        # Relay/combo cihazlarda ek bilgiler (sadece relay + fallback)
        # mode/target ESP kaynakli oldugundan GONDERILMEZ
        if device_type in ("relay", "combo"):
            ws_data["data"]["relay"] = relay_state
            ws_data["data"]["localFallback"] = local_fallback

        # Boost bilgisi varsa ekle
        if boost_active:
            ws_data["data"]["boost"] = True
            ws_data["data"]["boostRemaining"] = boost_remaining
            ws_data["data"]["boostTotal"] = boost_total

            # Ayrica boost_update mesaji da gonder
            await ws_manager.broadcast({
                "type": "boost_update",
                "data": {
                    "active": True,
                    "remaining_minutes": boost_remaining,
                    "total_minutes": boost_total,
                    "device_id": device_id,
                },
            })

        await ws_manager.broadcast(ws_data)

        # Relay/combo icin ayrica boiler status mesaji gonder
        # NOT: mode ve target ESP'den gelir ama sunucu DB'si ile uyumsuz olabilir.
        # Bu yuzden mode/target GONDERILMEZ — sadece relay + boost + fallback.
        # Mode/target sadece config_update mesajiyla (config.py PUT) gonderilir.
        if device_type in ("relay", "combo") and relay_state is not None:
            await ws_manager.broadcast({
                "type": "boiler",
                "data": {
                    "active": bool(relay_state),
                    "relay": bool(relay_state),
                    "boost": boost_active,
                    "boostRemaining": boost_remaining,
                    "boostTotal": boost_total,
                    "localFallback": local_fallback,
                    "runtimeToday": 0,
                },
            })

        # Sicaklik esik kontrolu — FCM push notification
        await self._check_temp_thresholds(device_id, temperature)

        # Isitma algoritmasi degerlendirmesi
        from app.services.heating import heating_service
        await heating_service.on_telemetry(device_id, payload)

    async def _handle_status(self, device_id: str, payload: dict):
        """Cihaz durum mesajini isle.

        Beklenen payload:
        {
            "online": true,
            "firmware": "1.0.3",
            "ip": "192.168.1.42"
        }
        """
        logger.info("Durum mesaji: device=%s, data=%s", device_id, payload)

        # LWT sends {"status": "offline"}, firmware sends {"status": "online"} or {"online": true}
        status_val = payload.get("status")
        if status_val is not None:
            is_online = status_val == "online"
        else:
            is_online = payload.get("online", True)
        firmware = payload.get("firmware")
        ip_address = payload.get("ip")

        try:
            async with async_session() as session:
                update_values: dict = {
                    "is_online": is_online,
                    "last_seen": datetime.now(timezone.utc),
                }
                if firmware:
                    update_values["firmware_version"] = firmware
                if ip_address:
                    update_values["ip_address"] = ip_address

                result = await session.execute(
                    update(Device)
                    .where(Device.device_id == device_id)
                    .values(**update_values)
                )

                # Cihaz DB'de yoksa otomatik kaydet
                if result.rowcount == 0:
                    device = Device(
                        device_id=device_id,
                        name=f"Cihaz {device_id[:8]}",
                        type="sensor",
                        is_online=is_online,
                        firmware_version=firmware,
                        ip_address=ip_address,
                        last_seen=datetime.now(timezone.utc),
                    )
                    session.add(device)
                    logger.info("Yeni cihaz otomatik kaydedildi: %s", device_id)

                await session.commit()
        except Exception as e:
            logger.error("Device durum guncelleme hatasi: %s", e)

        # Sensor offline FCM bildirimi
        if not is_online:
            try:
                from app.services.fcm import fcm_service
                await fcm_service.notify_sensor_offline(device_id)
            except Exception as e:
                logger.debug("FCM sensor_offline bildirimi gonderilemedi: %s", e)

        # WebSocket bildirimi
        await ws_manager.broadcast({
            "type": "device_status",
            "data": {
                "device_id": device_id,
                "is_online": is_online,
            },
        })

    async def _check_temp_thresholds(self, device_id: str, temperature: float | None) -> None:
        """Sicaklik esik degerlerini kontrol et, asildiysa FCM bildirimi gonder.

        Her kullanicinin kendi esik degerleri vardir (notification_preferences).
        Esik yoksa varsayilan degerler kullanilir (16C alt, 28C ust).
        """
        if temperature is None:
            return

        try:
            from app.services.fcm import fcm_service

            if not fcm_service.enabled:
                return

            # Varsayilan esikler (tercih olmayan kullanicilar icin)
            default_low = 16.0
            default_high = 28.0

            if temperature < default_low:
                await fcm_service.notify_temp_alert(
                    device_id=device_id,
                    temp=temperature,
                    threshold=default_low,
                    alert_type="temp_low",
                )
            elif temperature > default_high:
                await fcm_service.notify_temp_alert(
                    device_id=device_id,
                    temp=temperature,
                    threshold=default_high,
                    alert_type="temp_high",
                )
        except Exception as e:
            logger.debug("FCM sicaklik esik kontrolu hatasi: %s", e)

    def publish_command(self, device_id: str, command: str, payload: dict | None = None):
        """Cihaza MQTT komutu gonder.

        ESP8266 firmware beklenen format:
          relay_on  -> {"cmd": "setRelay", "value": true}
          relay_off -> {"cmd": "setRelay", "value": false}
          boost     -> {"cmd": "boost", "minutes": N}
        """
        topic = f"tonbil/devices/{device_id}/command"

        # Komutu ESP8266 firmware formatina cevir
        if command == "relay_on":
            message = {"cmd": "setRelay", "value": True}
        elif command == "relay_off":
            message = {"cmd": "setRelay", "value": False}
        elif command == "boost":
            minutes = (payload or {}).get("minutes", 30)
            message = {"cmd": "boost", "minutes": minutes}
        elif command == "boost_cancel":
            message = {"cmd": "boostCancel"}
        elif command == "reboot":
            message = {"cmd": "reboot"}
        elif command == "config_update":
            message = {"cmd": "config", **(payload or {})}
        else:
            message = {"cmd": command, **(payload or {})}

        self._client.publish(topic, json.dumps(message), qos=1)
        logger.info("Komut gonderildi: device=%s, cmd=%s", device_id, message.get("cmd", command))

    def _record_relay_event(self, state: bool, source: str = "command"):
        """Relay ON/OFF olayini InfluxDB'ye kaydet (enerji takibi).

        Her geciste state (0/1) ve OFF gecislerinde onceki ON suresi yazilir.
        """
        now = datetime.now(timezone.utc)
        try:
            point = (
                Point("relay_state")
                .tag("source", source)
                .tag("state", "on" if state else "off")
                .field("state", 1 if state else 0)
                .time(now)
            )

            # OFF gecisinde sureklilik hesapla
            if not state and self._last_relay_change is not None:
                duration = (now - self._last_relay_change).total_seconds()
                point = point.field("duration_sec", round(duration, 1))

            self._influx_write.write(
                bucket=settings.influxdb_bucket,
                org=settings.influxdb_org,
                record=point,
            )
        except Exception as e:
            logger.warning("Relay event InfluxDB yazma hatasi: %s", e)

    def publish_relay(self, state: bool):
        """Tum relay/combo cihazlarina komut gonder.

        Raises:
            ConnectionError: MQTT broker'a bagli degilse.
        """
        if not self._connected:
            logger.error("MQTT bagli degil, relay komutu gonderilemedi (state=%s)", state)
            raise ConnectionError("MQTT broker bagli degil")

        # ESP8266 firmware formatinda komut
        message = {"cmd": "setRelay", "value": state}
        msg_json = json.dumps(message)

        # Tum relay ve combo cihazlara gonder
        try:
            from app.models.database import Device, async_session
            import asyncio
            loop = self._loop

            async def _send():
                async with async_session() as session:
                    from sqlalchemy import select
                    result = await session.execute(
                        select(Device).where(Device.type.in_(["relay", "combo"]))
                    )
                    devices = result.scalars().all()
                    if not devices:
                        logger.warning("Relay/combo cihaz bulunamadi, komut gonderilemedi")
                        return
                    for dev in devices:
                        topic = f"tonbil/devices/{dev.device_id}/command"
                        self._client.publish(topic, msg_json, qos=1)
                        logger.info("Relay komutu gonderildi: device=%s, state=%s", dev.device_id, state)

            if loop and loop.is_running():
                asyncio.run_coroutine_threadsafe(_send(), loop)
            else:
                logger.error("Event loop hazir degil, relay komutu gonderilemedi")
                raise ConnectionError("Event loop hazir degil")
        except ConnectionError:
            raise
        except Exception as e:
            logger.error("Relay komut gonderim hatasi: %s", e)
            raise ConnectionError(f"Relay komutu gonderilemedi: {e}")

        # NOT: InfluxDB kaydı heating_service._record_relay_to_influx tarafından yapılıyor
        # Burada tekrar yazmıyoruz (çift yazma önleme)

        self.relay_state = state
        self._last_relay_change = datetime.now(timezone.utc)
        logger.info("Relay komutu: %s", "ACIK" if state else "KAPALI")

        # Heating service relay state'ini de senkronize et
        try:
            from app.services.heating import heating_service
            heating_service._relay_state = state
        except Exception:
            pass

        # FCM relay change bildirimi
        try:
            from app.services.fcm import fcm_service
            if self._loop and self._loop.is_running():
                asyncio.run_coroutine_threadsafe(
                    fcm_service.notify_relay_change(state), self._loop
                )
        except Exception as e:
            logger.debug("FCM relay_change bildirimi gonderilemedi: %s", e)

    def publish_command_to_relays(self, command: str, payload: dict):
        """Tum relay/combo cihazlara komut gonder (boost, config vb.)."""
        # ESP8266 firmware formatinda komut olustur
        if command == "boost":
            message = {"cmd": "boost", "minutes": payload.get("minutes", 30)}
        elif command == "boost_cancel":
            message = {"cmd": "boostCancel"}
        else:
            message = {"cmd": command, **payload}

        msg_json = json.dumps(message)

        # Bilinen relay/combo cihazlara gonder
        try:
            from app.models.database import Device, async_session
            import asyncio
            loop = self._loop

            async def _send():
                async with async_session() as session:
                    from sqlalchemy import select
                    result = await session.execute(
                        select(Device).where(Device.type.in_(["relay", "combo"]))
                    )
                    for dev in result.scalars().all():
                        topic = f"tonbil/devices/{dev.device_id}/command"
                        self._client.publish(topic, msg_json, qos=1)
                        logger.info("Komut gonderildi: device=%s, cmd=%s", dev.device_id, command)

            if loop and loop.is_running():
                asyncio.run_coroutine_threadsafe(_send(), loop)
        except Exception as e:
            logger.error("Relay broadcast hatasi: %s", e)
