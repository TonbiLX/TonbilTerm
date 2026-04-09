package com.tonbil.termostat.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.model.EnergyAlert
import com.tonbil.termostat.data.model.EnergyData
import com.tonbil.termostat.data.repository.TonbilRepository
import com.tonbil.termostat.ui.theme.BoostRed
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.CardDarker
import com.tonbil.termostat.ui.theme.HeatingOrange
import com.tonbil.termostat.ui.theme.InfoBlue
import com.tonbil.termostat.ui.theme.OnlineGreen
import com.tonbil.termostat.ui.theme.TonbilBackground
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import com.tonbil.termostat.ui.theme.WarningYellow
import com.tonbil.termostat.util.Formatters
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ECA Proteus efficiency curve (flow_temp -> efficiency %)
private data class EfficiencyRow(
    val flowTemp: Int,
    val efficiencyPct: Float,
    val isCondensing: Boolean,
)

private val EFFICIENCY_CURVE = listOf(
    EfficiencyRow(40, 108.0f, true),
    EfficiencyRow(45, 107.5f, true),
    EfficiencyRow(50, 105.0f, true),
    EfficiencyRow(55, 100.0f, true),
    EfficiencyRow(60, 96.0f, false),
    EfficiencyRow(65, 94.5f, false),
    EfficiencyRow(70, 93.5f, false),
    EfficiencyRow(75, 93.0f, false),
    EfficiencyRow(80, 93.0f, false),
)

