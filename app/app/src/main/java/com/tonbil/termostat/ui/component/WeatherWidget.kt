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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.model.WeatherData
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.InfoBlue
import com.tonbil.termostat.ui.theme.TempCold
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import com.tonbil.termostat.ui.theme.WarningYellow
import com.tonbil.termostat.util.Formatters

@Composable
fun WeatherWidget(
    weather: WeatherData?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
    ) {
        if (weather == null) {
            Text(
                text = "Hava durumu yukleniyor...",
                style = MaterialTheme.typography.bodyMedium,
                color = TonbilOnSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = weatherIcon(weather.icon),
                        contentDescription = null,
                        tint = WarningYellow,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = weather.city,
                            style = MaterialTheme.typography.titleMedium,
                            color = TonbilOnSurface,
                        )
                        Text(
                            text = weather.description.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = TonbilOnSurfaceVariant,
                        )
                    }
                    Text(
                        text = Formatters.formatTemp(weather.temperature, 0),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TonbilPrimary,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    WeatherDetail(
                        icon = Icons.Default.Thermostat,
                        label = "Hissedilen",
                        value = Formatters.formatTemp(weather.feelsLike, 0),
                    )
                    WeatherDetail(
                        icon = Icons.Default.WaterDrop,
                        label = "Nem",
                        value = "${weather.humidity}%",
                    )
                    WeatherDetail(
                        icon = Icons.Default.Air,
                        label = "Ruzgar",
                        value = "${"%.0f".format(weather.windSpeed)} km/s",
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherDetail(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = InfoBlue,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TonbilOnSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TonbilOnSurface,
        )
    }
}

private fun weatherIcon(code: String): ImageVector = when {
    code.contains("sun") || code.contains("clear") || code == "01d" || code == "01n" -> Icons.Default.WbSunny
    code.contains("cloud") || code.startsWith("02") || code.startsWith("03") || code.startsWith("04") -> Icons.Default.WbCloudy
    else -> Icons.Default.Cloud
}
