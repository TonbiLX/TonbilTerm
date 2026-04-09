package com.tonbil.termostat.ui.component

import android.view.View
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.ui.layout.layout
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonbil.termostat.ui.theme.HeatingOrange
import com.tonbil.termostat.ui.theme.TempCold
import com.tonbil.termostat.ui.theme.TempCool
import com.tonbil.termostat.ui.theme.TempHot
import com.tonbil.termostat.ui.theme.TempWarm
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilSurfaceVariant
import com.tonbil.termostat.util.Formatters
import com.tonbil.termostat.util.HapticHelper
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ARC_SWEEP = 270f
private const val ARC_START = 135f // bottom-left start (135 degrees from 3 o'clock)
private const val STEP = 0.5f

@Composable
fun ThermostatDial(
    currentTemp: Float,
    targetTemp: Float,
    onTargetChange: (Float) -> Unit,
    minTemp: Float = 15f,
    maxTemp: Float = 30f,
    isHeating: Boolean = false,
    mode: String = "auto",
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var dragTarget by remember { mutableFloatStateOf(targetTemp) }
    var isDragging by remember { mutableStateOf(false) }
    var lastStepTemp by remember { mutableFloatStateOf(targetTemp) }
    val textMeasurer = rememberTextMeasurer()

    // Sync external changes when not dragging
    if (!isDragging) {
        dragTarget = targetTemp
        lastStepTemp = targetTemp
    }

    val displayTarget = if (isDragging) dragTarget else targetTemp

    // Pulse animation for heating glow
    val pulseTransition = rememberInfiniteTransition(label = "dialPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isEnabled) {
                        Modifier.pointerInput(minTemp, maxTemp) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    val newTemp = offsetToTemp(offset, size, minTemp, maxTemp)
                                    if (newTemp != null) {
                                        dragTarget = newTemp
                                        lastStepTemp = newTemp
                                        HapticHelper.tickFeedback(view)
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val newTemp = offsetToTemp(
                                        change.position,
                                        size,
                                        minTemp,
                                        maxTemp,
                                    )
                                    if (newTemp != null) {
                                        dragTarget = newTemp
                                        // Fire haptic on each 0.5 step
                                        if (newTemp != lastStepTemp) {
                                            if (newTemp <= minTemp || newTemp >= maxTemp) {
                                                HapticHelper.boundaryFeedback(view)
                                            } else {
                                                HapticHelper.tickFeedback(view)
                                            }
                                            lastStepTemp = newTemp
                                        }
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    HapticHelper.confirmFeedback(view)
                                    onTargetChange(dragTarget)
                                },
                                onDragCancel = {
                                    isDragging = false
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            val padding = size.width * 0.12f
            val arcRect = Size(size.width - padding * 2, size.height - padding * 2)
            val arcTopLeft = Offset(padding, padding)

            // Pulse glow ring when heating
            if (isHeating) {
                val glowPadding = padding - 14f * pulseScale
                val glowRect = Size(
                    size.width - glowPadding * 2,
                    size.height - glowPadding * 2,
                )
                drawArc(
                    color = HeatingOrange.copy(alpha = pulseAlpha * 0.5f),
                    startAngle = ARC_START,
                    sweepAngle = ARC_SWEEP,
                    useCenter = false,
                    topLeft = Offset(glowPadding, glowPadding),
                    size = glowRect,
                    style = Stroke(width = 42f, cap = StrokeCap.Round),
                )
            }

            // Background arc
            drawArc(
                color = if (isEnabled) TonbilSurfaceVariant else TonbilSurfaceVariant.copy(alpha = 0.4f),
                startAngle = ARC_START,
                sweepAngle = ARC_SWEEP,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcRect,
                style = Stroke(width = 28f, cap = StrokeCap.Round),
            )

            // Filled arc (gradient based on target)
            val fraction = ((displayTarget - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
            val filledSweep = ARC_SWEEP * fraction

            val gradientColors = if (!isEnabled) listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.5f))
                else if (isHeating) listOf(HeatingOrange.copy(alpha = 0.8f), HeatingOrange)
                else getGradientColors(displayTarget, minTemp, maxTemp)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = gradientColors,
                    center = Offset(size.width / 2f, size.height / 2f),
                ),
                startAngle = ARC_START,
                sweepAngle = filledSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcRect,
                style = Stroke(width = 28f, cap = StrokeCap.Round),
            )

            // Tick marks every 1 degree, labeled every 5 degrees
            drawTickMarks(
                center = Offset(size.width / 2f, size.height / 2f),
                radius = (arcRect.width / 2f) + 6f,
                minTemp = minTemp,
                maxTemp = maxTemp,
                textMeasurer = textMeasurer,
            )

            // Thumb circle
            val thumbAngle = ARC_START + filledSweep
            val thumbRadians = Math.toRadians(thumbAngle.toDouble())
            val thumbRadius = arcRect.width / 2f
            val thumbCenter = Offset(
                x = size.width / 2f + thumbRadius * cos(thumbRadians).toFloat(),
                y = size.height / 2f + thumbRadius * sin(thumbRadians).toFloat(),
            )

            // Thumb (hidden in disabled/manual mode)
            if (isEnabled) {
                // Thumb shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.3f),
                    radius = 26f,
                    center = thumbCenter + Offset(2f, 3f),
                )
                // Thumb outer
                drawCircle(
                    color = Color.White,
                    radius = 24f,
                    center = thumbCenter,
                )
                // Thumb inner (accent)
                val thumbInnerColor = if (isHeating) HeatingOrange else gradientColors.last()
                drawCircle(
                    color = thumbInnerColor,
                    radius = 16f,
                    center = thumbCenter,
                )
            }

            // Inner glow when heating
            if (isHeating) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Radial warm glow behind center text
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            HeatingOrange.copy(alpha = 0.12f * pulseScale),
                            HeatingOrange.copy(alpha = 0.04f),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = arcRect.width * 0.38f,
                    ),
                    radius = arcRect.width * 0.38f,
                    center = Offset(cx, cy),
                )
            }
        }

        // Center text overlay
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!isEnabled) {
                // Manuel mod — alev ikonu + mevcut sicaklik
                if (isHeating) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Isitiliyor",
                        tint = HeatingOrange,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Text(
                        text = "Manuel Mod",
                        style = MaterialTheme.typography.labelSmall,
                        color = TonbilOnSurfaceVariant,
                    )
                }

                // Mevcut sicaklik (buyuk)
                Text(
                    text = Formatters.formatTempShort(currentTemp),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 52.sp,
                    ),
                    color = if (isHeating) HeatingOrange else TonbilOnSurfaceVariant.copy(alpha = 0.7f),
                )

                Text(
                    text = if (isHeating) "Isitiliyor" else "Mevcut Sicaklik",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isHeating) HeatingOrange else TonbilOnSurfaceVariant.copy(alpha = 0.5f),
                )
            } else {
                if (isHeating) {
                    // Alev ikonu — Material3 LocalFireDepartment
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Isitiliyor",
                        tint = HeatingOrange,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    // Mode text
                    Text(
                        text = Formatters.modeToTurkish(mode),
                        style = MaterialTheme.typography.labelSmall,
                        color = TonbilOnSurfaceVariant,
                    )
                }

                // Target temperature (big)
                Text(
                    text = Formatters.formatTempShort(displayTarget),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 52.sp,
                    ),
                    color = if (isHeating) HeatingOrange else TonbilOnSurface,
                )

                // Current temperature
                Text(
                    text = "Mevcut: ${Formatters.formatTemp(currentTemp)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TonbilOnSurfaceVariant,
                )

                if (isHeating) {
                    Text(
                        text = "Isitiliyor",
                        style = MaterialTheme.typography.labelLarge,
                        color = HeatingOrange,
                    )
                }
            }
        }
    }

    // - / + buttons below dial (hidden in manual/disabled mode)
    if (isEnabled) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            IconButton(
                onClick = {
                    val newTemp = (displayTarget - STEP).coerceAtLeast(minTemp)
                    if (newTemp != displayTarget) {
                        HapticHelper.tickFeedback(view)
                        onTargetChange(newTemp)
                    }
                },
            ) {
                Text(
                    text = "\u2212",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TonbilOnSurface,
                )
            }

            Spacer(modifier = Modifier.width(48.dp))

            IconButton(
                onClick = {
                    val newTemp = (displayTarget + STEP).coerceAtMost(maxTemp)
                    if (newTemp != displayTarget) {
                        HapticHelper.tickFeedback(view)
                        onTargetChange(newTemp)
                    }
                },
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TonbilOnSurface,
                )
            }
        }
    }
    } // end Column wrapper
}

