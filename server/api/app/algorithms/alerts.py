"""Akilli uyari sistemi - sensor, enerji ve kombi verilerini degerlendirip
kullaniciya anlamli uyarilar uretir.

Uyari seviyeleri:
- critical: Hemen mudahale gerekli (donma riski, kombi arizasi)
- warning: Dikkat edilmeli (yuksek tuketim, nem sorunu)
- info: Bilgilendirme (tasarruf onerisi, tahmin)
- success: Olumlu durum (verimli calisma)
"""

import logging
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone

from app.algorithms.energy import EnergyCalculator, energy_calculator

logger = logging.getLogger("tonbil.alerts")


@dataclass(frozen=True)
class Alert:
    """Tek bir uyari."""

    type: str       # "temperature", "humidity", "energy", "boiler", "forecast"
    severity: str   # "critical", "warning", "info", "success"
    title: str      # Kisa baslik
    message: str    # Detayli aciklama
    icon: str       # UI iconu
    action: str     # Onerilen aksiyon (bos olabilir)
    timestamp: str = ""  # ISO format

    def to_dict(self) -> dict:
        result = asdict(self)
        if not result["timestamp"]:
            result["timestamp"] = datetime.now(timezone.utc).isoformat()
        return result


@dataclass
class SensorData:
    """Bir oda/sensorun verileri."""

    room_name: str
    temperature: float | None = None
    humidity: float | None = None
    pressure: float | None = None
    last_seen: datetime | None = None


@dataclass
class BoilerStatus:
    """Kombi durum bilgisi."""

    relay_on: bool = False
    relay_on_since: datetime | None = None
    total_runtime_today_sec: float = 0.0
    cycle_count_today: int = 0
    last_cycle_duration_sec: float = 0.0
    avg_cycle_duration_sec: float = 0.0


@dataclass
class HeatingConfig:
    """Isitma konfigurasyonu."""

    target_temp: float = 22.0
    flow_temp: float = 60.0
    gas_price: float = 7.0
    floor_area: float = 100.0
    mode: str = "auto"


@dataclass
class WeatherData:
    """Hava durumu verisi."""

    outdoor_temp: float = 10.0
    outdoor_humidity: float = 50.0
    forecast_min_temp: float | None = None
    forecast_max_temp: float | None = None
    description: str = ""


