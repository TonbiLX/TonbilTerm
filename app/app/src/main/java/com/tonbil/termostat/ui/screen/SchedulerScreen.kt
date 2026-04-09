package com.tonbil.termostat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.model.ScheduleEntry
import com.tonbil.termostat.data.repository.TonbilRepository
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.HeatingOrange
import com.tonbil.termostat.ui.theme.OnlineGreen
import com.tonbil.termostat.ui.theme.TonbilBackground
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// 0=Pazartesi, 6=Pazar (backend formatı)
private val DAYS_SHORT = listOf("Pzt", "Sal", "Car", "Per", "Cum", "Cmt", "Paz")
private val DAYS_FULL = listOf("Pazartesi", "Sali", "Carsamba", "Persembe", "Cuma", "Cumartesi", "Pazar")

@Composable
fun SchedulerScreen(
    repository: TonbilRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ScheduleEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableIntStateOf(0) } // 0=Pazartesi
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<ScheduleEntry?>(null) }
    var pendingDeleteEntry by remember { mutableStateOf<ScheduleEntry?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        repository.getScheduleEntries()
            .onSuccess { entries = it }
            .onFailure { errorMsg = "Program yuklenemedi: ${it.message}" }
        isLoading = false
    }

    fun saveEntries(updated: List<ScheduleEntry>) {
        scope.launch {
            isSaving = true
            repository.updateScheduleEntries(updated)
                .onSuccess { entries = it }
                .onFailure { errorMsg = "Kaydedilemedi: ${it.message}" }
            isSaving = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TonbilBackground)
            .verticalScroll(rememberScrollState())
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
                    text = "Program",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TonbilPrimary,
                )
                Text(
                    text = "Haftalik isitma programi",
                    style = MaterialTheme.typography.bodySmall,
                    color = TonbilOnSurfaceVariant,
                )
            }
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = TonbilPrimary)
            }
        }

        errorMsg?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = TonbilPrimary)
            }
        } else {
            // Day selector tabs
            DaySelector(
                selectedDay = selectedDay,
                onDaySelected = { selectedDay = it },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Entries for selected day
            val dayEntries = entries.filter { it.dayOfWeek == selectedDay }
                .sortedBy { it.hour * 60 + it.minute }

            if (dayEntries.isEmpty()) {
                EmptyDayState(
                    dayName = DAYS_FULL.getOrElse(selectedDay) { "?" },
                    onAddClick = { showAddDialog = true },
                )
            } else {
                Text(
                    text = "${DAYS_FULL.getOrElse(selectedDay) { "?" }} Programi",
                    style = MaterialTheme.typography.titleMedium,
                    color = TonbilOnSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))

                dayEntries.forEach { entry ->
                    ScheduleEntryCard(
                        entry = entry,
                        onDelete = {
                            val updated = entries.filter { it !== entry }
                            saveEntries(updated)
                        },
                        onToggle = { enabled ->
                            val updated = entries.map {
                                if (it === entry) it.copy(enabled = enabled) else it
                            }
                            saveEntries(updated)
                        },
                        onEdit = { editingEntry = entry },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TonbilPrimary.copy(alpha = 0.15f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = TonbilPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Program Ekle", color = TonbilPrimary)
                }
            }

            // Weekly overview summary
            Spacer(modifier = Modifier.height(24.dp))
            WeeklyOverview(entries = entries)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Add entry dialog
    if (showAddDialog) {
        AddScheduleDialog(
            day = selectedDay,
            onDismiss = { showAddDialog = false },
            onConfirm = { newEntry ->
                val updated = entries + newEntry
                saveEntries(updated)
                showAddDialog = false
            },
        )
    }

    // Edit entry dialog
    editingEntry?.let { target ->
        EditScheduleDialog(
            entry = target,
            onDismiss = { editingEntry = null },
            onConfirm = { updated ->
                val newList = entries.map { if (it === target) updated else it }
                saveEntries(newList)
                editingEntry = null
            },
        )
    }
}

@Composable
private fun DaySelector(
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DAYS_SHORT.forEachIndexed { key, label ->
            val isSelected = key == selectedDay
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) TonbilPrimary else CardDark)
                    .clickable { onDaySelected(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color(0xFF4A2800) else TonbilOnSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun EmptyDayState(dayName: String, onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = TonbilOnSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$dayName icin henuz program yok",
                style = MaterialTheme.typography.bodyMedium,
                color = TonbilOnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onAddClick) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Program Ekle")
            }
        }
    }
}

