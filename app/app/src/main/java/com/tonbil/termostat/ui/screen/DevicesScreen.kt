package com.tonbil.termostat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.model.Device
import com.tonbil.termostat.data.model.Room
import com.tonbil.termostat.data.repository.TonbilRepository
import com.tonbil.termostat.ui.theme.BoostRed
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.OfflineGray
import com.tonbil.termostat.ui.theme.OnlineGreen
import com.tonbil.termostat.ui.theme.TonbilBackground
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import com.tonbil.termostat.ui.theme.TonbilSurfaceVariant
import com.tonbil.termostat.util.Formatters
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    repository: TonbilRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var rooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var editingDevice by remember { mutableStateOf<Device?>(null) }
    var deletingDevice by remember { mutableStateOf<Device?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }

    fun reloadDevices() {
        scope.launch {
            isLoading = true
            repository.getDevices().fold(
                onSuccess = { devices = it; errorMsg = null },
                onFailure = { errorMsg = "Cihazlar yuklenemedi: ${it.message}" },
            )
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        reloadDevices()
        repository.getRooms().onSuccess { rooms = it }
    }

    // Delete confirmation dialog
    deletingDevice?.let { device ->
        AlertDialog(
            onDismissRequest = { deletingDevice = null },
            title = { Text("Cihazi Sil") },
            text = { Text("\"${device.name.ifBlank { device.deviceId }}\" cihazini silmek istediginize emin misiniz?\n\nCihaz tekrar baglandiginda otomatik kaydedilecektir.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteDevice(device.deviceId)
                            deletingDevice = null
                            reloadDevices()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BoostRed),
                ) { Text("Sil", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { deletingDevice = null }) { Text("Iptal") }
            },
        )
    }

    // Collect WS device status updates
    LaunchedEffect(Unit) {
        repository.deviceStatusUpdates.collect { wsStatus ->
            devices = devices.map {
                if (it.deviceId == wsStatus.deviceId) it.copy(isOnline = wsStatus.isOnline)
                else it
            }
        }
    }

    // Edit device bottom sheet
    editingDevice?.let { device ->
        DeviceEditSheet(
            device = device,
            rooms = rooms,
            onDismiss = { editingDevice = null },
            onSave = { name, roomId ->
                scope.launch {
                    repository.updateDevice(device.deviceId, name, roomId)
                    editingDevice = null
                    reloadDevices()
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TonbilBackground)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Cihazlar",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TonbilPrimary,
                )
                Row {
                    Text(
                        text = "${devices.count { it.isOnline }} cevrimici",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnlineGreen,
                    )
                    Text(
                        text = " / ${devices.size} toplam",
                        style = MaterialTheme.typography.bodySmall,
                        color = TonbilOnSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            scanning = true
                            scanMessage = null
                            val beforeCount = devices.size
                            kotlinx.coroutines.delay(10_000)
                            repository.getDevices().fold(
                                onSuccess = {
                                    devices = it
                                    val newCount = it.size - beforeCount
                                    scanMessage = if (newCount > 0) "$newCount yeni cihaz bulundu!" else "Yeni cihaz bulunamadi"
                                },
                                onFailure = { scanMessage = "Tarama hatasi" },
                            )
                            scanning = false
                        }
                    },
                    enabled = !scanning,
                    colors = ButtonDefaults.buttonColors(containerColor = TonbilSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (scanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TonbilPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Tara", tint = TonbilOnSurface, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (scanning) "Taraniyor..." else "Tara", color = TonbilOnSurface, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        scanMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = if (msg.contains("bulundu")) Color(0xFF1B5E20).copy(alpha = 0.2f) else TonbilSurfaceVariant),
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TonbilOnSurface,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TonbilPrimary)
            }
        } else if (errorMsg != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = errorMsg ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Henuz cihaz tanimlanmamis",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TonbilOnSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        onEdit = { editingDevice = device },
                        onDelete = { deletingDevice = device },
                        onRelayToggle = { command ->
                            scope.launch { repository.sendCommand(device.deviceId, command) }
                        },
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: Device,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRelayToggle: (String) -> Unit,
) {
    val isRelayType = device.type.lowercase() in listOf("relay", "combo", "controller")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Icon(
                        imageVector = deviceTypeIcon(device.type),
                        contentDescription = null,
                        tint = TonbilPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (device.isOnline) OnlineGreen else OfflineGray)
                            .align(Alignment.BottomEnd),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TonbilOnSurface,
                    )
                    device.roomName?.let { room ->
                        Text(
                            text = room,
                            style = MaterialTheme.typography.bodySmall,
                            color = TonbilOnSurfaceVariant,
                        )
                    }
                    DeviceTypeLabel(type = device.type)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        device.firmwareVersion?.let { fw ->
                            Text(
                                text = "v$fw",
                                style = MaterialTheme.typography.labelSmall,
                                color = TonbilOnSurfaceVariant,
                            )
                        }
                        device.ipAddress?.let { ip ->
                            Text(
                                text = ip,
                                style = MaterialTheme.typography.labelSmall,
                                color = TonbilOnSurfaceVariant,
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (device.isOnline) "Cevrimici" else "Cevrimdisi",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (device.isOnline) OnlineGreen else OfflineGray,
                    )
                    device.lastSeen?.let { ts ->
                        Text(
                            text = Formatters.formatTimestamp(ts),
                            style = MaterialTheme.typography.labelSmall,
                            color = TonbilOnSurfaceVariant,
                        )
                    }
                }
            }

            // Relay control for combo/relay devices
            if (isRelayType) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onRelayToggle("relay_on") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("AC", color = Color.Black)
                    }
                    Button(
                        onClick = { onRelayToggle("relay_off") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BoostRed),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("KAPAT", color = Color.White)
                    }
                }
            }

            // Delete button
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = BoostRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cihazi Sil", color = BoostRed, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceEditSheet(
    device: Device,
    rooms: List<Room>,
    onDismiss: () -> Unit,
    onSave: (name: String, roomId: Int?) -> Unit,
) {
    var name by remember { mutableStateOf(device.name) }
    var selectedRoom by remember {
        mutableStateOf(rooms.firstOrNull { it.id == device.roomId })
    }
    var roomDropdownExpanded by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(
                text = "Cihazi Duzenle",
                style = MaterialTheme.typography.titleLarge,
                color = TonbilOnSurface,
            )
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Cihaz Adi", color = TonbilOnSurfaceVariant) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text("Cihaz adi bos birakilamaz", color = BoostRed) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TonbilPrimary,
                    unfocusedBorderColor = TonbilSurfaceVariant,
                    focusedTextColor = TonbilOnSurface,
                    unfocusedTextColor = TonbilOnSurface,
                    cursorColor = TonbilPrimary,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Oda",
                style = MaterialTheme.typography.labelLarge,
                color = TonbilOnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = roomDropdownExpanded,
                onExpandedChange = { roomDropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedRoom?.name ?: "Oda secilmedi",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roomDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TonbilPrimary,
                        unfocusedBorderColor = TonbilSurfaceVariant,
                        focusedTextColor = TonbilOnSurface,
                        unfocusedTextColor = TonbilOnSurface,
                    ),
                )
                ExposedDropdownMenu(
                    expanded = roomDropdownExpanded,
                    onDismissRequest = { roomDropdownExpanded = false },
                    modifier = Modifier.background(CardDark),
                ) {
                    DropdownMenuItem(
                        text = { Text("Oda secilmedi", color = TonbilOnSurfaceVariant) },
                        onClick = { selectedRoom = null; roomDropdownExpanded = false },
                    )
                    rooms.forEach { room ->
                        DropdownMenuItem(
                            text = { Text(room.name, color = TonbilOnSurface) },
                            onClick = { selectedRoom = room; roomDropdownExpanded = false },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Iptal", color = TonbilOnSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            nameError = true
                        } else {
                            onSave(name.trim(), selectedRoom?.id)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TonbilPrimary),
                ) {
                    Text("Kaydet", color = Color(0xFF4A2800))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeviceTypeLabel(type: String) {
    val (icon, label) = when (type.lowercase()) {
        "sensor" -> Icons.Default.Thermostat to "Sadece Sensor"
        "combo" -> Icons.Default.LocalFireDepartment to "Sensor + Role"
        "relay", "controller" -> Icons.Default.Power to "Sadece Role"
        else -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TonbilOnSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TonbilOnSurfaceVariant,
        )
    }
}

private fun deviceTypeIcon(type: String): ImageVector = when (type.lowercase()) {
    "sensor", "temperature" -> Icons.Default.Sensors
    "thermostat" -> Icons.Default.DeviceThermostat
    "relay", "controller" -> Icons.Default.Memory
    "gateway" -> Icons.Default.Router
    "combo" -> Icons.Default.DeviceThermostat
    else -> Icons.Default.Sensors
}