class AlertEngine:
    """Akilli uyari motoru.

    Tum verileri toplu degerlendirir ve oncelikli uyari listesi dondurur.
    Her uyari tipi icin cooldown suresi vardir (ayni uyari cok sik tekrarlanmaz).
    """

    # Esik degerleri
    TEMP_TOO_HIGH_OFFSET: float = 2.0      # hedef + 2C = cok sicak
    TEMP_TOO_LOW_ABSOLUTE: float = 5.0     # donma riski
    TEMP_WINDOW_OPEN_DROP: float = 3.0     # pencere acik oldugu zaman ani dusus
    HUMIDITY_HIGH: float = 60.0
    HUMIDITY_LOW: float = 30.0
    HUMIDITY_CRITICAL: float = 75.0
    HUMIDITY_BATHROOM_HIGH: float = 70.0
    BOILER_LONG_RUN_MIN: float = 120.0     # 2 saat surekli calisma
    BOILER_SHORT_CYCLE_MIN: float = 3.0    # 3 dakikadan kisa calisma
    BOILER_SHORT_CYCLE_COUNT: int = 10     # gunde 10+ kisa dongu = sorun
    DAILY_COST_HIGH_TL: float = 50.0       # gunde 50 TL uzerinde uyar
    CONDENSING_TEMP_LIMIT: float = 55.0    # yogusmali mod siniri

    def __init__(self, calculator: EnergyCalculator | None = None):
        self._calc = calculator or energy_calculator

    def evaluate(
        self,
        sensors: list[SensorData],
        config: HeatingConfig,
        weather: WeatherData,
        boiler: BoilerStatus,
    ) -> list[dict]:
        """Tum verileri degerlendir, uyari listesi dondur.

        Args:
            sensors: Oda sensor verileri listesi.
            config: Isitma konfigurasyonu.
            weather: Hava durumu verisi.
            boiler: Kombi durum bilgisi.

        Returns:
            Oncelik sirasina gore uyari listesi.
            Her eleman: {type, severity, title, message, icon, action, timestamp}
        """
        alerts: list[Alert] = []

        alerts.extend(self._check_temperature(sensors, config, weather))
        alerts.extend(self._check_humidity(sensors))
        alerts.extend(self._check_energy(config, boiler))
        alerts.extend(self._check_boiler(boiler, config, sensors))
        alerts.extend(self._check_forecast(config, weather, sensors, boiler))

        # Oncelik siralama: critical > warning > info > success
        severity_order = {"critical": 0, "warning": 1, "info": 2, "success": 3}
        alerts.sort(key=lambda a: severity_order.get(a.severity, 99))

        return [a.to_dict() for a in alerts]

    def _check_temperature(
        self,
        sensors: list[SensorData],
        config: HeatingConfig,
        weather: WeatherData,
    ) -> list[Alert]:
        """Sicaklik uyarilari."""
        alerts: list[Alert] = []
        target = config.target_temp

        for sensor in sensors:
            temp = sensor.temperature
            if temp is None:
                continue

            room = sensor.room_name

            # Donma riski
            if temp < self.TEMP_TOO_LOW_ABSOLUTE:
                alerts.append(Alert(
                    type="temperature",
                    severity="critical",
                    title=f"{room}: Donma riski!",
                    message=(
                        f"{room} sicakligi {temp:.1f}C - kritik dusuk. "
                        "Boru donmasi riski var. Isitmayi hemen acin."
                    ),
                    icon="thermometer-snowflake",
                    action="heating_on",
                ))

            # Cok soguk (hedefin cok altinda)
            elif temp < target - 3.0:
                alerts.append(Alert(
                    type="temperature",
                    severity="warning",
                    title=f"{room} cok soguk",
                    message=(
                        f"{room} sicakligi {temp:.1f}C, hedef {target:.1f}C. "
                        "Pencere acik olabilir veya kombi yetersiz kalabilir."
                    ),
                    icon="thermometer-low",
                    action="check_windows",
                ))

            # Hedefin uzerinde
            elif temp > target + self.TEMP_TOO_HIGH_OFFSET:
                alerts.append(Alert(
                    type="temperature",
                    severity="info",
                    title=f"{room} hedefin uzerinde",
                    message=(
                        f"{room} sicakligi {temp:.1f}C, hedef {target:.1f}C. "
                        "Hedef sicakligi dusurmeyi veya isitmayi kapatmayi dusunun."
                    ),
                    icon="thermometer-high",
                    action="reduce_target",
                ))

            # Sensor offline kontrolu
            if sensor.last_seen is not None:
                elapsed = (
                    datetime.now(timezone.utc) - sensor.last_seen
                ).total_seconds()
                if elapsed > 600:  # 10 dakikadir veri yok
                    alerts.append(Alert(
                        type="temperature",
                        severity="warning",
                        title=f"{room} sensoru yanit vermiyor",
                        message=(
                            f"{room} sensorunden {int(elapsed / 60)} dakikadir "
                            "veri alinmiyor. Pil veya baglanti sorunu olabilir."
                        ),
                        icon="wifi-off",
                        action="check_sensor",
                    ))

        # Odalar arasi sicaklik farki
        temps_with_rooms = [
            (s.room_name, s.temperature)
            for s in sensors
            if s.temperature is not None
        ]
        if len(temps_with_rooms) >= 2:
            sorted_rooms = sorted(temps_with_rooms, key=lambda x: x[1])
            coldest_name, coldest_temp = sorted_rooms[0]
            hottest_name, hottest_temp = sorted_rooms[-1]
            diff = hottest_temp - coldest_temp

            if diff > self.TEMP_WINDOW_OPEN_DROP:
                alerts.append(Alert(
                    type="temperature",
                    severity="warning",
                    title=f"{coldest_name} cok soguk ({diff:.1f}C fark)",
                    message=(
                        f"{coldest_name} ({coldest_temp:.1f}C) ile "
                        f"{hottest_name} ({hottest_temp:.1f}C) arasinda "
                        f"{diff:.1f}C fark var. "
                        f"{coldest_name}'da pencere acik olabilir."
                    ),
                    icon="window-open",
                    action="check_windows",
                ))

        return alerts

    def _check_humidity(self, sensors: list[SensorData]) -> list[Alert]:
        """Nem uyarilari."""
        alerts: list[Alert] = []

        for sensor in sensors:
            hum = sensor.humidity
            if hum is None:
                continue

            room = sensor.room_name
            is_bathroom = any(
                kw in room.lower()
                for kw in ("banyo", "wc", "tuvalet", "bath", "duş", "dus")
            )

            # Kritik yuksek nem
            if hum > self.HUMIDITY_CRITICAL:
                alerts.append(Alert(
                    type="humidity",
                    severity="warning",
                    title=f"{room}: Nem cok yuksek ({hum:.0f}%)",
                    message=(
                        f"{room} nemi %{hum:.0f} - kuf olusumu riski var. "
                        "Hemen havalandirin."
                    ),
                    icon="droplet-alert",
                    action="ventilate",
                ))

            # Banyo yuksek nem
            elif is_bathroom and hum > self.HUMIDITY_BATHROOM_HIGH:
                alerts.append(Alert(
                    type="humidity",
                    severity="info",
                    title=f"{room}: Nem yuksek ({hum:.0f}%)",
                    message=(
                        f"{room} nemi %{hum:.0f}. "
                        "Aspirator calistirin veya pencereyi acin."
                    ),
                    icon="droplet",
                    action="run_fan",
                ))

            # Genel yuksek nem
            elif hum > self.HUMIDITY_HIGH:
                alerts.append(Alert(
                    type="humidity",
                    severity="info",
                    title=f"{room}: Nem yuksek ({hum:.0f}%)",
                    message=(
                        f"{room} nemi %{hum:.0f}. "
                        "Havalandirma yapmaniz onerilir."
                    ),
                    icon="droplet",
                    action="ventilate",
                ))

            # Dusuk nem
            elif hum < self.HUMIDITY_LOW:
                alerts.append(Alert(
                    type="humidity",
                    severity="info",
                    title=f"{room}: Nem dusuk ({hum:.0f}%)",
                    message=(
                        f"{room} nemi %{hum:.0f}. Kuru hava solunum yollarini "
                        "tahris edebilir. Nemlendirici kullanmayi dusunun."
                    ),
                    icon="humidity-low",
                    action="use_humidifier",
                ))

        return alerts

    def _check_energy(
        self, config: HeatingConfig, boiler: BoilerStatus
    ) -> list[Alert]:
        """Enerji tuketimi ve verimlilik uyarilari."""
        alerts: list[Alert] = []

        runtime_hours = boiler.total_runtime_today_sec / 3600.0
        flow_temp = config.flow_temp
        gas_price = config.gas_price

        # Gunluk tuketim hesabi
        gas_m3 = self._calc.calculate_gas_consumption(runtime_hours, flow_temp)
        cost_tl = self._calc.calculate_cost(gas_m3, gas_price)

        # Gunluk maliyet ozeti (her zaman goster)
        if runtime_hours > 0.1:
            kwh, kcal = self._calc.calculate_thermal_output(gas_m3, flow_temp)
            alerts.append(Alert(
                type="energy",
                severity="info",
                title=f"Bugun: {cost_tl:.1f} TL ({gas_m3:.2f} m3)",
                message=(
                    f"Kombi bugun {runtime_hours:.1f} saat calisti. "
                    f"Tuketim: {gas_m3:.2f} m3 dogalgaz, "
                    f"{kwh:.1f} kWh ({kcal:.0f} kcal) isil enerji."
                ),
                icon="flame",
                action="",
            ))

        # Yuksek gunluk maliyet
        if cost_tl > self.DAILY_COST_HIGH_TL:
            alerts.append(Alert(
                type="energy",
                severity="warning",
                title=f"Yuksek tuketim: {cost_tl:.1f} TL",
                message=(
                    f"Gunluk dogalgaz maliyeti {cost_tl:.1f} TL'yi gecti. "
                    "Su sicakligini dusurmeyi veya hedef sicakligi "
                    "azaltmayi dusunun."
                ),
                icon="currency-lira",
                action="reduce_flow_temp",
            ))

        # Yogusmali verim kontrolu
        if flow_temp > self.CONDENSING_TEMP_LIMIT:
            analysis = self._calc.analyze_efficiency(flow_temp)
            savings = analysis.potential_savings_pct
            if savings > 3.0:
                alerts.append(Alert(
                    type="energy",
                    severity="info",
                    title=f"Tasarruf firsati: ~%{savings:.0f}",
                    message=(
                        f"Radyator su sicakliginiz {flow_temp:.0f}C. "
                        f"55C altina dusurmek yogusmali verimle "
                        f"~%{savings:.0f} tasarruf saglayabilir. "
                        "Soguk havalarda isitma suresi biraz uzayabilir."
                    ),
                    icon="leaf",
                    action="reduce_flow_temp",
                ))
        else:
            alerts.append(Alert(
                type="energy",
                severity="success",
                title="Yogusmali verim aktif",
                message=(
                    f"Su sicakliginiz {flow_temp:.0f}C - kombi yogusmali "
                    f"modda, verim ~%{self._calc.interpolate_efficiency(flow_temp) * 100:.0f}."
                ),
                icon="check-circle",
                action="",
            ))

        return alerts

    def _check_boiler(
        self,
        boiler: BoilerStatus,
        config: HeatingConfig,
        sensors: list[SensorData],
    ) -> list[Alert]:
        """Kombi calisma durumu uyarilari."""
        alerts: list[Alert] = []

        # Uzun sureli calisma
        if boiler.relay_on and boiler.relay_on_since is not None:
            running_min = (
                datetime.now(timezone.utc) - boiler.relay_on_since
            ).total_seconds() / 60.0

            if running_min > self.BOILER_LONG_RUN_MIN:
                alerts.append(Alert(
                    type="boiler",
                    severity="warning",
                    title=f"Kombi {running_min:.0f} dakikadir calisiyor",
                    message=(
                        f"Kombi {running_min:.0f} dakikadir surekli calisiyor "
                        "ama hedefe ulasilamadi. Bina yalitimi yetersiz olabilir "
                        "veya dis hava cok soguk olabilir."
                    ),
                    icon="alert-triangle",
                    action="check_insulation",
                ))

        # Short cycling (cok sik acilip kapanma)
        if boiler.cycle_count_today > self.BOILER_SHORT_CYCLE_COUNT:
            avg_cycle = boiler.avg_cycle_duration_sec / 60.0
            if avg_cycle < self.BOILER_SHORT_CYCLE_MIN:
                alerts.append(Alert(
                    type="boiler",
                    severity="warning",
                    title="Kombi cok sik acilip kapaniyor",
                    message=(
                        f"Bugun {boiler.cycle_count_today} kez acilip kapandi, "
                        f"ortalama calisma: {avg_cycle:.1f} dakika. "
                        "Hysteresis degerini artirmayi dusunun (ornegin 0.5 -> 1.0)."
                    ),
                    icon="repeat",
                    action="increase_hysteresis",
                ))

        # Kombi kapali ama sicaklik dusuyor
        if not boiler.relay_on and config.mode == "auto":
            cold_rooms = [
                s for s in sensors
                if s.temperature is not None
                and s.temperature < config.target_temp - 1.0
            ]
            if cold_rooms:
                names = ", ".join(s.room_name for s in cold_rooms[:3])
                alerts.append(Alert(
                    type="boiler",
                    severity="info",
                    title="Sicaklik hedefin altinda",
                    message=(
                        f"{names} hedef sicakligin ({config.target_temp:.1f}C) "
                        "altinda. Kombi kapaliysa bu beklenen bir soguma olabilir."
                    ),
                    icon="thermometer-minus",
                    action="",
                ))

        return alerts

    def _check_forecast(
        self,
        config: HeatingConfig,
        weather: WeatherData,
        sensors: list[SensorData],
        boiler: BoilerStatus,
    ) -> list[Alert]:
        """Tahmine dayali uyarilar."""
        alerts: list[Alert] = []

        # Ortalama ic sicaklik
        valid_temps = [
            s.temperature for s in sensors if s.temperature is not None
        ]
        if not valid_temps:
            return alerts

        avg_indoor = sum(valid_temps) / len(valid_temps)
        outdoor = weather.outdoor_temp

        # Soguma tahmini (kombi kapali ise)
        if not boiler.relay_on:
            cooling_rate = self._calc.estimate_cooling_rate(avg_indoor, outdoor)
            if cooling_rate < -0.3:  # saatte 0.3C'den fazla soguma
                hours_to_target = 0.0
                if cooling_rate != 0:
                    hours_to_target = (
                        avg_indoor - config.target_temp + 1.0
                    ) / abs(cooling_rate)

                if 0 < hours_to_target < 6:
                    alerts.append(Alert(
                        type="forecast",
                        severity="info",
                        title=f"~{hours_to_target:.0f} saat sonra sicaklik dusecek",
                        message=(
                            f"Mevcut soguma hizina gore ({abs(cooling_rate):.1f}C/saat) "
                            f"yaklasik {hours_to_target:.0f} saat sonra sicaklik "
                            f"hedefin ({config.target_temp:.1f}C) altina dusecek."
                        ),
                        icon="trending-down",
                        action="",
                    ))

        # Isitma suresi tahmini
        if boiler.relay_on:
            estimate = self._calc.estimate_heating_duration(
                current_temp=avg_indoor,
                target_temp=config.target_temp,
                outdoor_temp=outdoor,
                floor_area=config.floor_area,
                flow_temp=config.flow_temp,
            )
            if (
                estimate.estimated_minutes > 0
                and estimate.estimated_minutes < float("inf")
            ):
                alerts.append(Alert(
                    type="forecast",
                    severity="info",
                    title=f"Hedefe ~{estimate.estimated_minutes:.0f} dakika",
                    message=(
                        f"Hedef sicakliga ({config.target_temp:.1f}C) ulasmak icin "
                        f"kombi yaklasik {estimate.estimated_minutes:.0f} dakika "
                        f"daha calisacak. Tahmini maliyet: {estimate.estimated_cost_tl:.1f} TL."
                    ),
                    icon="clock",
                    action="",
                ))

        # Hava durumu tahmini
        if weather.forecast_min_temp is not None:
            if weather.forecast_min_temp < outdoor - 5:
                alerts.append(Alert(
                    type="forecast",
                    severity="info",
                    title="Hava soguyacak",
                    message=(
                        f"Yarin minimum sicaklik {weather.forecast_min_temp:.0f}C "
                        f"(su an {outdoor:.0f}C). Kombi daha fazla calisacak, "
                        "enerji tuketimi artabilir."
                    ),
                    icon="cloud-snow",
                    action="",
                ))

        # Diger yontem: dis sicaklik cok dusukse uyar
        if outdoor < -5:
            alerts.append(Alert(
                type="forecast",
                severity="warning",
                title=f"Dis hava cok soguk ({outdoor:.0f}C)",
                message=(
                    f"Dis sicaklik {outdoor:.0f}C. Boru donmasi riskine karsi "
                    "isitmayi kesinlikle acik tutun. Radyator su sicakligini "
                    "artirmaniz gerekebilir."
                ),
                icon="snowflake",
                action="increase_flow_temp",
            ))

        return alerts


# Singleton instance
alert_engine = AlertEngine()