@Composable
private fun ScheduleEntryCard(
    entry: ScheduleEntry,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Enable/disable toggle
            androidx.compose.material3.Switch(
                checked = entry.enabled,
                onCheckedChange = onToggle,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedTrackColor = OnlineGreen,
                ),
                modifier = Modifier.size(36.dp, 20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "%02d:%02d".format(entry.hour, entry.minute),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (entry.enabled) TonbilPrimary else TonbilOnSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Hedef: ${"%.1f".format(entry.targetTemp)}°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = TonbilOnSurfaceVariant,
                )
            }
            // Temperature badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(HeatingOrange.copy(alpha = if (entry.enabled) 0.15f else 0.07f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${"%.0f".format(entry.targetTemp)}°C",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (entry.enabled) HeatingOrange else TonbilOnSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Duzenle",
                    tint = TonbilPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Sil",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun WeeklyOverview(entries: List<ScheduleEntry>) {
    if (entries.isEmpty()) return

    Text(
        text = "Haftalik Ozet",
        style = MaterialTheme.typography.titleMedium,
        color = TonbilOnSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            DAYS_SHORT.forEachIndexed { key, label ->
                val dayEntries = entries.filter { it.dayOfWeek == key }.sortedBy { it.hour * 60 + it.minute }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = TonbilOnSurfaceVariant,
                        modifier = Modifier.width(40.dp),
                    )
                    if (dayEntries.isEmpty()) {
                        Text(
                            text = "Program yok",
                            style = MaterialTheme.typography.bodySmall,
                            color = TonbilOnSurfaceVariant.copy(alpha = 0.5f),
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dayEntries.take(4).forEach { entry ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(TonbilPrimary.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "${"%02d:%02d".format(entry.hour, entry.minute)} ${entry.targetTemp.toInt()}°",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TonbilPrimary,
                                    )
                                }
                            }
                            if (dayEntries.size > 4) {
                                Text(
                                    text = "+${dayEntries.size - 4}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TonbilOnSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddScheduleDialog(
    day: Int,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleEntry) -> Unit,
) {
    var hour by remember { mutableStateOf(7) }
    var minute by remember { mutableStateOf(0) }
    var targetTemp by remember { mutableFloatStateOf(22f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = {
            Text(
                text = "Program Ekle - ${DAYS_FULL.getOrElse(day) { "?" }}",
                style = MaterialTheme.typography.titleMedium,
                color = TonbilOnSurface,
            )
        },
        text = {
            Column {
                // Time picker
                Text(
                    text = "Saat: ${"%02d".format(hour)}:${"%02d".format(minute)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TonbilPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Saat (0-23)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TonbilOnSurfaceVariant,
                )
                Slider(
                    value = hour.toFloat(),
                    onValueChange = { hour = it.toInt() },
                    valueRange = 0f..23f,
                    steps = 22,
                    colors = SliderDefaults.colors(thumbColor = TonbilPrimary, activeTrackColor = TonbilPrimary),
                )

                Text(
                    text = "Dakika (0/15/30/45)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TonbilOnSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 15, 30, 45).forEach { m ->
                        val sel = minute == m
                        OutlinedButton(
                            onClick = { minute = m },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (sel) TonbilPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (sel) TonbilPrimary else TonbilOnSurfaceVariant,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("$m", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Temperature picker
                Text(
                    text = "Hedef Sicaklik: ${"%.1f".format(targetTemp)}°C",
                    style = MaterialTheme.typography.bodyLarge,
                    color = HeatingOrange,
                    fontWeight = FontWeight.Bold,
                )
                Slider(
                    value = targetTemp,
                    onValueChange = { targetTemp = (it * 2).toInt() / 2f },
                    valueRange = 14f..28f,
                    steps = 27,
                    colors = SliderDefaults.colors(thumbColor = HeatingOrange, activeTrackColor = HeatingOrange),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("14°C", style = MaterialTheme.typography.labelSmall, color = TonbilOnSurfaceVariant)
                    Text("28°C", style = MaterialTheme.typography.labelSmall, color = TonbilOnSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val time = "${"%02d".format(hour)}:${"%02d".format(minute)}"
                    onConfirm(ScheduleEntry(dayOfWeek = day, hour = hour, minute = minute, targetTemp = targetTemp))
                },
                colors = ButtonDefaults.buttonColors(containerColor = TonbilPrimary),
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kaydet", color = Color(0xFF4A2800))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Iptal", color = TonbilOnSurfaceVariant)
            }
        },
    )
}

@Composable
private fun EditScheduleDialog(
    entry: ScheduleEntry,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleEntry) -> Unit,
) {
    var hour by remember { mutableStateOf(entry.hour) }
    var minute by remember { mutableStateOf(entry.minute) }
    var targetTemp by remember { mutableFloatStateOf(entry.targetTemp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = {
            Text(
                text = "Program Duzenle",
                style = MaterialTheme.typography.titleMedium,
                color = TonbilOnSurface,
            )
        },
        text = {
            Column {
                Text(
                    text = "Saat: ${"%02d".format(hour)}:${"%02d".format(minute)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TonbilPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Saat (0-23)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TonbilOnSurfaceVariant,
                )
                Slider(
                    value = hour.toFloat(),
                    onValueChange = { hour = it.toInt() },
                    valueRange = 0f..23f,
                    steps = 22,
                    colors = SliderDefaults.colors(thumbColor = TonbilPrimary, activeTrackColor = TonbilPrimary),
                )

                Text(
                    text = "Dakika (0/15/30/45)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TonbilOnSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 15, 30, 45).forEach { m ->
                        val sel = minute == m
                        OutlinedButton(
                            onClick = { minute = m },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (sel) TonbilPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (sel) TonbilPrimary else TonbilOnSurfaceVariant,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("$m", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Hedef Sicaklik: ${"%.1f".format(targetTemp)}°C",
                    style = MaterialTheme.typography.bodyLarge,
                    color = HeatingOrange,
                    fontWeight = FontWeight.Bold,
                )
                Slider(
                    value = targetTemp,
                    onValueChange = { targetTemp = (it * 2).toInt() / 2f },
                    valueRange = 14f..28f,
                    steps = 27,
                    colors = SliderDefaults.colors(thumbColor = HeatingOrange, activeTrackColor = HeatingOrange),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("14°C", style = MaterialTheme.typography.labelSmall, color = TonbilOnSurfaceVariant)
                    Text("28°C", style = MaterialTheme.typography.labelSmall, color = TonbilOnSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(entry.copy(hour = hour, minute = minute, targetTemp = targetTemp))
                },
                colors = ButtonDefaults.buttonColors(containerColor = TonbilPrimary),
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kaydet", color = Color(0xFF4A2800))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Iptal", color = TonbilOnSurfaceVariant)
            }
        },
    )
}