// ── Helpers ──

private fun offsetToTemp(
    offset: Offset,
    size: androidx.compose.ui.unit.IntSize,
    minTemp: Float,
    maxTemp: Float,
): Float? {
    val center = Offset(size.width / 2f, size.height / 2f)
    val dx = offset.x - center.x
    val dy = offset.y - center.y

    // atan2 returns angle from positive X-axis, clockwise
    var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (angleDeg < 0) angleDeg += 360f

    // Convert to arc-relative angle (start at ARC_START)
    var arcAngle = angleDeg - ARC_START
    if (arcAngle < 0) arcAngle += 360f

    // Reject if in the gap area (bottom)
    if (arcAngle > ARC_SWEEP + 15f) return null
    if (arcAngle < -15f) return null

    val fraction = (arcAngle / ARC_SWEEP).coerceIn(0f, 1f)
    val rawTemp = minTemp + fraction * (maxTemp - minTemp)

    // Snap to 0.5 steps
    return (rawTemp * 2).roundToInt() / 2f
}

private fun getGradientColors(temp: Float, minTemp: Float, maxTemp: Float): List<Color> {
    val fraction = ((temp - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
    return when {
        fraction < 0.33f -> listOf(TempCold, TempCool)
        fraction < 0.66f -> listOf(TempCool, TempWarm)
        else -> listOf(TempWarm, TempHot)
    }
}

private fun DrawScope.drawTickMarks(
    center: Offset,
    radius: Float,
    minTemp: Float,
    maxTemp: Float,
    textMeasurer: TextMeasurer,
) {
    val totalSteps = ((maxTemp - minTemp) / 1f).toInt()
    val tickRadius = radius + 8f
    val labelRadius = radius + 28f

    for (i in 0..totalSteps) {
        val temp = minTemp + i
        val fraction = i.toFloat() / totalSteps
        val angle = ARC_START + fraction * ARC_SWEEP
        val rad = Math.toRadians(angle.toDouble())

        val isLabel = (temp.toInt() % 5 == 0)
        val tickLength = if (isLabel) 10f else 5f
        val tickColor = if (isLabel) Color(0xFFCAC4D0) else Color(0xFF938F99).copy(alpha = 0.5f)

        val innerPoint = Offset(
            x = center.x + (tickRadius) * cos(rad).toFloat(),
            y = center.y + (tickRadius) * sin(rad).toFloat(),
        )
        val outerPoint = Offset(
            x = center.x + (tickRadius + tickLength) * cos(rad).toFloat(),
            y = center.y + (tickRadius + tickLength) * sin(rad).toFloat(),
        )

        drawLine(
            color = tickColor,
            start = innerPoint,
            end = outerPoint,
            strokeWidth = if (isLabel) 2f else 1f,
        )

        // Label every 5 degrees
        if (isLabel) {
            val labelPoint = Offset(
                x = center.x + labelRadius * cos(rad).toFloat(),
                y = center.y + labelRadius * sin(rad).toFloat(),
            )
            val label = "${temp.toInt()}°"
            val textResult = textMeasurer.measure(
                text = label,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = Color(0xFFCAC4D0),
                    textAlign = TextAlign.Center,
                ),
            )
            drawText(
                textLayoutResult = textResult,
                topLeft = Offset(
                    x = labelPoint.x - textResult.size.width / 2f,
                    y = labelPoint.y - textResult.size.height / 2f,
                ),
            )
        }
    }
}

private fun DrawScope.drawFlameShape(center: Offset, flameSize: Float, color: Color) {
    val path = androidx.compose.ui.graphics.Path().apply {
        // Tepe noktası
        moveTo(center.x, center.y - flameSize)
        // Sol taraf — dışa doğru kavisli, aşağı inen
        cubicTo(
            center.x - flameSize * 0.55f, center.y - flameSize * 0.5f,
            center.x - flameSize * 0.5f, center.y + flameSize * 0.15f,
            center.x - flameSize * 0.2f, center.y + flameSize * 0.5f,
        )
        // Alt orta — yuvarlak taban
        cubicTo(
            center.x - flameSize * 0.1f, center.y + flameSize * 0.7f,
            center.x + flameSize * 0.1f, center.y + flameSize * 0.7f,
            center.x + flameSize * 0.2f, center.y + flameSize * 0.5f,
        )
        // Sağ taraf — yukarı çıkan kavis
        cubicTo(
            center.x + flameSize * 0.5f, center.y + flameSize * 0.15f,
            center.x + flameSize * 0.55f, center.y - flameSize * 0.5f,
            center.x, center.y - flameSize,
        )
        close()
    }
    drawPath(path = path, color = color)
}
