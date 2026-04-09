package com.tonbil.termostat.ui.component

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.model.Device
import com.tonbil.termostat.data.model.Room
import com.tonbil.termostat.data.model.SensorHistoryPoint
import com.tonbil.termostat.data.repository.TonbilRepository
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.OnlineGreen
import com.tonbil.termostat.ui.theme.TempCold
import com.tonbil.termostat.ui.theme.TonbilBackground
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import com.tonbil.termostat.ui.theme.TonbilSurfaceVariant
import com.tonbil.termostat.util.Formatters
import com.tonbil.termostat.util.roomIcon
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val HISTORY_RANGES = listOf(
    "24h" to "24S",
    "7d" to "7G",
    "30d" to "30G",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailSheet(
    room: Room,
    onDismiss: () -> Unit,
    repository: TonbilRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var historyPoints by remember { mutableStateOf<List<SensorHistoryPoint>>(emptyList()) }
    var isLoadingHistory by remember { mutableStateOf(false) }
    var selectedRange by remember { mutableStateOf("24h") }
    var roomDevices by remember { mutableStateOf<List<Device>>(emptyList()) }

    // Cihazlari yukle
    LaunchedEffect(room.id) {
        repository.getDevices().onSuccess { devices ->
            roomDevices = devices.filter { it.roomId == room.id }
        }
    }

    // Grafik gecmisini yukle (range degisince yenile)
    LaunchedEffect(room.id, selectedRange) {
        isLoadingHistory = true
        val deviceId = roomDevices.firstOrNull()?.deviceId
            ?: repository.getDevices().getOrNull()
                ?.firstOrNull { it.roomId == room.id }
                ?.deviceId
        if (deviceId != null) {
            repository.getSensorHistory(deviceId, selectedRange)
                .onSuccess { historyPoints = it.points }
        }
        isLoadingHistory = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TonbilBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // Room header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(TonbilPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = roomIcon(room.icon),
                        contentDescription = null,
                        tint = TonbilPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = TonbilOnSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${room.deviceCount} cihaz",
                        style = MaterialTheme.typography.bodySmall,
                        color = TonbilOnSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Big numbers: temp, humidity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                room.currentTemp?.let { temp ->
                    BigStatItem(
                        icon = Icons.Default.Thermostat,
                        label = "Sicaklik",
                        value = Formatters.formatTemp(temp),
                        color = TonbilPrimary,
                    )
                }
                room.currentHumidity?.let { hum ->
                    BigStatItem(
                        icon = Icons.Default.WaterDrop,
                        label = "Nem",
                        value = Formatters.formatHumidity(hum),
                        color = TempCold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grafik zaman araligi secici
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HISTORY_RANGES.forEach { (rangeKey, rangeLabel) ->
                    FilterChip(
                        selected = selectedRange == rangeKey,
                        onClick = { selectedRange = rangeKey },
                        label = {
                            Text(
                                text = rangeLabel,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TonbilPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = TonbilPrimary,
                            containerColor = CardDark,
                            labelColor = TonbilOnSurfaceVariant,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Grafik
            if (isLoadingHistory) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = TonbilPrimary)
                }
            } else {
                TemperatureSparkline(points = historyPoints)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bagli cihazlar listesi
            if (roomDevices.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Bagli Cihazlar",
                            style = MaterialTheme.typography.labelLarge,
                            color = TonbilOnSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        roomDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (device.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = if (device.isOnline) OnlineGreen else TonbilOnSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name.ifBlank { device.deviceId },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TonbilOnSurface,
                                    )
                                    Text(
                                        text = device.type.ifBlank { "Sensor" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TonbilOnSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = if (device.isOnline) "Cevrimici" else "Cevrimdisi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (device.isOnline) OnlineGreen else TonbilOnSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                // Placeholder: yukleniyor veya cihaz yok
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = OnlineGreen,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Cihaz Durumu",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TonbilOnSurface,
                            )
                            Text(
                                text = "${room.deviceCount} cihaz tanimli",
                                style = MaterialTheme.typography.bodySmall,
                                color = TonbilOnSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigStatItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TonbilOnSurfaceVariant,
        )
    }
}
