"""ECA Proteus Premix 30kW enerji hesaplama motoru.

Yogusmali kombi teknik verilerine dayali gaz tuketimi, isil cikti,
maliyet ve verimlilik hesaplamalari.

Referans:
- ECA Proteus Premix 30 HE teknik dokumantasyonu
- Turkiye dogalgaz alt isil degeri: ~9.59 kWh/m3 (34.5 MJ/m3)
- Yogusmali verim Hi bazinda hesaplanir (ust isil deger uzerinden >%100 mumkun)
"""

import math
from dataclasses import dataclass


@dataclass(frozen=True)
class HourlyStats:
    """Saatlik enerji istatistikleri."""

    runtime_minutes: float
    gas_m3: float
    thermal_kwh: float
    thermal_kcal: float
    cost_tl: float
    efficiency_pct: float
    flow_temp: float


@dataclass(frozen=True)
class DailySummary:
    """Gunluk enerji ozeti."""

    date: str
    total_runtime_minutes: float
    total_gas_m3: float
    total_thermal_kwh: float
    total_thermal_kcal: float
    total_cost_tl: float
    avg_efficiency_pct: float
    hourly_breakdown: list[HourlyStats]
    duty_cycle_pct: float  # gun icinde ne kadar calisti (%)


@dataclass(frozen=True)
class HeatingEstimate:
    """Isitma suresi ve maliyet tahmini."""

    estimated_minutes: float
    estimated_gas_m3: float
    estimated_cost_tl: float
    estimated_kwh: float
    confidence: str  # "high", "medium", "low"


@dataclass(frozen=True)
class EfficiencyAnalysis:
    """Verimlilik analiz sonucu."""

    current_flow_temp: float
    current_efficiency_pct: float
    optimal_flow_temp: float
    optimal_efficiency_pct: float
    potential_savings_pct: float
    is_condensing: bool
    recommendation: str


