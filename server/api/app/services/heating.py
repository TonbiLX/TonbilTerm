"""Isitma kontrol servisi - sensor verilerini degerlendirip relay komutu uretir.

Onemli: Sadece MQTT telemetriye degil, config degisikliklerine ve
periyodik zamanlayiciya da tepki verir. InfluxDB'den son bilinen
sicakliklari okuyarak cihaz offline olsa bile karar verebilir.
"""

import asyncio
import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy import select, update

from app.algorithms.hysteresis import evaluate_heating
from app.config import settings
from app.models.database import Device, HeatingConfig, Room, Schedule, async_session
from app.websocket.manager import ws_manager

logger = logging.getLogger("tonbil.heating")


class HeatingService:
    """Merkezi isitma kontrol servisi.

    Tetikleme kaynaklari:
    1. MQTT telemetri gelince (on_telemetry)
    2. Config degisince (on_config_changed)
    3. Periyodik zamanlayici (her 30 saniye)
    """

    def __init__(self):
        self._temps: dict[str, float] = {}
        self._relay_state: bool = False
        self._last_relay_change: datetime | None = None
        self._mqtt_service = None
        self._daily_runtime_sec: float = 0.0
        self._daily_date: str = ""
        self._cycle_count_today: int = 0
        self._boost_active: bool = False
        self._periodic_task: asyncio.Task | None = None
        self._startup_time: datetime = datetime.now(timezone.utc)

    async def on_telemetry(self, device_id: str, payload: dict):
        """Sensor telemetrisi geldiginde cagirilir."""
        temperature = payload.get("temperature") or payload.get("temp")
        if temperature is None:
            return

        self._temps[device_id] = float(temperature)
        logger.debug("Telemetri alindi: %s = %.1f°C", device_id, float(temperature))

        try:
            await self._evaluate()
        except Exception as e:
            logger.error("Isitma degerlendirme hatasi: %s", e)

    async def on_config_changed(self):
        """Config degistiginde cagirilir (target_temp, mode vs.).

        InfluxDB'den son sicakliklari okuyup evaluate calistirir.
        Boylece kullanici panelden sicaklik ayarladiginda
        cihaz offline olsa bile relay karari verilir.
        """
        try:
            await self._load_temps_from_influx()
            await self._evaluate()
        except Exception as e:
            logger.error("Config degisiklik sonrasi degerlendirme hatasi: %s", e)

    def start_periodic_evaluation(self):
        """Periyodik degerlendirme zamanlayicisini baslat (API startup'ta cagirilir)."""
        if self._periodic_task is None or self._periodic_task.done():
            self._periodic_task = asyncio.create_task(self._periodic_loop())
            logger.info("Periyodik isitma degerlendirme zamanlayicisi baslatildi (30sn)")

    async def _periodic_loop(self):
        """Her 30 saniyede InfluxDB'den sicakliklari oku ve evaluate et."""
        logger.info("Periyodik loop basliyor... mqtt=%s", self._mqtt_service is not None)
        while True:
            try:
                await asyncio.sleep(30)
                logger.info("Periyodik dongu: temps yukle basliyor (mqtt=%s)", self._mqtt_service is not None)
                await self._mark_stale_devices_offline()
                await self._load_temps_from_influx()
                if self._temps:
                    logger.info("Periyodik eval: temps=%s", self._temps)
                    await self._evaluate()
                else:
                    logger.info("Periyodik eval: temps bos")
            except asyncio.CancelledError:
                logger.info("Periyodik loop iptal edildi")
                break
            except Exception as e:
                logger.error("Periyodik degerlendirme hatasi: %s (%s)", e, type(e).__name__, exc_info=True)

    async def _mark_stale_devices_offline(self):
        """Mark devices as offline if no telemetry for 2+ minutes."""
        # Grace period: startup sonrası ilk 60sn'de stale check yapma
        # ESP cihazlar 10sn'de telemetri gönderir, reconnect için zaman tanı
        if (datetime.now(timezone.utc) - self._startup_time).total_seconds() < 60:
            return
        try:
            cutoff = datetime.now(timezone.utc) - timedelta(minutes=2)
            async with async_session() as session:
                result = await session.execute(
                    update(Device)
                    .where(Device.is_online == True, Device.last_seen < cutoff)
                    .values(is_online=False)
                )
                if result.rowcount > 0:
                    logger.info("Marked %d stale device(s) as offline", result.rowcount)
                await session.commit()
        except Exception as e:
            logger.error("Stale device check error: %s", e)

    async def _load_temps_from_influx(self):
        """InfluxDB'den son 10dk icindeki sensor sicakliklarini oku."""
        mqtt_svc = getattr(self, "_mqtt_service", None)
        if mqtt_svc is None:
            logger.warning("_load_temps: mqtt_service None, InfluxDB okunamiyor")
            return

        if not hasattr(mqtt_svc, '_influx') or mqtt_svc._influx is None:
            logger.warning("_load_temps: mqtt_service._influx None")
            return

        try:
            query_api = mqtt_svc._influx.query_api()
            flux_query = f'''
                from(bucket: "{settings.influxdb_bucket}")
                  |> range(start: -10m)
                  |> filter(fn: (r) => r["_measurement"] == "sensor_data")
                  |> filter(fn: (r) => r["_field"] == "temperature")
                  |> last()
            '''

            loop = asyncio.get_running_loop()
            tables = await loop.run_in_executor(None, lambda: query_api.query(flux_query))

            for table in tables:
                for record in table.records:
                    device_id = record.values.get("device_id", "")
                    temp = record.get_value()
                    if device_id and temp is not None:
                        self._temps[device_id] = float(temp)
                        logger.debug(
                            "InfluxDB'den sicaklik yuklendi: %s = %.1f°C",
                            device_id, float(temp)
                        )

            # Eger InfluxDB'de 10dk icinde veri yoksa, 1 saate kadar genis
            if not self._temps:
                flux_query_wide = f'''
                    from(bucket: "{settings.influxdb_bucket}")
                      |> range(start: -1h)
                      |> filter(fn: (r) => r["_measurement"] == "sensor_data")
                      |> filter(fn: (r) => r["_field"] == "temperature")
                      |> last()
                '''
                tables = await loop.run_in_executor(
                    None, lambda: query_api.query(flux_query_wide)
                )
                for table in tables:
                    for record in table.records:
                        device_id = record.values.get("device_id", "")
                        temp = record.get_value()
                        if device_id and temp is not None:
                            self._temps[device_id] = float(temp)
                            logger.debug(
                                "InfluxDB'den (1h) sicaklik yuklendi: %s = %.1f°C",
                                device_id, float(temp)
                            )

        except Exception as e:
            logger.warning("InfluxDB'den sicaklik okunamadi: %s", e)

    def set_boost_active(self, active: bool):
        """Boost modu durumunu ayarla."""
        self._boost_active = active
        if active:
            logger.info("Boost modu aktif -- otomatik isitma kontrolu duraklatildi")
        else:
            logger.info("Boost modu bitti -- otomatik isitma kontrolu devam ediyor")

    @property
    def boost_active(self) -> bool:
        return self._boost_active

    async def _evaluate(self):
        """Mevcut durumu degerlendir, gerekirse relay komutu gonder."""
        if self._boost_active:
            logger.debug("Boost aktif, otomatik degerlendirme atlaniyor")
            return

        async with async_session() as session:
            result = await session.execute(select(HeatingConfig).limit(1))
            config = result.scalar_one_or_none()
            if not config:
                return

            # Manuel modlarda otomatik kontrol yapma
            if config.mode in ("manual_on", "manual_off", "manual"):
                return

            # Schedule modu
            target_temp = config.target_temp
            if config.mode == "schedule":
                schedule_temp = await self._get_schedule_temp(session)
                if schedule_temp is not None:
                    target_temp = schedule_temp

            # Sensor verisi kontrolu
            if not self._temps:
                logger.debug("Henuz sensor verisi yok, bekleniyor")
                return

            # Oda agirliklarini al
            rooms_result = await session.execute(select(Room))
            rooms = {r.id: r for r in rooms_result.scalars().all()}

            # Device -> Room eslemesi
            devices_result = await session.execute(
                select(Device).where(
                    Device.type.in_(["sensor", "combo"]),
                    Device.device_id.in_(list(self._temps.keys())),
                )
            )
            devices = devices_result.scalars().all()

            temps: list[float] = []
            weights: list[float] = []
            for device in devices:
                if device.device_id in self._temps:
                    temp = self._temps[device.device_id]
                    weight = 1.0
                    if device.room_id and device.room_id in rooms:
                        weight = rooms[device.room_id].weight
                    temps.append(temp)
                    weights.append(weight)

            if not temps:
                return

            # MQTT servisinden gerçek relay state'i senkronize et
            mqtt_svc = getattr(self, "_mqtt_service", None)
            if mqtt_svc is not None:
                self._relay_state = mqtt_svc.relay_state

            # Hysteresis karar
            new_state = evaluate_heating(
                temps=temps,
                weights=weights,
                target=target_temp,
                hysteresis=config.hysteresis,
                current_relay_state=self._relay_state,
                strategy=config.strategy,
            )

            logger.info(
                "Eval sonuc: temps=%.1f, target=%.1f, hyst=%.1f, relay_now=%s, karar=%s",
                temps[0] if temps else 0, target_temp, config.hysteresis,
                self._relay_state, new_state,
            )

            # Min cycle time kontrolu
            if (
                config.min_cycle_min > 0
                and self._last_relay_change
                and new_state != self._relay_state
            ):
                elapsed = (
                    datetime.now(timezone.utc) - self._last_relay_change
                ).total_seconds()
                if elapsed < config.min_cycle_min * 60:
                    logger.debug(
                        "Min cycle suresi dolmadi: %.0f/%d saniye",
                        elapsed,
                        config.min_cycle_min * 60,
                    )
                    return

            if new_state != self._relay_state:
                logger.info(
                    "Isitma karari: %s (temps=%s, target=%.1f, hyst=%.1f)",
                    "AC" if new_state else "KAPA",
                    [f"{t:.1f}" for t in temps],
                    target_temp,
                    config.hysteresis,
                )
                await self._set_relay(new_state)

    async def _get_schedule_temp(self, session) -> float | None:
        """Mevcut zamana gore schedule'dan hedef sicakligi bul."""
        now = datetime.now()
        day_of_week = now.weekday()
        hour = now.hour
        minute = now.minute

        result = await session.execute(
            select(Schedule)
            .where(
                Schedule.enabled == True,
                Schedule.day_of_week == day_of_week,
                (Schedule.hour * 60 + Schedule.minute) <= (hour * 60 + minute),
            )
            .order_by((Schedule.hour * 60 + Schedule.minute).desc())
            .limit(1)
        )
        schedule = result.scalar_one_or_none()

        if schedule:
            return schedule.target_temp

        yesterday = (day_of_week - 1) % 7
        result = await session.execute(
            select(Schedule)
            .where(
                Schedule.enabled == True,
                Schedule.day_of_week == yesterday,
            )
            .order_by((Schedule.hour * 60 + Schedule.minute).desc())
            .limit(1)
        )
        schedule = result.scalar_one_or_none()
        return schedule.target_temp if schedule else None

    def set_mqtt_service(self, mqtt_service):
        """MQTT servis referansini ayarla."""
        self._mqtt_service = mqtt_service

    def _reset_daily_counters_if_needed(self):
        """Gun degismisse gunluk sayaclari sifirla."""
        today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        if self._daily_date != today:
            self._daily_runtime_sec = 0.0
            self._cycle_count_today = 0
            self._daily_date = today

    async def _record_relay_to_influx(self, state: bool, now: datetime):
        """Relay durumunu InfluxDB'ye kaydet (thread executor'da)."""
        mqtt_svc = getattr(self, "_mqtt_service", None)
        if mqtt_svc is None:
            return

        try:
            from influxdb_client import Point

            point = (
                Point("relay_state")
                .tag("source", "heating_service")
                .tag("state", "on" if state else "off")
                .field("state", 1 if state else 0)
                .time(now)
            )

            if not state and self._last_relay_change is not None:
                duration = (now - self._last_relay_change).total_seconds()
                point = point.field("duration_sec", round(duration, 1))
                self._reset_daily_counters_if_needed()
                self._daily_runtime_sec += duration

            if state:
                self._reset_daily_counters_if_needed()
                self._cycle_count_today += 1

            # Blocking write'ı thread'e taşı
            loop = asyncio.get_running_loop()
            await loop.run_in_executor(
                None,
                lambda: mqtt_svc._influx_write.write(
                    bucket=settings.influxdb_bucket,
                    org=settings.influxdb_org,
                    record=point,
                ),
            )
        except Exception as e:
            logger.warning("Relay durumu InfluxDB'ye yazilamadi: %s", e)

    async def _set_relay(self, state: bool):
        """Relay durumunu degistir ve MQTT komutu gonder."""
        if state == self._relay_state and self._last_relay_change is not None:
            return

        now = datetime.now(timezone.utc)

        await self._record_relay_to_influx(state, now)

        self._relay_state = state
        self._last_relay_change = now

        logger.info("Relay durumu degisti: %s", "ACIK" if state else "KAPALI")

        mqtt_svc = getattr(self, "_mqtt_service", None)
        if mqtt_svc is not None:
            mqtt_svc.publish_relay(state)
        else:
            logger.warning("MQTT servisi ayarlanmamis, relay komutu gonderilemedi")

        await ws_manager.broadcast({
            "type": "relay_state",
            "data": {
                "state": state,
                "timestamp": self._last_relay_change.isoformat(),
            },
        })


# Singleton instance
heating_service = HeatingService()
