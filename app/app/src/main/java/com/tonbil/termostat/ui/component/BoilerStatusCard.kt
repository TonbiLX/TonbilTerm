package com.tonbil.termostat.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.model.BoilerStatus
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.HeatingOrange
import com.tonbil.termostat.ui.theme.OfflineGray
import com.tonbil.termostat.ui.theme.OnlineGreen
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.util.Formatters

@Composable
fun BoilerStatusCard(
    boilerStatus: BoilerStatus?,
    relayState: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Relay can come from WS boiler message or from heating config
    val isRelayOn = boilerStatus?.relay == true || relayState
    val isActive = boilerStatus?.active == true

    val statusColor by animateColorAsState(
        targetValue = when {
            isActive -> HeatingOrange
            isRelayOn -> OnlineGreen
            else -> OfflineGray
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "boilerStatusColor",
    )

    // Flame pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "flame")
    val flameScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flameScale",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isActive) Modifier.border(
                    width = 1.dp,
                    color = HeatingOrange.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                ) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF3D1111) else CardDark,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.LocalFireDepartment else Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier
                        .size(28.dp)
                        .then(
                            if (isActive) Modifier.graphicsLayer(
                                scaleX = flameScale,
                                scaleY = flameScale,
                            ) else Modifier
                        ),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kombi Durumu",
                        style = MaterialTheme.typography.titleMedium,
                        color = TonbilOnSurface,
                    )
                    Text(
                        text = when {
                            isActive -> "Yanma aktif"
                            isRelayOn -> "Role acik"
                            else -> "Kapali"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                    )
                }
                Text(
                    text = Formatters.modeToTurkish(boilerStatus?.mode ?: "auto"),

                    style = MaterialTheme.typography.labelLarge,
                    color = TonbilOnSurfaceVariant,
                )
            }

            if (boilerStatus != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Hedef",
                            style = MaterialTheme.typography.labelSmall,
                            color = TonbilOnSurfaceVariant,
                        )
                        Text(
                            text = Formatters.formatTemp(boilerStatus.target?.toFloat() ?: 22f),
                            style = MaterialTheme.typography.bodySmall,
                            color = TonbilOnSurface,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Role",
                            style = MaterialTheme.typography.labelSmall,
                            color = TonbilOnSurfaceVariant,
                        )
                        Text(
                            text = if (boilerStatus.relay) "Acik" else "Kapali",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (boilerStatus.relay) OnlineGreen else OfflineGray,
                        )
                    }
                }
            }
        }
    }
}
