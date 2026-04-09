package com.tonbil.termostat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.model.HeatingConfig
import com.tonbil.termostat.data.model.ScheduleEntry
import com.tonbil.termostat.data.model.UserInfo
import com.tonbil.termostat.data.repository.TonbilRepository
import com.tonbil.termostat.ui.theme.BoostRed
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.HeatingOrange
import com.tonbil.termostat.ui.theme.OnlineGreen
import com.tonbil.termostat.ui.theme.TonbilBackground
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import com.tonbil.termostat.ui.theme.TonbilSurfaceVariant
import com.tonbil.termostat.util.Formatters
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val strategyOptions = listOf(
    "weighted_avg" to "Agirlikli Ortalama",
    "coldest_room" to "En Soguk Oda",
    "hottest_room" to "En Sicak Oda",
    "single_room" to "Tek Oda",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onNavigateToScheduler: (() -> Unit)? = null,
    repository: TonbilRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<UserInfo?>(null) }
    var heatingConfig by remember { mutableStateOf(HeatingConfig()) }
    var schedules by remember { mutableStateOf<List<ScheduleEntry>>(emptyList()) }
    var serverUrl by remember { mutableStateOf("") }

    // Heating edit state
    var hysteresis by remember { mutableFloatStateOf(0.3f) }
    var minCycleSec by remember { mutableFloatStateOf(0f) }
    var strategy by remember { mutableStateOf("weighted_avg") }
    var strategyExpanded by remember { mutableStateOf(false) }

    // Boiler/location/app edit state
    var gasPriceStr by remember { mutableStateOf("") }
    var boilerPowerStr by remember { mutableStateOf("") }
    var cityStr by remember { mutableStateOf("") }
    var latStr by remember { mutableStateOf("") }
    var lonStr by remember { mutableStateOf("") }
    var serverUrlEdit by remember { mutableStateOf("") }

    // User management state
    val userList = remember { mutableStateListOf<UserInfo>() }
    var showAddUserDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<UserInfo?>(null) }
    var userMgmtMsg by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialogs
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    // Save feedback
    var heatingSaveMsg by remember { mutableStateOf<String?>(null) }
    var passwordMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userMgmtMsg) {
        userMgmtMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            userMgmtMsg = null
        }
    }

    LaunchedEffect(Unit) {
        repository.getMe().onSuccess { user = it }
        repository.getUsers().onSuccess { list ->
            userList.clear()
            userList.addAll(list)
        }
        repository.getHeatingConfig().onSuccess { config ->
            heatingConfig = config
            hysteresis = config.hysteresis
            minCycleSec = config.minCycleMin * 60f
            strategy = config.strategy
            gasPriceStr = if (config.gasPricePerM3 > 0) "%.2f".format(config.gasPricePerM3) else ""
            boilerPowerStr = if (config.boilerPowerKw > 0) "%.1f".format(config.boilerPowerKw) else ""
        }
        repository.getScheduleEntries().onSuccess { schedules = it }
        repository.getServerUrl()?.let {
            serverUrl = it
            serverUrlEdit = it
        }
    }

    // Add user dialog
    if (showAddUserDialog) {
        AddUserDialog(
            onDismiss = { showAddUserDialog = false },
            onConfirm = { email, password, displayName ->
                scope.launch {
                    repository.createUser(email, password, displayName).fold(
                        onSuccess = { newUser ->
                            userList.add(newUser)
                            showAddUserDialog = false
                            userMgmtMsg = "Kullanici eklendi: ${newUser.email}"
                        },
                        onFailure = { userMgmtMsg = "Hata: ${it.message}" },
                    )
                }
            },
        )
    }

    // Edit user dialog
    editingUser?.let { targetUser ->
        EditUserDialog(
            user = targetUser,
            onDismiss = { editingUser = null },
            onConfirm = { newDisplayName ->
                scope.launch {
                    repository.updateUser(targetUser.id, newDisplayName, null).fold(
                        onSuccess = { updated ->
                            val idx = userList.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) userList[idx] = updated
                            editingUser = null
                            userMgmtMsg = "Kullanici guncellendi"
                        },
                        onFailure = { userMgmtMsg = "Hata: ${it.message}" },
                    )
                }
            },
        )
    }

    // Logout dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cikis Yap", color = TonbilOnSurface) },
            text = { Text("Oturumunuzu kapatmak istediginize emin misiniz?", color = TonbilOnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    scope.launch { repository.logout(); onLogout() }
                }) { Text("Evet", color = BoostRed) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Iptal", color = TonbilOnSurfaceVariant)
                }
            },
            containerColor = CardDark,
            titleContentColor = TonbilOnSurface,
            textContentColor = TonbilOnSurfaceVariant,
        )
    }

    // Change password dialog
    if (showPasswordDialog) {
        ChangePasswordDialog(
            errorMsg = passwordMsg,
            onDismiss = { showPasswordDialog = false; passwordMsg = null },
            onConfirm = { oldPw, newPw ->
                scope.launch {
                    repository.changePassword(oldPw, newPw).fold(
                        onSuccess = { showPasswordDialog = false; passwordMsg = null },
                        onFailure = { passwordMsg = it.message ?: "Hata olustu" },
                    )
                }
            },
        )
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TonbilBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ayarlar",
            style = MaterialTheme.typography.headlineMedium,
            color = TonbilPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // ── Kullanici Bilgileri ──
        SectionTitle("Kullanici Bilgileri")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TonbilPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        user?.let { u ->
                            Text(
                                text = u.displayName.ifBlank { u.email },
                                style = MaterialTheme.typography.titleMedium,
                                color = TonbilOnSurface,
                            )
                            Text(
                                text = u.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = TonbilOnSurfaceVariant,
                            )
                        } ?: Text("Yukleniyor...", color = TonbilOnSurfaceVariant)
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = TonbilSurfaceVariant.copy(alpha = 0.5f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPasswordDialog = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = TonbilPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Sifre Degistir",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TonbilOnSurface,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Isitma Ayarlari ──
        SectionTitle("Isitma Ayarlari")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Hysteresis slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = TonbilPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Histerezis", style = MaterialTheme.typography.bodyLarge, color = TonbilOnSurface)
                    }
                    Text(
                        text = "${"%.1f".format(hysteresis)}°C",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TonbilPrimary,
                    )
                }
                Slider(
                    value = hysteresis,
                    onValueChange = { hysteresis = it },
                    valueRange = 0.1f..3.0f,
                    steps = 28,
                    colors = SliderDefaults.colors(
                        thumbColor = TonbilPrimary,
                        activeTrackColor = TonbilPrimary,
                        inactiveTrackColor = TonbilSurfaceVariant,
                    ),
                )

                HorizontalDivider(color = TonbilSurfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Min cycle time slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = TonbilPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Min. Cevrim Suresi", style = MaterialTheme.typography.bodyLarge, color = TonbilOnSurface)
                    }
                    Text(
                        text = "${minCycleSec.toInt()}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TonbilPrimary,
                    )
                }
                Slider(
                    value = minCycleSec,
                    onValueChange = { minCycleSec = it },
                    valueRange = 0f..600f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = TonbilPrimary,
                        activeTrackColor = TonbilPrimary,
                        inactiveTrackColor = TonbilSurfaceVariant,
                    ),
                )

                HorizontalDivider(color = TonbilSurfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Strategy dropdown
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoMode,
                        contentDescription = null,
                        tint = TonbilPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Strateji", style = MaterialTheme.typography.bodyLarge, color = TonbilOnSurface)
                }
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = strategyExpanded,
                    onExpandedChange = { strategyExpanded = it },
                ) {
                    OutlinedTextField(
                        value = strategyOptions.firstOrNull { it.first == strategy }?.second ?: strategy,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(strategyExpanded) },
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
                        expanded = strategyExpanded,
                        onDismissRequest = { strategyExpanded = false },
                        modifier = Modifier.background(CardDark),
                    ) {
                        strategyOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = TonbilOnSurface) },
                                onClick = { strategy = key; strategyExpanded = false },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                heatingSaveMsg?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (msg.startsWith("Hata")) BoostRed else OnlineGreen,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        scope.launch {
                            val updated = heatingConfig.copy(
                                hysteresis = hysteresis,
                                minCycleMin = (minCycleSec / 60).toInt(),
                                strategy = strategy,
                            )
                            repository.updateHeatingConfig(updated).fold(
                                onSuccess = { heatingConfig = it; heatingSaveMsg = "Kaydedildi" },
                                onFailure = { heatingSaveMsg = "Hata: ${it.message}" },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TonbilPrimary),
                ) {
                    Text("Kaydet", color = androidx.compose.ui.graphics.Color(0xFF4A2800))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Kombi Bilgileri ──
        SectionTitle("Kombi Bilgileri")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (heatingConfig.boilerBrand.isNotBlank()) {
                    SettingsRow(
                        icon = Icons.Default.Build,
                        label = "Marka / Model",
                        value = "${heatingConfig.boilerBrand} ${heatingConfig.boilerModel}".trim(),
                    )
                    HorizontalDivider(color = TonbilSurfaceVariant.copy(alpha = 0.5f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = boilerPowerStr,
                    onValueChange = { boilerPowerStr = it },
                    label = { Text("Guc (kW)", color = TonbilOnSurfaceVariant) },
                    leadingIcon = {
                        Icon(Icons.Default.Thermostat, contentDescription = null, tint = TonbilPrimary)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TonbilPrimary,
                        unfocusedBorderColor = TonbilSurfaceVariant,
                        focusedTextColor = TonbilOnSurface,
                        unfocusedTextColor = TonbilOnSurface,
                        cursorColor = TonbilPrimary,
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = gasPriceStr,
                    onValueChange = { gasPriceStr = it },
                    label = { Text("Gaz Fiyati (TL/m³)", color = TonbilOnSurfaceVariant) },
                    leadingIcon = {
                        Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = TonbilPrimary)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

                Button(
                    onClick = {
                        scope.launch {
                            val updated = heatingConfig.copy(
                                boilerPowerKw = boilerPowerStr.toFloatOrNull() ?: heatingConfig.boilerPowerKw,
                                gasPricePerM3 = gasPriceStr.toFloatOrNull() ?: heatingConfig.gasPricePerM3,
                            )
                            repository.updateHeatingConfig(updated).fold(
                                onSuccess = { heatingConfig = it },
                                onFailure = { /* silently ignore */ },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TonbilPrimary),
                ) {
                    Text("Kaydet", color = androidx.compose.ui.graphics.Color(0xFF4A2800))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Programlar ──
        SectionTitle("Programlar")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToScheduler?.invoke() },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = TonbilPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Haftalik Program",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TonbilOnSurface,
                        )
                        Text(
                            text = "Gunluk isitma programlarini duzenle",
                            style = MaterialTheme.typography.bodySmall,
                            color = TonbilOnSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = TonbilOnSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (schedules.isNotEmpty()) {
            SectionTitle("Mevcut Programlar")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    schedules.forEachIndexed { index, schedule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = TonbilPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = Formatters.dayOfWeekTurkish(schedule.dayOfWeek),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TonbilOnSurface,
                                    )
                                    Text(
                                        text = "${schedule.hour.toString().padStart(2, '0')}:${schedule.minute.toString().padStart(2, '0')} - ${Formatters.formatTemp(schedule.targetTemp)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TonbilOnSurfaceVariant,
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = Formatters.formatTemp(schedule.targetTemp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HeatingOrange,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = schedule.enabled,
                                    onCheckedChange = null,
                                    colors = SwitchDefaults.colors(checkedTrackColor = OnlineGreen),
                                )
                            }
                        }
                        if (index < schedules.lastIndex) {
                            HorizontalDivider(color = TonbilSurfaceVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Kullanici Yonetimi ──
        val isAdmin = user?.role == "admin"
        SectionTitle("Kullanici Yonetimi")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = TonbilPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${userList.size} kullanici",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TonbilOnSurfaceVariant,
                        )
                    }
                    if (isAdmin) {
                        IconButton(onClick = { showAddUserDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Yeni Kullanici Ekle",
                                tint = TonbilPrimary,
                            )
                        }
                    }
                }

                if (userList.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = TonbilSurfaceVariant.copy(alpha = 0.5f),
                    )
                    userList.forEachIndexed { index, u ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = u.displayName.ifBlank { u.email },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TonbilOnSurface,
                                )
                                Text(
                                    text = u.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TonbilOnSurfaceVariant,
                                )
                            }
                            // Admin: duzenle + sil; kullanici: sadece kendini duzenleyebilir
                            val isSelf = u.email == user?.email
                            if (isAdmin || isSelf) {
                                IconButton(onClick = { editingUser = u }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Duzenle",
                                        tint = TonbilPrimary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            if (isAdmin) {
                                IconButton(
                                    onClick = {
                                        if (!isSelf) {
                                            scope.launch {
                                                repository.deleteUser(u.id).fold(
                                                    onSuccess = {
                                                        userList.removeAt(index)
                                                        userMgmtMsg = "Kullanici silindi: ${u.email}"
                                                    },
                                                    onFailure = { userMgmtMsg = "Hata: ${it.message}" },
                                                )
                                            }
                                        }
                                    },
                                    enabled = !isSelf,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Sil",
                                        tint = if (isSelf) TonbilSurfaceVariant else BoostRed,
                                    )
                                }
                            }
                        }
                        if (index < userList.lastIndex) {
                            HorizontalDivider(color = TonbilSurfaceVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Uygulama ──
        SectionTitle("Uygulama")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Server URL
                OutlinedTextField(
                    value = serverUrlEdit,
                    onValueChange = { serverUrlEdit = it },
                    label = { Text("Sunucu URL", color = TonbilOnSurfaceVariant) },
                    leadingIcon = {
                        Icon(Icons.Default.WifiTethering, contentDescription = null, tint = TonbilPrimary)
                    },
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
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            if (serverUrlEdit.isNotBlank()) {
                                repository.saveServerUrl(serverUrlEdit.trim())
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TonbilPrimary),
                ) {
                    Text("URL Kaydet", color = androidx.compose.ui.graphics.Color(0xFF4A2800))
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = TonbilSurfaceVariant.copy(alpha = 0.5f),
                )

                SettingsRow(
                    icon = Icons.Default.Info,
                    label = "Surum",
                    value = "1.0.0",
                )

                HorizontalDivider(color = TonbilSurfaceVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogoutDialog = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = BoostRed,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Cikis Yap",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BoostRed,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
        snackbar = { data ->
            Snackbar(
                snackbarData = data,
                containerColor = CardDark,
                contentColor = TonbilOnSurface,
            )
        },
    )
    } // Box
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = TonbilOnSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TonbilPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TonbilOnSurface,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TonbilOnSurfaceVariant,
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    errorMsg: String?,
    onDismiss: () -> Unit,
    onConfirm: (oldPassword: String, newPassword: String) -> Unit,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sifre Degistir", color = TonbilOnSurface) },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it; localError = null },
                    label = { Text("Mevcut Sifre", color = TonbilOnSurfaceVariant) },
                    visualTransformation = PasswordVisualTransformation(),
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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; localError = null },
                    label = { Text("Yeni Sifre", color = TonbilOnSurfaceVariant) },
                    visualTransformation = PasswordVisualTransformation(),
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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; localError = null },
                    label = { Text("Yeni Sifre (Tekrar)", color = TonbilOnSurfaceVariant) },
                    visualTransformation = PasswordVisualTransformation(),
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
                val displayError = localError ?: errorMsg
                if (displayError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodySmall,
                        color = BoostRed,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    oldPassword.isBlank() || newPassword.isBlank() -> localError = "Alanlar bos birakilamaz"
                    newPassword != confirmPassword -> localError = "Sifreler eslesmiyor"
                    newPassword.length < 6 -> localError = "Sifre en az 6 karakter olmali"
                    else -> onConfirm(oldPassword, newPassword)
                }
            }) {
                Text("Degistir", color = TonbilPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Iptal", color = TonbilOnSurfaceVariant)
            }
        },
        containerColor = CardDark,
        titleContentColor = TonbilOnSurface,
        textContentColor = TonbilOnSurfaceVariant,
    )
}