class EnergyCalculator:
    """ECA Proteus Premix 30kW enerji hesaplama motoru.

    Kombi ON/OFF relay ile kontrol edilir. Kullanici radyator su sicakligini
    (flow temp) kombi panelinden ayarlar. Bu sicaklik gaz tuketimi ve verimi
    dogrudan etkiler.

    Yogusmali kombiler dus sicaklikta (~40-55C) baca gazindaki su buharinin
    yogusmasiyla ekstra isi geri kazanir, bu nedenle Hi bazinda verim >%100
    olabilir.
    """

    # ================================================================
    # ECA Proteus Premix 30 HM — Gercek Teknik Veriler
    # Kaynak: ECA resmi dokumantasyon, eca.com.tr, ManualsLib
    # ================================================================
    BOILER_MAX_KW: float = 30.0       # maks isil cikti 80/60C'de
    BOILER_MAX_KW_COND: float = 31.7  # maks isil cikti 50/30C'de (yogusmali)
    BOILER_MIN_KW: float = 6.9        # min modulasyon (1:4 oran, %25)
    BOILER_MIN_KW_COND: float = 8.3   # min modulasyon 50/30C'de
    BOILER_HEAT_INPUT_MAX: float = 28.7  # maks gaz giris gucu (kW)
    BOILER_HEAT_INPUT_MIN: float = 7.7   # min gaz giris gucu (kW)
    BOILER_BRAND: str = "ECA Proteus Premix"
    BOILER_MODEL: str = "30 HM"

    # Gaz tuketimi (m3/saat) - akis sicakligina gore
    # Gercek ECA verileri: min 0.72, maks 2.70 m3/saat
    # Ara degerler: modulasyon orani + akis sicakligina gore interpolasyon
    GAS_FLOW_RATES: dict[int, float] = {
        35: 0.72,  # minimum modulasyon, tam yogusmali
        40: 0.90,  # dusuk sicaklik, yogusmali mod
        45: 1.10,
        50: 1.35,  # ideal yogusmali (50/30C donus)
        55: 1.65,  # yogusma sinirinda (donus ~45-50C)
        60: 2.00,  # yogusma azaliyor (donus ~50-55C)
        65: 2.25,  # konvansiyonel moda gecis
        70: 2.45,  # konvansiyonel mod
        75: 2.60,
        80: 2.70,  # tam yuk, yogusma yok
    }

    # Verim tablosu (akis sicakligina gore, Hi bazinda)
    # ECA resmi: %107.5 verim (50/30C'de)
    # Yogusma esigi: donus suyu < 54-57C
    EFFICIENCY: dict[int, float] = {
        35: 1.09,  # %109 tam yogusmali
        40: 1.08,  # %108 yogusmali
        45: 1.065, # %106.5
        50: 1.05,  # %105 (ECA resmi 50/30C: %107.5, ortalama)
        55: 1.02,  # %102 yogusma sinirinda
        60: 0.97,  # %97 yogusma bitmeye basliyor
        65: 0.955, # %95.5 konvansiyonel
        70: 0.945, # %94.5
        75: 0.935, # %93.5
        80: 0.93,  # %93 tam konvansiyonel (ECA resmi 80/60C)
    }

    # Anti-cycling: ECA dahili 3-5 dk bekleme suresi var
    BOILER_ANTI_CYCLE_MIN: int = 3  # dakika

    # Turkiye dogalgaz degerleri (BOTAS + IGDAS 2025)
    GAS_CALORIFIC_KWH: float = 9.59    # kWh/m3 (alt isil deger - Hi)
    GAS_CALORIFIC_KWH_HS: float = 10.64  # kWh/m3 (ust isil deger - Hs)
    GAS_CALORIFIC_KCAL: float = 8250.0  # kcal/m3 (alt isil deger)
    GAS_CALORIFIC_KCAL_HS: float = 9155.0  # kcal/m3 (ust isil deger)
    DEFAULT_GAS_PRICE: float = 11.91   # TL/m3 (IGDAS Istanbul 2025, KDV dahil)

    # Bina termal parametreleri (varsayilan Turkiye konut)
    DEFAULT_THERMAL_MASS_KJ_PER_C: float = 15000.0  # kJ/C (100m2 beton bina)
    DEFAULT_HEAT_LOSS_W_PER_C: float = 150.0  # W/C dis sicaklik farki basina
    DEFAULT_TAU_HOURS: float = 40.0  # soguma zaman sabiti (saat)

    def interpolate_gas_rate(self, flow_temp: float) -> float:
        """Ara sicaklik degerleri icin gas rate interpolasyonu.

        Bilinen veri noktalari arasinda lineer interpolasyon yapar.
        Sinir disinda en yakin degeri kullanir.

        Args:
            flow_temp: Radyator akis sicakligi (C).

        Returns:
            Tahmini gaz tuketim hizi (m3/saat).
        """
        temps = sorted(self.GAS_FLOW_RATES.keys())

        if flow_temp <= temps[0]:
            return self.GAS_FLOW_RATES[temps[0]]
        if flow_temp >= temps[-1]:
            return self.GAS_FLOW_RATES[temps[-1]]

        # Aradaki iki noktayi bul
        for i in range(len(temps) - 1):
            t_low = temps[i]
            t_high = temps[i + 1]
            if t_low <= flow_temp <= t_high:
                rate_low = self.GAS_FLOW_RATES[t_low]
                rate_high = self.GAS_FLOW_RATES[t_high]
                fraction = (flow_temp - t_low) / (t_high - t_low)
                return rate_low + fraction * (rate_high - rate_low)

        return self.GAS_FLOW_RATES[60]  # fallback

    def interpolate_efficiency(self, flow_temp: float) -> float:
        """Ara sicaklik degerleri icin verim interpolasyonu.

        Args:
            flow_temp: Radyator akis sicakligi (C).

        Returns:
            Verim katsayisi (ornek: 1.04 = %104).
        """
        temps = sorted(self.EFFICIENCY.keys())

        if flow_temp <= temps[0]:
            return self.EFFICIENCY[temps[0]]
        if flow_temp >= temps[-1]:
            return self.EFFICIENCY[temps[-1]]

        for i in range(len(temps) - 1):
            t_low = temps[i]
            t_high = temps[i + 1]
            if t_low <= flow_temp <= t_high:
                eff_low = self.EFFICIENCY[t_low]
                eff_high = self.EFFICIENCY[t_high]
                fraction = (flow_temp - t_low) / (t_high - t_low)
                return eff_low + fraction * (eff_high - eff_low)

        return self.EFFICIENCY[60]

    def calculate_gas_consumption(
        self, runtime_hours: float, flow_temp: float = 60.0
    ) -> float:
        """Calisma suresinden gaz tuketimi hesapla.

        Args:
            runtime_hours: Kombi calisma suresi (saat).
            flow_temp: Radyator akis sicakligi (C).

        Returns:
            Tuketilen gaz miktari (m3).
        """
        if runtime_hours <= 0:
            return 0.0
        gas_rate = self.interpolate_gas_rate(flow_temp)
        return round(gas_rate * runtime_hours, 3)

    def calculate_thermal_output(
        self, gas_m3: float, flow_temp: float = 60.0
    ) -> tuple[float, float]:
        """Gaz tuketiminden isil cikti hesapla.

        Yogusmali verim uygulayarak gercek isil ciktiyi hesaplar.

        Args:
            gas_m3: Tuketilen gaz (m3).
            flow_temp: Radyator akis sicakligi (C).

        Returns:
            (thermal_kwh, thermal_kcal) tuple.
        """
        if gas_m3 <= 0:
            return (0.0, 0.0)

        efficiency = self.interpolate_efficiency(flow_temp)
        raw_kwh = gas_m3 * self.GAS_CALORIFIC_KWH
        thermal_kwh = round(raw_kwh * efficiency, 2)
        thermal_kcal = round(thermal_kwh * 860.0, 0)  # 1 kWh = 860 kcal
        return (thermal_kwh, thermal_kcal)

    def calculate_cost(
        self, gas_m3: float, price_per_m3: float | None = None
    ) -> float:
        """Maliyet hesapla (TL).

        Args:
            gas_m3: Tuketilen gaz (m3).
            price_per_m3: m3 basi fiyat (TL). None ise varsayilan kullanilir.

        Returns:
            Toplam maliyet (TL).
        """
        if gas_m3 <= 0:
            return 0.0
        price = price_per_m3 if price_per_m3 is not None else self.DEFAULT_GAS_PRICE
        return round(gas_m3 * price, 2)

    def calculate_hourly_stats(
        self,
        runtime_seconds: float,
        flow_temp: float = 60.0,
        gas_price: float | None = None,
    ) -> HourlyStats:
        """Saatlik istatistik hesapla.

        Args:
            runtime_seconds: O saat icerisinde kombi calisma suresi (saniye).
            flow_temp: Radyator akis sicakligi (C).
            gas_price: Gaz fiyati TL/m3.

        Returns:
            HourlyStats dataclass.
        """
        runtime_hours = runtime_seconds / 3600.0
        runtime_minutes = runtime_seconds / 60.0
        gas_m3 = self.calculate_gas_consumption(runtime_hours, flow_temp)
        thermal_kwh, thermal_kcal = self.calculate_thermal_output(gas_m3, flow_temp)
        cost_tl = self.calculate_cost(gas_m3, gas_price)
        efficiency = self.interpolate_efficiency(flow_temp)

        return HourlyStats(
            runtime_minutes=round(runtime_minutes, 1),
            gas_m3=gas_m3,
            thermal_kwh=thermal_kwh,
            thermal_kcal=thermal_kcal,
            cost_tl=cost_tl,
            efficiency_pct=round(efficiency * 100, 1),
            flow_temp=flow_temp,
        )

    def calculate_daily_summary(
        self,
        hourly_runtimes: list[float],
        flow_temp: float = 60.0,
        gas_price: float | None = None,
        date_str: str = "",
    ) -> DailySummary:
        """Gunluk ozet hesapla.

        Args:
            hourly_runtimes: 24 elemanli liste, her eleman o saatteki
                            calisma suresi (saniye).
            flow_temp: Radyator akis sicakligi (C).
            gas_price: Gaz fiyati TL/m3.
            date_str: Tarih string (YYYY-MM-DD).

        Returns:
            DailySummary dataclass.
        """
        hourly_stats: list[HourlyStats] = []
        total_runtime_sec = 0.0
        total_gas = 0.0
        total_kwh = 0.0
        total_kcal = 0.0
        total_cost = 0.0

        for runtime_sec in hourly_runtimes:
            stats = self.calculate_hourly_stats(runtime_sec, flow_temp, gas_price)
            hourly_stats.append(stats)
            total_runtime_sec += runtime_sec
            total_gas += stats.gas_m3
            total_kwh += stats.thermal_kwh
            total_kcal += stats.thermal_kcal
            total_cost += stats.cost_tl

        total_minutes = total_runtime_sec / 60.0
        total_hours_in_day = len(hourly_runtimes) if hourly_runtimes else 24
        duty_cycle = (total_runtime_sec / (total_hours_in_day * 3600.0)) * 100.0
        avg_efficiency = self.interpolate_efficiency(flow_temp) * 100.0

        return DailySummary(
            date=date_str,
            total_runtime_minutes=round(total_minutes, 1),
            total_gas_m3=round(total_gas, 3),
            total_thermal_kwh=round(total_kwh, 2),
            total_thermal_kcal=round(total_kcal, 0),
            total_cost_tl=round(total_cost, 2),
            avg_efficiency_pct=round(avg_efficiency, 1),
            hourly_breakdown=hourly_stats,
            duty_cycle_pct=round(duty_cycle, 1),
        )

    def estimate_heating_duration(
        self,
        current_temp: float,
        target_temp: float,
        outdoor_temp: float,
        floor_area: float = 100.0,
        flow_temp: float = 60.0,
    ) -> HeatingEstimate:
        """Hedef sicakliga ulasma suresi tahmini.

        Basitlestirilmis termal model:
        - Bina termal kutlesi: floor_area * 150 kJ/C (beton yapi varsayimi)
        - Isitma gereken enerji = thermal_mass * delta_T
        - Net isitma gucu = kombi gucu - isi kaybi
        - Isi kaybi = heat_loss_coeff * (indoor_avg - outdoor)

        Args:
            current_temp: Mevcut ic sicaklik (C).
            target_temp: Hedef sicaklik (C).
            outdoor_temp: Dis hava sicakligi (C).
            floor_area: Bina alani (m2).
            flow_temp: Radyator akis sicakligi (C).

        Returns:
            HeatingEstimate dataclass.
        """
        delta_temp = target_temp - current_temp
        if delta_temp <= 0:
            return HeatingEstimate(
                estimated_minutes=0.0,
                estimated_gas_m3=0.0,
                estimated_cost_tl=0.0,
                estimated_kwh=0.0,
                confidence="high",
            )

        # Termal kutle (kJ/C) - beton bina: ~150 kJ/(m2*C)
        thermal_mass = floor_area * 150.0

        # Isitma esnasindaki ortalama ic sicaklik
        avg_indoor = (current_temp + target_temp) / 2.0

        # Isi kaybi (W) = kayip katsayisi * (ic - dis)
        # Tipik Turkiye binacilik: 1.5-2.0 W/(m2*C) dis sicaklik farki basina
        heat_loss_coeff = floor_area * 1.8  # W/C
        heat_loss_w = heat_loss_coeff * (avg_indoor - outdoor_temp)
        heat_loss_kw = max(heat_loss_w / 1000.0, 0.0)

        # Kombi net isitma gucu
        gas_rate = self.interpolate_gas_rate(flow_temp)
        efficiency = self.interpolate_efficiency(flow_temp)
        boiler_output_kw = gas_rate * self.GAS_CALORIFIC_KWH * efficiency
        net_heating_kw = boiler_output_kw - heat_loss_kw

        if net_heating_kw <= 0.5:
            # Kombi gucu kaybi karsilayamiyorsa
            return HeatingEstimate(
                estimated_minutes=float("inf"),
                estimated_gas_m3=0.0,
                estimated_cost_tl=0.0,
                estimated_kwh=0.0,
                confidence="low",
            )

        # Gereken enerji (kJ -> kWh)
        energy_needed_kj = thermal_mass * delta_temp
        energy_needed_kwh = energy_needed_kj / 3600.0

        # Sure (saat)
        time_hours = energy_needed_kwh / net_heating_kw
        time_minutes = time_hours * 60.0

        # Toplam gaz tuketimi (kombi calisma suresi boyunca)
        total_gas = self.calculate_gas_consumption(time_hours, flow_temp)
        total_cost = self.calculate_cost(total_gas)

        # Guven seviyesi
        confidence = "high"
        if delta_temp > 5 or abs(outdoor_temp - current_temp) > 20:
            confidence = "medium"
        if delta_temp > 10 or outdoor_temp < -10:
            confidence = "low"

        return HeatingEstimate(
            estimated_minutes=round(time_minutes, 0),
            estimated_gas_m3=round(total_gas, 3),
            estimated_cost_tl=round(total_cost, 2),
            estimated_kwh=round(energy_needed_kwh, 2),
            confidence=confidence,
        )

    def estimate_cooling_rate(
        self,
        indoor_temp: float,
        outdoor_temp: float,
        tau_hours: float | None = None,
    ) -> float:
        """Soguma hizi tahmini (C/saat).

        Newton soguma yasasi: dT/dt = -(T_indoor - T_outdoor) / tau
        tau: bina soguma zaman sabiti (saat). Daha buyuk = daha yavas soguma.

        Args:
            indoor_temp: Mevcut ic sicaklik (C).
            outdoor_temp: Dis hava sicakligi (C).
            tau_hours: Soguma zaman sabiti. None ise varsayilan kullanilir.

        Returns:
            Soguma hizi (C/saat, negatif = soguma).
        """
        tau = tau_hours if tau_hours is not None else self.DEFAULT_TAU_HOURS
        if tau <= 0:
            tau = self.DEFAULT_TAU_HOURS

        delta = indoor_temp - outdoor_temp
        if delta <= 0:
            return 0.0  # dis hava iceriden sicaksa soguma yok

        cooling_rate = -delta / tau
        return round(cooling_rate, 3)

    def predict_temperature(
        self,
        indoor_temp: float,
        outdoor_temp: float,
        hours_ahead: float,
        heating_on: bool = False,
        flow_temp: float = 60.0,
        floor_area: float = 100.0,
        tau_hours: float | None = None,
    ) -> float:
        """Gelecek sicaklik tahmini.

        Newton soguma/isitma modeli ile t saat sonraki sicakligi tahmin eder.

        Args:
            indoor_temp: Mevcut ic sicaklik (C).
            outdoor_temp: Dis hava sicakligi (C).
            hours_ahead: Kac saat sonra.
            heating_on: Kombi acik mi.
            flow_temp: Akis sicakligi.
            floor_area: Bina alani.
            tau_hours: Soguma zaman sabiti.

        Returns:
            Tahmini sicaklik (C).
        """
        tau = tau_hours if tau_hours is not None else self.DEFAULT_TAU_HOURS

        if not heating_on:
            # Newton soguma: T(t) = T_outdoor + (T_indoor - T_outdoor) * exp(-t/tau)
            predicted = outdoor_temp + (indoor_temp - outdoor_temp) * math.exp(
                -hours_ahead / tau
            )
            return round(predicted, 1)

        # Isitma acikken: efektif ortam sicakligi artar
        gas_rate = self.interpolate_gas_rate(flow_temp)
        efficiency = self.interpolate_efficiency(flow_temp)
        boiler_kw = gas_rate * self.GAS_CALORIFIC_KWH * efficiency
        heat_loss_coeff_kw = (floor_area * 1.8) / 1000.0  # kW/C

        if heat_loss_coeff_kw <= 0:
            return indoor_temp

        # Efektif denge sicakligi (kombi surekli calisirsa ulasilacak sicaklik)
        t_equilibrium = outdoor_temp + boiler_kw / heat_loss_coeff_kw

        # Isitma zaman sabiti (termal kutle / kayip katsayisi)
        thermal_mass_kwh_per_c = (floor_area * 150.0) / 3600.0  # kJ -> kWh
        tau_heating = thermal_mass_kwh_per_c / heat_loss_coeff_kw

        predicted = t_equilibrium + (indoor_temp - t_equilibrium) * math.exp(
            -hours_ahead / tau_heating
        )
        return round(predicted, 1)

    def analyze_efficiency(
        self, current_flow_temp: float = 60.0
    ) -> EfficiencyAnalysis:
        """Verimlilik analizi ve optimizasyon onerisi.

        Args:
            current_flow_temp: Mevcut akis sicakligi (C).

        Returns:
            EfficiencyAnalysis dataclass.
        """
        current_eff = self.interpolate_efficiency(current_flow_temp)
        is_condensing = current_flow_temp <= 55

        # Optimal: 50C genelde en iyi denge (yeterli isitma + yogusma)
        optimal_temp = 50.0
        optimal_eff = self.interpolate_efficiency(optimal_temp)

        # Tasarruf potansiyeli
        if current_eff > 0:
            savings_pct = ((optimal_eff - current_eff) / current_eff) * 100.0
        else:
            savings_pct = 0.0

        # Oneri metni
        if current_flow_temp <= 50:
            recommendation = (
                "Mukemmel! Su sicakliginiz yogusmali verim icin ideal aralikta. "
                "Kombininiz maksimum verimle calisiyor."
            )
        elif current_flow_temp <= 55:
            recommendation = (
                "Iyi! Yogusmali verim aktif. Biraz daha dusurerek "
                f"(%{abs(savings_pct):.0f} ek tasarruf) daha verimli calisabilirsiniz."
            )
        elif current_flow_temp <= 60:
            recommendation = (
                f"Su sicakliginiz yogusma sinirinda. 50C'ye dusurmeniz "
                f"%{abs(savings_pct):.0f} tasarruf saglayabilir. "
                "Ancak cok soguk havalarda isitma suresi uzayabilir."
            )
        else:
            recommendation = (
                f"Su sicakliginiz yuksek ({current_flow_temp:.0f}C), "
                f"kombi konvansiyonel modda calisiyor. "
                f"50-55C'ye dusurmek %{abs(savings_pct):.0f} tasarruf saglar. "
                "Yogusmali verimden faydalanmak icin 55C altinda kalin."
            )

        return EfficiencyAnalysis(
            current_flow_temp=current_flow_temp,
            current_efficiency_pct=round(current_eff * 100, 1),
            optimal_flow_temp=optimal_temp,
            optimal_efficiency_pct=round(optimal_eff * 100, 1),
            potential_savings_pct=round(max(savings_pct, 0), 1),
            is_condensing=is_condensing,
            recommendation=recommendation,
        )

    def compare_flow_temps(
        self,
        runtime_hours: float,
        flow_temps: list[float] | None = None,
        gas_price: float | None = None,
    ) -> list[dict]:
        """Farkli akis sicakliklarini karsilastir.

        Args:
            runtime_hours: Karsilastirma icin baz calisma suresi.
            flow_temps: Karsilastirilacak sicakliklar. None ise tumu.
            gas_price: Gaz fiyati.

        Returns:
            Her sicaklik icin tuketim/maliyet/verim listesi.
        """
        if flow_temps is None:
            flow_temps = [40.0, 45.0, 50.0, 55.0, 60.0, 65.0, 70.0, 75.0, 80.0]

        comparisons = []
        for ft in flow_temps:
            gas = self.calculate_gas_consumption(runtime_hours, ft)
            kwh, kcal = self.calculate_thermal_output(gas, ft)
            cost = self.calculate_cost(gas, gas_price)
            eff = self.interpolate_efficiency(ft)

            comparisons.append({
                "flow_temp": ft,
                "gas_m3": gas,
                "thermal_kwh": kwh,
                "thermal_kcal": kcal,
                "cost_tl": cost,
                "efficiency_pct": round(eff * 100, 1),
                "is_condensing": ft <= 55,
            })

        return comparisons


# Singleton instance
energy_calculator = EnergyCalculator()