@Composable
fun EnergyScreen(
    repository: TonbilRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var energy by remember { mutableStateOf<EnergyData?>(null) }
    var alerts by remember { mutableStateOf<List<EnergyAlert>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            repository.getEnergyData().onSuccess { energy = it }
            repository.getEnergyAlerts().onSuccess { alerts = it }
            isLoading = false
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
        Text(
            text = "Enerji",
            style = MaterialTheme.typography.headlineMedium,
            color = TonbilPrimary,
        )
        Text(
            text = "Tuketim ve maliyet analizi",
            style = MaterialTheme.typography.bodySmall,
            color = TonbilOnSurfaceVariant,
        )
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
        } else if (energy == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Enerji verisi alinamadi",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TonbilOnSurfaceVariant,
                )
            }
        } else {
            val e = energy!!
            val today = e.today

            // Daily summary
            Text(
                text = "Gunluk Ozet",
                style = MaterialTheme.typography.titleMedium,
                color = TonbilOnSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EnergyStatCard(
                    icon = Icons.Default.Timer,
                    label = "Calisma",
                    value = Formatters.formatHours(today.runtimeMinutes / 60f),
                    iconTint = InfoBlue,
                    modifier = Modifier.weight(1f),
                )
                EnergyStatCard(
                    icon = Icons.Default.Payments,
                    label = "Maliyet",
                    value = Formatters.formatCurrency(today.costTl),
                    iconTint = OnlineGreen,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EnergyStatCard(
                    icon = Icons.Default.LocalGasStation,
                    label = "Gaz",
                    value = "${"%.2f".format(today.gasM3)} m3",
                    iconTint = HeatingOrange,
                    modifier = Modifier.weight(1f),
                )
                EnergyStatCard(
                    icon = Icons.Default.Bolt,
                    label = "Termal",
                    value = Formatters.formatKwh(today.thermalKwh),
                    iconTint = WarningYellow,
                    modifier = Modifier.weight(1f),
                )
            }

            // Cost breakdown card
            Spacer(modifier = Modifier.height(16.dp))
            CostBreakdownCard(energy = e)

            // Efficiency
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Verimlilik",
                style = MaterialTheme.typography.titleMedium,
                color = TonbilOnSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EnergyStatCard(
                    icon = Icons.Default.Speed,
                    label = "Verim",
                    value = "${"%.0f".format(e.efficiency.currentPct)}%",
                    iconTint = if (e.efficiency.isCondensing) OnlineGreen else HeatingOrange,
                    modifier = Modifier.weight(1f),
                )
                EnergyStatCard(
                    icon = Icons.Default.LocalFireDepartment,
                    label = "Gidis Sicakligi",
                    value = Formatters.formatTemp(e.flowTemp),
                    iconTint = HeatingOrange,
                    modifier = Modifier.weight(1f),
                )
            }

            // Efficiency comparison table
            Spacer(modifier = Modifier.height(16.dp))
            EfficiencyComparisonTable(currentFlowTemp = e.flowTemp, gasPricePerM3 = e.gasPricePerM3)

            // Relay status
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Role Durumu",
                style = MaterialTheme.typography.titleMedium,
                color = TonbilOnSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (e.relay.state) OnlineGreen else TonbilOnSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (e.relay.state) "Acik" else "Kapali",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (e.relay.state) OnlineGreen else TonbilOnSurfaceVariant,
                        )
                        e.relay.onSince?.let { since ->
                            Text(
                                text = "Acilma: ${Formatters.formatTimestamp(since)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TonbilOnSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Alerts
            Spacer(modifier = Modifier.height(20.dp))
            AlertsCard(alerts = alerts)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CostBreakdownCard(energy: EnergyData) {
    val gasPrice = energy.gasPricePerM3
    val dailyCost = energy.today.costTl
    val weeklyCost = dailyCost * 7f
    val monthlyCost = dailyCost * 30f

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Maliyet Tahmini",
                style = MaterialTheme.typography.titleMedium,
                color = TonbilOnSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CostItem(label = "Bugun", value = Formatters.formatCurrency(dailyCost))
                CostItem(label = "Haftalik", value = Formatters.formatCurrency(weeklyCost))
                CostItem(label = "Aylik", value = Formatters.formatCurrency(monthlyCost))
            }
            if (gasPrice > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Gaz birim fiyati: ${Formatters.formatCurrency(gasPrice)}/m3",
                    style = MaterialTheme.typography.bodySmall,
                    color = TonbilOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CostItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TonbilOnSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = OnlineGreen,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EfficiencyComparisonTable(currentFlowTemp: Float, gasPricePerM3: Float) {
    val currentTempRounded = currentFlowTemp.toInt()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Verimlilik Karsilastirmasi (ECA Proteus)",
                style = MaterialTheme.typography.titleMedium,
                color = TonbilOnSurface,
            )
            Text(
                text = "Gidis sicakligina gore teorik verim",
                style = MaterialTheme.typography.bodySmall,
                color = TonbilOnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Header row
            EfficiencyTableRow(
                flowTemp = "Gidis °C",
                efficiency = "Verim %",
                condensing = "Yog.",
                gasRelative = "Gaz",
                isHeader = true,
                isCurrentRow = false,
            )
            HorizontalDivider(color = TonbilOnSurfaceVariant.copy(alpha = 0.2f))

            EFFICIENCY_CURVE.forEach { row ->
                val isCurrentRow = row.flowTemp == currentTempRounded ||
                    (currentTempRounded in (row.flowTemp - 2)..(row.flowTemp + 2) &&
                        EFFICIENCY_CURVE.none { it.flowTemp == currentTempRounded })
                val bestEfficiency = EFFICIENCY_CURVE.maxOf { it.efficiencyPct }
                val relativeGas = bestEfficiency / row.efficiencyPct
                val gasLabel = "${"%.2f".format(relativeGas)}x"

                EfficiencyTableRow(
                    flowTemp = "${row.flowTemp}°C",
                    efficiency = "${"%.1f".format(row.efficiencyPct)}%",
                    condensing = if (row.isCondensing) "Evet" else "Hayir",
                    gasRelative = gasLabel,
                    isHeader = false,
                    isCurrentRow = isCurrentRow,
                    isCondensing = row.isCondensing,
                )
            }
        }
    }
}

@Composable
private fun EfficiencyTableRow(
    flowTemp: String,
    efficiency: String,
    condensing: String,
    gasRelative: String,
    isHeader: Boolean,
    isCurrentRow: Boolean,
    isCondensing: Boolean = false,
) {
    val bgColor = when {
        isCurrentRow -> TonbilPrimary.copy(alpha = 0.12f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val textStyle = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    val textColor = if (isHeader) TonbilOnSurfaceVariant else TonbilOnSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = flowTemp,
            style = textStyle,
            color = if (isCurrentRow) TonbilPrimary else textColor,
            modifier = Modifier.weight(1.2f),
            fontWeight = if (isCurrentRow) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            text = efficiency,
            style = textStyle,
            color = if (isHeader) textColor else if (isCondensing) OnlineGreen else HeatingOrange,
            modifier = Modifier.weight(1.2f),
            textAlign = TextAlign.Center,
            fontWeight = if (isCurrentRow) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            text = condensing,
            style = textStyle,
            color = if (isHeader) textColor else if (isCondensing) OnlineGreen else TonbilOnSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Text(
            text = gasRelative,
            style = textStyle,
            color = textColor,
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AlertsCard(alerts: List<EnergyAlert>) {
    Text(
        text = "Bildirimler",
        style = MaterialTheme.typography.titleMedium,
        color = TonbilOnSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (alerts.isEmpty()) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Bildirim yok",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TonbilOnSurfaceVariant,
                )
            }
        }
    } else {
        alerts.forEach { alert ->
            AlertCard(alert)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EnergyStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TonbilOnSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = TonbilOnSurface,
            )
        }
    }
}

@Composable
private fun AlertCard(alert: EnergyAlert) {
    val (icon, color) = when (alert.type) {
        "warning" -> Icons.Default.Warning to WarningYellow
        "error", "critical" -> Icons.Default.LocalFireDepartment to BoostRed
        else -> when (alert.icon) {
            "eco" -> Icons.Default.Eco to OnlineGreen
            else -> Icons.Default.Info to InfoBlue
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TonbilOnSurface,
                )
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TonbilOnSurfaceVariant,
                )
            }
        }
    }
}
