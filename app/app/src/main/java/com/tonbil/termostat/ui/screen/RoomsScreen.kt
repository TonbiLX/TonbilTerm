package com.tonbil.termostat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Desk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Living
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.model.Room
import com.tonbil.termostat.data.repository.TonbilRepository
import com.tonbil.termostat.ui.component.RoomCard
import com.tonbil.termostat.ui.component.RoomDetailSheet
import com.tonbil.termostat.ui.theme.BoostRed
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.TonbilBackground
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import com.tonbil.termostat.ui.theme.TonbilSurfaceVariant
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private data class RoomIconOption(val key: String, val label: String, val icon: ImageVector)

private val roomIconOptions = listOf(
    RoomIconOption("kitchen", "Mutfak", Icons.Default.Kitchen),
    RoomIconOption("living", "Salon", Icons.Default.Living),
    RoomIconOption("bedroom", "Yatak", Icons.Default.Bed),
    RoomIconOption("child", "Cocuk", Icons.Default.ChildCare),
    RoomIconOption("bathroom", "Banyo", Icons.Default.Bathtub),
    RoomIconOption("desk", "Calisma", Icons.Default.Desk),
    RoomIconOption("garage", "Garaj", Icons.Default.Garage),
    RoomIconOption("default", "Diger", Icons.Default.Living),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(
    repository: TonbilRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var showRoomDialog by remember { mutableStateOf(false) }
    var editingRoom by remember { mutableStateOf<Room?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Room?>(null) }
    var longPressedRoom by remember { mutableStateOf<Room?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var selectedRoomDetail by remember { mutableStateOf<Room?>(null) }

    fun reloadRooms() {
        scope.launch {
            isLoading = true
            repository.getRooms().fold(
                onSuccess = { rooms = it; errorMsg = null },
                onFailure = { errorMsg = "Odalar yuklenemedi: ${it.message}" },
            )
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reloadRooms() }

    if (showRoomDialog) {
        RoomFormSheet(
            room = editingRoom,
            onDismiss = { showRoomDialog = false; editingRoom = null },
            onSave = { name, icon, weight ->
                scope.launch {
                    val room = editingRoom
                    if (room == null) {
                        repository.createRoom(name, icon, weight)
                    } else {
                        repository.updateRoom(room.id, name, icon, weight)
                    }
                    showRoomDialog = false
                    editingRoom = null
                    reloadRooms()
                }
            },
        )
    }

    showDeleteDialog?.let { room ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Odayi Sil", color = TonbilOnSurface) },
            text = {
                Text(
                    text = "\"${room.name}\" odasini silmek istediginize emin misiniz?",
                    color = TonbilOnSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deleteRoom(room.id)
                        showDeleteDialog = null
                        reloadRooms()
                    }
                }) {
                    Text("Sil", color = BoostRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Iptal", color = TonbilOnSurfaceVariant)
                }
            },
            containerColor = CardDark,
            titleContentColor = TonbilOnSurface,
            textContentColor = TonbilOnSurfaceVariant,
        )
    }

    selectedRoomDetail?.let { room ->
        RoomDetailSheet(
            room = room,
            onDismiss = { selectedRoomDetail = null },
        )
    }

    Scaffold(
        containerColor = TonbilBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingRoom = null; showRoomDialog = true },
                containerColor = TonbilPrimary,
                contentColor = Color(0xFF4A2800),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Oda Ekle")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TonbilBackground)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Odalar",
                style = MaterialTheme.typography.headlineMedium,
                color = TonbilPrimary,
            )
            Text(
                text = "Oda bazli sicaklik takibi",
                style = MaterialTheme.typography.bodySmall,
                color = TonbilOnSurfaceVariant,
            )
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
            } else if (rooms.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Henuz oda tanimlanmamis",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TonbilOnSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Oda eklemek icin + tusuna basin",
                            style = MaterialTheme.typography.bodySmall,
                            color = TonbilOnSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(rooms, key = { it.id }) { room ->
                        var roomWeight by remember(room.id, room.weight) {
                            mutableFloatStateOf(room.weight ?: 0.5f)
                        }
                        val weightChanged = roomWeight != (room.weight ?: 0.5f)

                        Box {
                            Column {
                                RoomCard(
                                    room = room,
                                    modifier = Modifier.pointerInput(room.id) {
                                        detectTapGestures(
                                            onTap = { selectedRoomDetail = room },
                                            onLongPress = {
                                                longPressedRoom = room
                                                showContextMenu = true
                                            },
                                        )
                                    },
                                )
                                // Inline ağırlık slider
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = CardDark),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Isitma Agirligi",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TonbilOnSurfaceVariant,
                                            )
                                            Text(
                                                text = "%${"%.0f".format(roomWeight * 100)}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = TonbilPrimary,
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Slider(
                                                value = roomWeight,
                                                onValueChange = { roomWeight = it },
                                                valueRange = 0f..1f,
                                                steps = 19,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = TonbilPrimary,
                                                    activeTrackColor = TonbilPrimary,
                                                    inactiveTrackColor = TonbilSurfaceVariant,
                                                ),
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (weightChanged) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            repository.updateRoom(
                                                                room.id,
                                                                room.name,
                                                                room.icon ?: "default",
                                                                roomWeight,
                                                            )
                                                            reloadRooms()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = TonbilPrimary),
                                                    modifier = Modifier.height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                ) {
                                                    Text(
                                                        "Kaydet",
                                                        color = Color(0xFF4A2800),
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (longPressedRoom?.id == room.id) {
                                DropdownMenu(
                                    expanded = showContextMenu,
                                    onDismissRequest = {
                                        showContextMenu = false
                                        longPressedRoom = null
                                    },
                                    modifier = Modifier.background(CardDark),
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = null,
                                                    tint = TonbilPrimary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Duzenle", color = TonbilOnSurface)
                                            }
                                        },
                                        onClick = {
                                            editingRoom = room
                                            showRoomDialog = true
                                            showContextMenu = false
                                            longPressedRoom = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = BoostRed,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Sil", color = BoostRed)
                                            }
                                        },
                                        onClick = {
                                            showDeleteDialog = room
                                            showContextMenu = false
                                            longPressedRoom = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomFormSheet(
    room: Room?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, weight: Float) -> Unit,
) {
    var name by remember { mutableStateOf(room?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(room?.icon ?: "kitchen") }
    var weight by remember { mutableFloatStateOf(room?.weight ?: 0.5f) }
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
                text = if (room == null) "Oda Ekle" else "Odayi Duzenle",
                style = MaterialTheme.typography.titleLarge,
                color = TonbilOnSurface,
            )
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Oda Adi", color = TonbilOnSurfaceVariant) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text("Oda adi bos birakilamaz", color = BoostRed) }
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
                text = "Ikon",
                style = MaterialTheme.typography.labelLarge,
                color = TonbilOnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(2.dp),
            ) {
                items(roomIconOptions) { option ->
                    val isSelected = selectedIcon == option.key
                    Card(
                        onClick = { selectedIcon = option.key },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) TonbilPrimary.copy(alpha = 0.2f)
                            else TonbilSurfaceVariant.copy(alpha = 0.3f),
                        ),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(
                            1.5.dp, TonbilPrimary,
                        ) else null,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = option.label,
                                tint = if (isSelected) TonbilPrimary else TonbilOnSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) TonbilPrimary else TonbilOnSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Isitma Agirligi",
                    style = MaterialTheme.typography.labelLarge,
                    color = TonbilOnSurfaceVariant,
                )
                Text(
                    text = "%${"%.0f".format(weight * 100)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = TonbilPrimary,
                )
            }
            Slider(
                value = weight,
                onValueChange = { weight = it },
                valueRange = 0f..1f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = TonbilPrimary,
                    activeTrackColor = TonbilPrimary,
                    inactiveTrackColor = TonbilSurfaceVariant,
                ),
            )

            Spacer(modifier = Modifier.height(20.dp))

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
                            onSave(name.trim(), selectedIcon, weight)
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
