package com.tonbil.termostat.util

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToInt

object Formatters {

    private val isoParser = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale("tr"))
    private val localZone = ZoneId.systemDefault()

    fun formatTemp(temp: Float, decimals: Int = 1): String {
        return if (decimals == 0) {
            "${temp.roundToInt()}°C"
        } else {
            "${"%.${decimals}f".format(temp)}°C"
        }
    }

    fun formatTempShort(temp: Float): String {
        return if (temp == temp.toInt().toFloat()) {
            "${temp.toInt()}°"
        } else {
            "${"%.1f".format(temp)}°"
        }
    }

    fun formatHumidity(humidity: Float): String = "${humidity.roundToInt()}%"

    fun formatPercent(value: Float): String = "${value.roundToInt()}%"

    fun formatKwh(kwh: Float): String = "${"%.1f".format(kwh)} kWh"

    fun formatCurrency(amount: Float, currency: String = "TRY"): String {
        val symbol = when (currency) {
            "TRY" -> "\u20BA"
            "USD" -> "$"
            "EUR" -> "\u20AC"
            else -> currency
        }
        return "${"%.2f".format(amount)} $symbol"
    }

    fun formatHours(hours: Float): String {
        val h = hours.toInt()
        val m = ((hours - h) * 60).roundToInt()
        return if (h > 0) "${h}s ${m}dk" else "${m}dk"
    }

    fun formatTimestamp(iso: String): String {
        return try {
            // Fractional seconds varsa truncate et
            val cleaned = iso.substringBefore('.')
            val utcDateTime = LocalDateTime.parse(cleaned, isoParser)
            val localDateTime = utcDateTime.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(localZone)
                .toLocalDateTime()
            localDateTime.format(timeFormatter)
        } catch (_: DateTimeParseException) {
            iso
        }
    }

    fun formatDate(iso: String): String {
        return try {
            val cleaned = iso.substringBefore('.')
            val utcDateTime = LocalDateTime.parse(cleaned, isoParser)
            val localDateTime = utcDateTime.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(localZone)
                .toLocalDateTime()
            localDateTime.format(dateTimeFormatter)
        } catch (_: DateTimeParseException) {
            iso
        }
    }

    fun modeToTurkish(mode: String): String = when (mode.lowercase()) {
        "auto" -> "Otomatik"
        "manual" -> "Manuel"
        "off" -> "Kapali"
        "boost" -> "Boost"
        "schedule" -> "Program"
        "eco" -> "Eko"
        else -> mode
    }

    /**
     * Gun indexi -> Turkce gun adi.
     * Backend 0-indexed kullanir (0=Pazartesi, 6=Pazar).
     * 1-indexed (1=Pazartesi, 7=Pazar) de desteklenir (geriye uyumluluk).
     */
    fun dayOfWeekTurkish(day: Int): String = when (day) {
        0 -> "Pazartesi"
        1 -> "Sali"
        2 -> "Carsamba"
        3 -> "Persembe"
        4 -> "Cuma"
        5 -> "Cumartesi"
        6 -> "Pazar"
        else -> "?"
    }
}
