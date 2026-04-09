package com.tonbil.termostat.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.repository.TonbilRepository
import com.tonbil.termostat.ui.theme.TonbilBackground
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    repository: TonbilRepository = koinInject(),
) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(600, easing = FastOutSlowInEasing),
        )
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(800),
        )
    }

    LaunchedEffect(Unit) {
        delay(1000)
        // Sunucu keşfi — erişilebilir adresi bul
        repository.discoverAndConnect()
        delay(500)
        val isLoggedIn = repository.isLoggedIn().first()
        if (isLoggedIn) {
            repository.restoreToken()
            repository.ensureWebSocketConnected()
            onNavigateToDashboard()
        } else {
            onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TonbilBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value),
        ) {
            Icon(
                imageVector = Icons.Default.DeviceThermostat,
                contentDescription = null,
                tint = TonbilPrimary,
                modifier = Modifier.size(80.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "TonbilTerm",
                style = MaterialTheme.typography.displayMedium,
                color = TonbilPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Akilli Termostat Sistemi",
                style = MaterialTheme.typography.bodyLarge,
                color = TonbilOnSurfaceVariant,
            )
        }
    }
}
