"""Hysteresis isitma kontrol algoritmasi.

Pure fonksiyon - side effect yok, kolayca test edilebilir.
"""


def weighted_average(temps: list[float], weights: list[float]) -> float:
    """Agirlikli ortalama hesapla.

    Args:
        temps: Sensor sicakliklari listesi.
        weights: Her sensora karsilik gelen agirliklar (oda agirligi).

    Returns:
        Agirlikli ortalama sicaklik.

    Raises:
        ValueError: Bos liste veya toplam agirlik sifirsa.
    """
    if not temps or not weights or len(temps) != len(weights):
        raise ValueError("Sicaklik ve agirlik listeleri bos olamaz ve ayni uzunlukta olmali")

    total_weight = sum(weights)
    if total_weight == 0:
        raise ValueError("Toplam agirlik sifir olamaz")

    return sum(t * w for t, w in zip(temps, weights)) / total_weight


def evaluate_heating(
    temps: list[float],
    weights: list[float],
    target: float,
    hysteresis: float,
    current_relay_state: bool,
    strategy: str = "weighted_avg",
) -> bool:
    """Isitma durumunu degerlendir.

    Dead-band (hysteresis) mantigi:
    - Sicaklik < (target - hysteresis) ise -> ISIT (relay ON)
    - Sicaklik > (target + hysteresis) ise -> DURDUR (relay OFF)
    - Aradaysa -> mevcut durumu koru (dead-band)

    Stratejiler:
    - weighted_avg: Agirlikli ortalama (varsayilan)
    - coldest_room: En soguk oda baz alinir
    - hottest_room: En sicak oda baz alinir
    - single_room: Ilk sensor (tekli oda sistemi)

    Args:
        temps: Sensor sicakliklari.
        weights: Oda agirliklari.
        target: Hedef sicaklik.
        hysteresis: Hysteresis bandi (+-).
        current_relay_state: Mevcut role durumu.
        strategy: Kullanilacak strateji.

    Returns:
        True ise relay AC, False ise relay KAPAT.
    """
    if not temps:
        return current_relay_state

    # Stratejiye gore efektif sicakligi hesapla
    match strategy:
        case "weighted_avg":
            effective_temp = weighted_average(temps, weights)
        case "coldest_room":
            effective_temp = min(temps)
        case "hottest_room":
            effective_temp = max(temps)
        case "single_room":
            effective_temp = temps[0]
        case _:
            effective_temp = weighted_average(temps, weights)

    # Dead-band (hysteresis) kontrol
    lower_bound = target - hysteresis
    upper_bound = target + hysteresis

    if effective_temp < lower_bound:
        # Hedefin altinda - isit
        return True
    elif effective_temp > upper_bound:
        # Hedefin ustunde - durdur
        return False
    else:
        # Dead-band icinde - mevcut durumu koru
        return current_relay_state
