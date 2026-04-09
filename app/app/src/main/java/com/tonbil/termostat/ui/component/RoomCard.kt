package com.tonbil.termostat.ui.component

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
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.model.Room
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.TempCold
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import com.tonbil.termostat.util.Formatters
import com.tonbil.termostat.util.roomIcon

@Composable
fun RoomCard(
    room: Room,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = roomIcon(room.icon),
                    contentDescription = null,
                    tint = TonbilPrimary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TonbilOnSurface,
                    )
                    Text(
                        text = "${room.deviceCount} cihaz",
                        style = MaterialTheme.typography.bodySmall,
                        color = TonbilOnSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current temperature
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Mevcut",
                        style = MaterialTheme.typography.labelSmall,
                        color = TonbilOnSurfaceVariant,
                    )
                    Text(
                        text = room.currentTemp?.let { Formatters.formatTemp(it) } ?: "--",
                        style = MaterialTheme.typography.titleLarge,
                        color = TonbilOnSurface,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Agirlik",
                        style = MaterialTheme.typography.labelSmall,
                        color = TonbilOnSurfaceVariant,
                    )
                    Text(
                        text = "${"%.0f".format(room.weight * 100)}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = TonbilPrimary,
                    )
                }
            }

            room.currentHumidity?.let { humidity ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = TempCold,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = Formatters.formatHumidity(humidity),
                        style = MaterialTheme.typography.bodySmall,
                        color = TonbilOnSurfaceVariant,
                    )
                }
            }
        }
    }
}
