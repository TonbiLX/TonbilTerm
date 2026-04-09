package com.tonbil.termostat.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.model.SensorHistoryPoint
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant

private val PressurePurple = Color(0xFF9C27B0)
private val PressurePurpleLight = Color(0xFFCE93D8)

@Composable
fun PressureChart(
    data: List<SensorHistoryPoint>,
    modifier: Modifier = Modifier,
) {
    // Pressure verisi olmayan noktaları filtrele; hepsi null ise gizle
    val pressurePoints = data.filter { it.pressure != null }
    if (pressurePoints.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Atmosfer Basinci (hPa)",
                style = MaterialTheme.typography.titleMedium,
                color = TonbilOnSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            val pressures = pressurePoints.map { it.pressure!! }
            val minPres = pressures.min()
            val maxPres = pressures.max()
            val avgPres = pressures.average().toFloat()

            PressureCanvas(
                points = pressurePoints,
                minPres = minPres,
                maxPres = maxPres,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // X-axis saat etiketleri
            PressureXLabels(points = pressurePoints)

            Spacer(modifier = Modifier.height(10.dp))

            // Min / Ort / Maks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PressureStat(
                    label = "Min",
                    value = "${"%.0f".format(minPres)} hPa",
                    color = PressurePurpleLight,
                )
                PressureStat(
                    label = "Ort",
                    value = "${"%.0f".format(avgPres)} hPa",
                    color = PressurePurple,
                )
                PressureStat(
                    label = "Maks",
                    value = "${"%.0f".format(maxPres)} hPa",
                    color = PressurePurpleLight,
                )
            }
        }
    }
}

@Composable
private fun PressureCanvas(
    points: List<SensorHistoryPoint>,
    minPres: Float,
    maxPres: Float,
    modifier: Modifier = Modifier,
) {
    val lineColor = PressurePurple
    val fillStartColor = PressurePurple.copy(alpha = 0.3f)
    val fillEndColor = PressurePurple.copy(alpha = 0f)
    val gridColor = Color.White.copy(alpha = 0.08f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 4f

        // Basınç değişimi çok küçük olsa bile en az 1 hPa aralık garanti
        val presRange = (maxPres - minPres).coerceAtLeast(1f)

        fun xFor(i: Int) = pad + (i.toFloat() / (points.size - 1).coerceAtLeast(1)) * (w - pad * 2)
        fun yFor(pres: Float) = h - pad - ((pres - minPres) / presRange) * (h - pad * 2)

        // Yatay grid çizgileri (3 seviye)
        repeat(3) { level ->
            val y = pad + level * (h - pad * 2) / 2
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }

        if (points.size < 2) return@Canvas

        // Dolgu alanı
        val fillPath = Path().apply {
            moveTo(xFor(0), h)
            lineTo(xFor(0), yFor(points[0].pressure!!))
            for (i in 1 until points.size) {
                lineTo(xFor(i), yFor(points[i].pressure!!))
            }
            lineTo(xFor(points.size - 1), h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillStartColor, fillEndColor),
                startY = 0f,
                endY = h,
            ),
        )

        // Çizgi
        val linePath = Path().apply {
            moveTo(xFor(0), yFor(points[0].pressure!!))
            for (i in 1 until points.size) {
                lineTo(xFor(i), yFor(points[i].pressure!!))
            }
        }

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Son nokta vurgusu
        val lastX = xFor(points.size - 1)
        val lastY = yFor(points.last().pressure!!)
        drawCircle(color = lineColor, radius = 4f, center = Offset(lastX, lastY))
        drawCircle(color = Color.White, radius = 2f, center = Offset(lastX, lastY))
    }
}

@Composable
private fun PressureXLabels(points: List<SensorHistoryPoint>) {
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
                text = formatPressureHourLabel(points[idx].time),
                style = MaterialTheme.typography.labelSmall,
                color = TonbilOnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PressureStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TonbilOnSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

private fun formatPressureHourLabel(iso: String): String {
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = parser.parse(iso) ?: return ""
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        fmt.format(date)
    } catch (_: Exception) {
        ""
    }
}
