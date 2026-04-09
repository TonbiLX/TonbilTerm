package com.tonbil.termostat.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.model.SensorHistoryPoint
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.InfoBlue
import com.tonbil.termostat.ui.theme.TempCold
import com.tonbil.termostat.ui.theme.TempHot
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun TemperatureSparkline(
    points: List<SensorHistoryPoint>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Başlık + Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "24 Saatlik Sicaklik & Nem",
                    style = MaterialTheme.typography.titleMedium,
                    color = TonbilOnSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LegendItem(color = TonbilPrimary, label = "Sicaklik", dashed = false)
                    LegendItem(color = InfoBlue, label = "Nem", dashed = true)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Veri yok",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TonbilOnSurfaceVariant,
                    )
                }
            } else {
                val temps = points.map { it.temperature }
                val minTemp = temps.min()
                val maxTemp = temps.max()
                val avgTemp = temps.average().toFloat()

                val humidities = points.map { it.humidity }
                val minHum = humidities.min()
                val maxHum = humidities.max()
                val avgHum = humidities.average().toFloat()

                SparklineCanvas(
                    points = points,
                    minTemp = minTemp,
                    maxTemp = maxTemp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // X ekseni etiketleri
                SparklineXLabels(points = points)

                Spacer(modifier = Modifier.height(12.dp))

                // Sicaklik istatistikleri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(TonbilPrimary, CircleShape),
                    )
                    Text(
                        text = "Sicaklik",
                        style = MaterialTheme.typography.labelSmall,
                        color = TonbilOnSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    SparklineStatInline(
                        label = "Min",
                        value = "${"%.1f".format(minTemp)}°",
                        color = TempCold,
                    )
                    Text(
                        text = "  |  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = TonbilOnSurfaceVariant,
                    )
                    SparklineStatInline(
                        label = "Ort",
                        value = "${"%.1f".format(avgTemp)}°",
                        color = TonbilPrimary,
                    )
                    Text(
                        text = "  |  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = TonbilOnSurfaceVariant,
                    )
                    SparklineStatInline(
                        label = "Maks",
                        value = "${"%.1f".format(maxTemp)}°",
                        color = TempHot,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Nem istatistikleri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(InfoBlue, CircleShape),
                    )
                    Text(
                        text = "Nem",
                        style = MaterialTheme.typography.labelSmall,
                        color = TonbilOnSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    SparklineStatInline(
                        label = "Min",
                        value = "${"%.0f".format(minHum)}%",
                        color = InfoBlue.copy(alpha = 0.8f),
                    )
                    Text(
                        text = "  |  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = TonbilOnSurfaceVariant,
                    )
                    SparklineStatInline(
                        label = "Ort",
                        value = "${"%.0f".format(avgHum)}%",
                        color = InfoBlue,
                    )
                    Text(
                        text = "  |  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = TonbilOnSurfaceVariant,
                    )
                    SparklineStatInline(
                        label = "Maks",
                        value = "${"%.0f".format(maxHum)}%",
                        color = InfoBlue.copy(alpha = 1.2f).copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dashed: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(width = 18.dp, height = 8.dp)) {
            val dashEffect = if (dashed) {
                PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
            } else null
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2.5f,
                pathEffect = dashEffect,
                cap = StrokeCap.Round,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TonbilOnSurfaceVariant,
        )
    }
}

@Composable
private fun SparklineCanvas(
    points: List<SensorHistoryPoint>,
    minTemp: Float,
    maxTemp: Float,
    modifier: Modifier = Modifier,
) {
    val tempLineColor = TonbilPrimary
    val tempFillStart = TonbilPrimary.copy(alpha = 0.28f)
    val tempFillEnd = TonbilPrimary.copy(alpha = 0f)
    val humLineColor = InfoBlue
    val gridColor = Color.White.copy(alpha = 0.08f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padding = 4f

        val drawH = h - padding * 2

        // Sicaklik scale
        val tempRange = (maxTemp - minTemp).coerceAtLeast(1f)
        // Nem scale: 0-100, ama gosterim icin min/max civarinda
        val humVals = points.map { it.humidity }
        val minHum = humVals.min()
        val maxHum = humVals.max()
        val humRange = (maxHum - minHum).coerceAtLeast(5f)

        fun xFor(i: Int) = padding + (i.toFloat() / (points.size - 1).coerceAtLeast(1)) * (w - padding * 2)
        fun yForTemp(temp: Float) = h - padding - ((temp - minTemp) / tempRange) * drawH
        // Nem Y ekseni: canvas'in alt %40'inda cizilir (sicakligin altinda kalsin)
        fun yForHum(hum: Float): Float {
            val humAreaTop = h * 0.55f
            val humAreaBot = h - padding
            val humAreaH = humAreaBot - humAreaTop
            return humAreaBot - ((hum - minHum) / humRange) * humAreaH
        }

        // Yatay grid cizgileri (3 seviye)
        repeat(3) { level ->
            val y = padding + level * drawH / 2
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }

        if (points.size < 2) return@Canvas

        // Sicaklik dolgu alani
        val fillPath = Path().apply {
            moveTo(xFor(0), h)
            lineTo(xFor(0), yForTemp(points[0].temperature))
            for (i in 1 until points.size) {
                lineTo(xFor(i), yForTemp(points[i].temperature))
            }
            lineTo(xFor(points.size - 1), h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(tempFillStart, tempFillEnd),
                startY = 0f,
                endY = h,
            ),
        )

        // Sicaklik cizgisi
        val tempLinePath = Path().apply {
            moveTo(xFor(0), yForTemp(points[0].temperature))
            for (i in 1 until points.size) {
                lineTo(xFor(i), yForTemp(points[i].temperature))
            }
        }

        drawPath(
            path = tempLinePath,
            color = tempLineColor,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Son nokta icin nokta
        val lastTempX = xFor(points.size - 1)
        val lastTempY = yForTemp(points.last().temperature)
        drawCircle(color = tempLineColor, radius = 4f, center = Offset(lastTempX, lastTempY))
        drawCircle(color = Color.White, radius = 2f, center = Offset(lastTempX, lastTempY))

        // Nem kesikli cizgisi
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
        val humPath = Path().apply {
            moveTo(xFor(0), yForHum(points[0].humidity))
            for (i in 1 until points.size) {
                lineTo(xFor(i), yForHum(points[i].humidity))
            }
        }

        drawPath(
            path = humPath,
            color = humLineColor,
            style = Stroke(
                width = 2f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = dashEffect,
            ),
        )

        // Nem son nokta
        val lastHumX = xFor(points.size - 1)
        val lastHumY = yForHum(points.last().humidity)
        drawCircle(color = humLineColor, radius = 3.5f, center = Offset(lastHumX, lastHumY))
        drawCircle(color = Color.White, radius = 1.8f, center = Offset(lastHumX, lastHumY))
    }
}

@Composable
private fun SparklineXLabels(points: List<SensorHistoryPoint>) {
    if (points.isEmpty()) return

    val labelIndices = listOf(0, points.size / 4, points.size / 2, 3 * points.size / 4, points.size - 1)
        .distinct()
        .filter { it < points.size }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labelIndices.forEach { idx ->
            Text(
                text = formatHourLabel(points[idx].time),
                style = MaterialTheme.typography.labelSmall,
                color = TonbilOnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SparklineStatInline(label: String, value: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = TonbilOnSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

private fun formatHourLabel(iso: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(iso) ?: return ""
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        fmt.format(date)
    } catch (_: Exception) {
        ""
    }
}
