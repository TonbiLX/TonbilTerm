package com.tonbil.termostat.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.tonbil.termostat.data.model.HeatingProfile
import com.tonbil.termostat.data.model.Room
import com.tonbil.termostat.data.model.SensorReading
import com.tonbil.termostat.ui.component.BoilerStatusCard
import com.tonbil.termostat.ui.component.BoostControl
import com.tonbil.termostat.ui.component.PressureChart
import com.tonbil.termostat.ui.component.TemperatureSparkline
import com.tonbil.termostat.ui.component.ThermostatDial
import com.tonbil.termostat.ui.component.WeatherWidget
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.HeatingOrange
import com.tonbil.termostat.ui.theme.OfflineGray
import com.tonbil.termostat.ui.theme.OnlineGreen
import com.tonbil.termostat.ui.theme.TonbilBackground
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import com.tonbil.termostat.util.HapticHelper
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = koinViewModel(),
    showSnackbar: ((String, Boolean) -> Unit)? = null,
) {
    val view = LocalView.current

    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val heatingConfig by viewModel.heatingConfig.collectAsState()
    val sensors by viewModel.sensors.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val boilerStatus by viewModel.boilerStatus.collectAsState()
    val boostConfig by viewModel.boostConfig.collectAsState()
    val wsConnected by viewModel.wsConnected.collectAsState()
    val relayLoading by viewModel.relayLoading.collectAsState()
    val modeLoading by viewModel.modeLoading.collectAsState()
    val historyPoints by viewModel.historyPoints.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val runtimeToday by viewModel.runtimeToday.collectAsState()
    val runtimeWeekly by viewModel.runtimeWeekly.collectAsState()

    // UI-only state
    var showProfileDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<HeatingProfile?>(null) }

    // Alerts / bell state
    val alertRepository: com.tonbil.termostat.data.repository.TonbilRepository = org.koin.compose.koinInject()
    var alerts by remember { mutableStateOf<List<com.tonbil.termostat.data.model.EnergyAlert>>(emptyList()) }
    var dismissedAlertIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bellOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                alertRepository.getEnergyAlerts().onSuccess { alerts = it }
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(5 * 60 * 1000L)
        }
    }

    val visibleAlerts = alerts.filter { "${it.type}-${it.title}" !in dismissedAlertIds }

    val rooms by viewModel.rooms.collectAsState()
    val selectedRoomId by viewModel.selectedRoomId.collectAsState()

    val isRelayOn = boilerStatus?.relay == true || heatingConfig.relayState
    val currentMode = heatingConfig.mode
    val currentTemp = viewModel.calculateEffectiveTemp(rooms, sensors, heatingConfig.strategy)

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refreshData() },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TonbilBackground)
            .pullRefresh(pullRefreshState),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                        text = "TonbilTerm",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TonbilPrimary,
                    )
                    Text(
                        text = "Akilli Termostat",
                        style = MaterialTheme.typography.bodySmall,
                        color = TonbilOnSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (wsConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (wsConnected) OnlineGreen else OfflineGray,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Bell icon
                    Box {
                        IconButton(onClick = { bellOpen = !bellOpen }) {
                            BadgedBox(
                                badge = {
                                    if (visibleAlerts.isNotEmpty()) {
                                        Badge { Text("${visibleAlerts.size}") }
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = "Bildirimler",
                                    tint = TonbilOnSurfaceVariant,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = bellOpen,
                            onDismissRequest = { bellOpen = false },
                        ) {
                            if (visibleAlerts.isEmpty()) {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    Text("Bildirim yok", color = TonbilOnSurfaceVariant)
                                }
                            } else {
                                visibleAlerts.forEach { alert ->
                                    val alertId = "${alert.type}-${alert.title}"
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = CardDark),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFFF9800),
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(alert.title, style = MaterialTheme.typography.labelMedium, color = TonbilOnSurface)
                                                Text(alert.message, style = MaterialTheme.typography.bodySmall, color = TonbilOnSurfaceVariant)
                                            }
                                            IconButton(
                                                onClick = { dismissedAlertIds = dismissedAlertIds + alertId },
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = TonbilOnSurfaceVariant, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Yenile",
                            tint = TonbilOnSurfaceVariant,
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = TonbilPrimary)
                }
            } else {
                val isManualMode = heatingConfig.mode in listOf("manual", "manual_on", "manual_off")
                ThermostatDial(
                    currentTemp = currentTemp,
                    targetTemp = heatingConfig.targetTemp,
                    onTargetChange = { newTarget ->
                        if (!isManualMode) {
                            viewModel.updateTargetTemp(newTarget)
                        }
                    },
                    isHeating = heatingConfig.relayState || (boilerStatus?.relay == true),
                    mode = if (boostConfig?.active == true) "boost" else heatingConfig.mode,
                    isEnabled = !isManualMode,
                )

                // Task 1: Relay Toggle Button — sadece manuel modda aktif
                RelayToggleButton(
                    isRelayOn = isRelayOn,
                    isLoading = relayLoading,
                    isEnabled = isManualMode,
                    onToggle = {
                        val targetRelayState = !isRelayOn
                        if (targetRelayState) HapticHelper.relayOnFeedback(view)
                        else HapticHelper.relayOffFeedback(view)

                        viewModel.toggleRelay(
                            currentRelayOn = isRelayOn,
                            onSuccess = { msg -> showSnackbar?.invoke(msg, true) },
                            onFailure = { msg -> showSnackbar?.invoke(msg, false) },
                        )
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Task 2: Auto/Manual Mode Toggle
                ModeToggle(
                    currentMode = currentMode,
                    isLoading = modeLoading,
                    onModeSelect = { newMode ->
                        viewModel.selectMode(
                            newMode = newMode,
                            onSuccess = { msg -> showSnackbar?.invoke(msg, true) },
                            onFailure = { msg -> showSnackbar?.invoke(msg, false) },
                        )
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Strategy Chips
                StrategyChips(
                    currentStrategy = heatingConfig.strategy,
                    onStrategySelect = { newStrategy ->
                        viewModel.updateStrategy(
                            newStrategy = newStrategy,
                            onSuccess = { msg -> showSnackbar?.invoke(msg, true) },
                            onFailure = { msg -> showSnackbar?.invoke(msg, false) },
                        )
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Task 3: Profile Selector (dinamik)
                ProfileSelector(
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    onProfileSelect = { profile ->
                        viewModel.selectProfile(
                            profile = profile,
                            onSuccess = { msg -> showSnackbar?.invoke(msg, true) },
                            onFailure = { msg -> showSnackbar?.invoke(msg, false) },
                        )
                    },
                    onAddProfile = { showProfileDialog = true },
                    onEditProfile = { profile ->
                        editingProfile = profile
                        showProfileDialog = true
                    },
                )

                // Profile Dialog
                if (showProfileDialog) {
                    ProfileDialog(
                        profile = editingProfile,
                        onDismiss = {
                            showProfileDialog = false
                            editingProfile = null
                        },
                        onSave = { name, icon, targetTemp, hysteresis ->
                            viewModel.saveProfile(
                                editingProfile = editingProfile,
                                name = name,
                                icon = icon,
                                targetTemp = targetTemp,
                                hysteresis = hysteresis,
                                onSuccess = { msg -> showSnackbar?.invoke(msg, true) },
                                onFailure = { msg -> showSnackbar?.invoke(msg, false) },
                            )
                            showProfileDialog = false
                            editingProfile = null
                        },
                        onDelete = if (editingProfile != null && !editingProfile!!.isDefault) {
                            { id ->
                                viewModel.deleteProfile(
                                    id = id,
                                    onSuccess = { msg -> showSnackbar?.invoke(msg, true) },
                                    onFailure = { msg -> showSnackbar?.invoke(msg, false) },
                                )
                                showProfileDialog = false
                                editingProfile = null
                            }
                        } else {
                            null
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                BoilerStatusCard(boilerStatus = boilerStatus, relayState = heatingConfig.relayState)

                Spacer(modifier = Modifier.height(12.dp))

                RuntimeCard(
                    runtimeToday = runtimeToday,
                    runtimeWeekly = runtimeWeekly,
                )

                Spacer(modifier = Modifier.height(12.dp))

                BoostControl(
                    boostConfig = boostConfig,
                    onActivate = { minutes -> viewModel.activateBoost(minutes) },
                    onCancel = { viewModel.cancelBoost() },
                )

                Spacer(modifier = Modifier.height(12.dp))

                WeatherWidget(weather = weather)

                Spacer(modifier = Modifier.height(12.dp))

                RoomSelectorChips(
                    rooms = rooms,
                    selectedRoomId = selectedRoomId,
                    onRoomSelect = { viewModel.selectRoom(it) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                TemperatureSparkline(points = historyPoints)

                // Basınç grafiği: pressure verisi olan noktalar varsa göster
                if (historyPoints.any { it.pressure != null }) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PressureChart(data = historyPoints)
                }

                if (sensors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sensor Verileri",
                        style = MaterialTheme.typography.titleMedium,
                        color = TonbilOnSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    sensors.forEach { sensor ->
                        SensorRow(sensor)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Task 4: Pull-to-refresh indicator overlay
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = CardDark,
            contentColor = TonbilPrimary,
        )
    }
}

@Composable
private fun RelayToggleButton(
    isRelayOn: Boolean,
    isLoading: Boolean,
    isEnabled: Boolean = true,
    onToggle: () -> Unit,
) {
    Button(
        onClick = onToggle,
        enabled = isEnabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRelayOn) Color(0xFFB71C1C) else Color(0xFF1B5E20),
            contentColor = Color.White,
            disabledContainerColor = OfflineGray,
            disabledContentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = if (isRelayOn) Icons.Default.PowerSettingsNew else Icons.Default.LocalFireDepartment,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isRelayOn) "Kombi KAPAT" else "Kombi AC",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ModeToggle(
    currentMode: String,
    isLoading: Boolean,
    onModeSelect: (String) -> Unit,
) {
    Column {
        Text(
            text = "Calisma Modu",
            style = MaterialTheme.typography.titleSmall,
            color = TonbilOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val autoSelected = currentMode == "auto"
            if (autoSelected) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TonbilPrimary,
                        contentColor = Color(0xFF4A2800),
                        disabledContainerColor = TonbilPrimary,
                        disabledContentColor = Color(0xFF4A2800),
                    ),
                ) {
                    Text("Otomatik", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedButton(
                    onClick = { if (!isLoading) onModeSelect("auto") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TonbilOnSurfaceVariant),
                ) {
                    Text("Otomatik", style = MaterialTheme.typography.labelLarge)
                }
            }

            val manualSelected = currentMode in listOf("manual", "manual_on", "manual_off")
            if (manualSelected) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TonbilPrimary,
                        contentColor = Color(0xFF4A2800),
                        disabledContainerColor = TonbilPrimary,
                        disabledContentColor = Color(0xFF4A2800),
                    ),
                ) {
                    Text("Manuel", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedButton(
                    onClick = { if (!isLoading) onModeSelect("manual") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TonbilOnSurfaceVariant),
                ) {
                    Text("Manuel", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (currentMode == "auto") {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Otomatik kontrol aktif",
                style = MaterialTheme.typography.bodySmall,
                color = OnlineGreen,
            )
        }
        if (isLoading) {
            Spacer(modifier = Modifier.height(4.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = TonbilPrimary,
                strokeWidth = 2.dp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileSelector(
    profiles: List<HeatingProfile>,
    activeProfileId: Int?,
    onProfileSelect: (HeatingProfile) -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (HeatingProfile) -> Unit,
) {
    Column {
        Text(
            text = "Isitma Profili",
            style = MaterialTheme.typography.titleSmall,
            color = TonbilOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            profiles.forEach { profile ->
                val isSelected = profile.id == activeProfileId
                FilterChip(
                    selected = isSelected,
                    onClick = { onProfileSelect(profile) },
                    label = {
                        Text(
                            text = if (isSelected) "${profile.name} (${profile.targetTemp.toInt()}°C)" else profile.name,
                            maxLines = 1,
                        )
                    },
                    leadingIcon = {
                        ProfileIcon(
                            iconName = profile.icon,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TonbilPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = TonbilPrimary,
                        selectedLeadingIconColor = TonbilPrimary,
                    ),
                    modifier = Modifier.combinedClickable(
                        onClick = { onProfileSelect(profile) },
                        onLongClick = { onEditProfile(profile) },
                    ),
                )
            }
            // "+" chip for adding new profile
            FilterChip(
                selected = false,
                onClick = onAddProfile,
                label = { Text("+") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Yeni profil",
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun ProfileIcon(iconName: String, modifier: Modifier = Modifier) {
    val icon: ImageVector = when (iconName) {
        "eco" -> Icons.Default.Eco
        "whatshot" -> Icons.Default.Whatshot
        "nightlight" -> Icons.Default.NightsStay
        "wb_sunny" -> Icons.Default.WbSunny
        "ac_unit" -> Icons.Default.AcUnit
        else -> Icons.Default.Thermostat
    }
    Icon(icon, contentDescription = iconName, modifier = modifier)
}

@Composable
private fun ProfileDialog(
    profile: HeatingProfile?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, targetTemp: Float, hysteresis: Float) -> Unit,
    onDelete: ((Int) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(profile?.icon ?: "thermostat") }
    var targetTemp by remember { mutableFloatStateOf(profile?.targetTemp ?: 22f) }
    var hysteresis by remember { mutableFloatStateOf(profile?.hysteresis ?: 0.5f) }

    val iconOptions = listOf("eco", "whatshot", "thermostat", "nightlight", "wb_sunny", "ac_unit")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (profile != null) "Profil Düzenle" else "Yeni Profil",
                color = TonbilOnSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profil Adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TonbilPrimary,
                        focusedLabelColor = TonbilPrimary,
                        cursorColor = TonbilPrimary,
                    ),
                )

                // Icon selection
                Text(
                    text = "İkon",
                    style = MaterialTheme.typography.bodySmall,
                    color = TonbilOnSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    iconOptions.forEach { iconName ->
                        val isSelected = iconName == selectedIcon
                        IconButton(
                            onClick = { selectedIcon = iconName },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = if (isSelected) TonbilPrimary.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                ),
                        ) {
                            ProfileIcon(
                                iconName = iconName,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // Target temperature slider
                Text(
                    text = "Hedef Sıcaklık: ${"%.1f".format(targetTemp)}°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = TonbilOnSurfaceVariant,
                )
                Slider(
                    value = targetTemp,
                    onValueChange = { targetTemp = it },
                    valueRange = 5f..35f,
                    steps = 59,
                    colors = SliderDefaults.colors(
                        thumbColor = TonbilPrimary,
                        activeTrackColor = TonbilPrimary,
                    ),
                )

                // Hysteresis slider
                Text(
                    text = "Histerezis: ${"%.1f".format(hysteresis)}°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = TonbilOnSurfaceVariant,
                )
                Slider(
                    value = hysteresis,
                    onValueChange = { hysteresis = it },
                    valueRange = 0.1f..3.0f,
                    steps = 28,
                    colors = SliderDefaults.colors(
                        thumbColor = TonbilPrimary,
                        activeTrackColor = TonbilPrimary,
                    ),
                )

                // Delete button
                if (onDelete != null && profile != null) {
                    TextButton(
                        onClick = { onDelete(profile.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB71C1C)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Profili Sil")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), selectedIcon, targetTemp, hysteresis)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TonbilPrimary),
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = TonbilOnSurfaceVariant)
            }
        },
        containerColor = CardDark,
    )
}

@Composable
private fun RuntimeCard(
    runtimeToday: Int,
    runtimeWeekly: Int,
) {
    var showWeekly by remember { mutableStateOf(false) }

    fun formatMinutes(minutes: Int): String {
        return if (minutes < 60) {
            "${minutes}dk"
        } else {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins == 0) "${hours}s" else "${hours}s ${mins}dk"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardDark, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = TonbilPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Calisma Suresi",
                    style = MaterialTheme.typography.titleSmall,
                    color = TonbilOnSurface,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = !showWeekly,
                    onClick = { showWeekly = false },
                    label = { Text("Gunluk", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TonbilPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = TonbilPrimary,
                    ),
                )
                FilterChip(
                    selected = showWeekly,
                    onClick = { showWeekly = true },
                    label = { Text("Haftalik", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TonbilPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = TonbilPrimary,
                    ),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val displayMinutes = if (showWeekly) runtimeWeekly else runtimeToday
        Text(
            text = if (displayMinutes == 0) "—" else formatMinutes(displayMinutes),
            style = MaterialTheme.typography.headlineMedium,
            color = if (displayMinutes > 0) TonbilPrimary else TonbilOnSurfaceVariant,
        )
        Text(
            text = if (showWeekly) "Son 7 gun toplam" else "Bugunku toplam",
            style = MaterialTheme.typography.bodySmall,
            color = TonbilOnSurfaceVariant,
        )
    }
}

@Composable
private fun RoomSelectorChips(
    rooms: List<Room>,
    selectedRoomId: Int?,
    onRoomSelect: (Int?) -> Unit,
) {
    val roomsWithSensors = rooms.filter { it.deviceCount > 0 }
    if (roomsWithSensors.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            FilterChip(
                selected = selectedRoomId == null,
                onClick = { onRoomSelect(null) },
                label = {
                    Text(
                        text = "Tümü",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TonbilPrimary,
                    selectedLabelColor = Color(0xFF4A2800),
                    containerColor = CardDark,
                    labelColor = TonbilOnSurfaceVariant,
                ),
            )
        }
        items(roomsWithSensors, key = { it.id }) { room ->
            FilterChip(
                selected = selectedRoomId == room.id,
                onClick = { onRoomSelect(room.id) },
                label = {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TonbilPrimary,
                    selectedLabelColor = Color(0xFF4A2800),
                    containerColor = CardDark,
                    labelColor = TonbilOnSurfaceVariant,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrategyChips(
    currentStrategy: String,
    onStrategySelect: (String) -> Unit,
) {
    val strategyOptions = listOf(
        "weighted_avg" to "Agirlikli Ort.",
        "coldest_room" to "En Soguk Oda",
        "hottest_room" to "En Sicak Oda",
        "single_room" to "Tek Oda",
    )

    Column {
        Text(
            text = "Isitma Stratejisi",
            style = MaterialTheme.typography.titleSmall,
            color = TonbilOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            strategyOptions.forEach { (key, label) ->
                val isSelected = key == currentStrategy
                FilterChip(
                    selected = isSelected,
                    onClick = { if (!isSelected) onStrategySelect(key) },
                    label = {
                        Text(
                            text = label,
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
    }
}

@Composable
private fun SensorRow(sensor: SensorReading) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = sensor.roomName ?: sensor.deviceId,
                style = MaterialTheme.typography.bodyMedium,
                color = TonbilOnSurface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "${"%.1f".format(sensor.temperature)}C",
                style = MaterialTheme.typography.titleMedium,
                color = TonbilPrimary,
            )
            Text(
                text = "${sensor.humidity.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = TonbilOnSurfaceVariant,
            )
        }
    }
}