@Composable
private fun AddUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (email: String, password: String, displayName: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = TonbilPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Yeni Kullanici Ekle", color = TonbilOnSurface)
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; localError = null },
                    label = { Text("Isim", color = TonbilOnSurfaceVariant) },
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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; localError = null },
                    label = { Text("E-posta", color = TonbilOnSurfaceVariant) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TonbilPrimary,
                        unfocusedBorderColor = TonbilSurfaceVariant,
                        focusedTextColor = TonbilOnSurface,
                        unfocusedTextColor = TonbilOnSurface,
                        cursorColor = TonbilPrimary,
                    ),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; localError = null },
                    label = { Text("Sifre", color = TonbilOnSurfaceVariant) },
                    visualTransformation = PasswordVisualTransformation(),
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
                if (localError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = localError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = BoostRed,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    displayName.isBlank() -> localError = "Isim bos birakilamaz"
                    email.isBlank() -> localError = "E-posta bos birakilamaz"
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> localError = "Gecerli e-posta girin"
                    password.length < 6 -> localError = "Sifre en az 6 karakter olmali"
                    else -> onConfirm(email.trim(), password, displayName.trim())
                }
            }) {
                Text("Ekle", color = TonbilPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Iptal", color = TonbilOnSurfaceVariant)
            }
        },
        containerColor = CardDark,
        titleContentColor = TonbilOnSurface,
        textContentColor = TonbilOnSurfaceVariant,
    )
}

@Composable
private fun EditUserDialog(
    user: UserInfo,
    onDismiss: () -> Unit,
    onConfirm: (displayName: String) -> Unit,
) {
    var displayName by remember { mutableStateOf(user.displayName) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = TonbilPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kullanici Duzenle", color = TonbilOnSurface)
            }
        },
        text = {
            Column {
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = TonbilOnSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; localError = null },
                    label = { Text("Goruntulenen Isim", color = TonbilOnSurfaceVariant) },
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
                if (localError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = localError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = BoostRed,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (displayName.isBlank()) {
                    localError = "Isim bos birakilamaz"
                } else {
                    onConfirm(displayName.trim())
                }
            }) {
                Text("Kaydet", color = TonbilPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Iptal", color = TonbilOnSurfaceVariant)
            }
        },
        containerColor = CardDark,
        titleContentColor = TonbilOnSurface,
        textContentColor = TonbilOnSurfaceVariant,
    )
}
