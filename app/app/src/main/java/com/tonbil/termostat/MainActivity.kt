package com.tonbil.termostat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tonbil.termostat.ui.navigation.AppNavHost
import com.tonbil.termostat.ui.navigation.LoginRoute
import com.tonbil.termostat.ui.navigation.SplashRoute
import com.tonbil.termostat.ui.navigation.TonbilBottomNav
import com.tonbil.termostat.ui.theme.OnlineGreen
import com.tonbil.termostat.ui.theme.TonbilError
import com.tonbil.termostat.ui.theme.TonbilSurface
import com.tonbil.termostat.data.model.EnergyAlert
import com.tonbil.termostat.data.repository.TonbilRepository
import com.tonbil.termostat.ui.theme.CardDark
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import com.tonbil.termostat.ui.theme.TonbilTermTheme
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val crashText = mutableStateOf<String?>(null)
    private val repository: TonbilRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#1C1B1F")),
        )

        super.onCreate(savedInstanceState)

        // Check for previous crash log
        val crashFile = File(filesDir, "crash_log.txt")
        if (crashFile.exists()) {
            crashText.value = crashFile.readText()
            crashFile.delete()
        }

        setContent {
            TonbilTermTheme {
                val crash = crashText.value
                if (crash != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = "HATA",
                            color = Color.Red,
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        val lines = crash.lines()
                        val exceptionLine = lines.firstOrNull {
                            it.contains("Exception") || it.contains("Error")
                        } ?: ""
                        if (exceptionLine.isNotBlank()) {
                            Spacer(modifier = Modifier.padding(8.dp))
                            Text(
                                text = exceptionLine.trim(),
                                color = Color.Yellow,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Spacer(modifier = Modifier.padding(8.dp))
                        Text(
                            text = crash,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(modifier = Modifier.padding(16.dp))
                        Button(onClick = { crashText.value = null }) {
                            Text("Devam Et")
                        }
                    }
                } else {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    val isAuthScreen = currentDestination?.hasRoute(LoginRoute::class) == true ||
                        currentDestination?.hasRoute(SplashRoute::class) == true

                    // ── Snackbar state ──
                    val snackbarHostState = remember { SnackbarHostState() }
                    val snackbarScope = rememberCoroutineScope()
                    // Track whether the current snackbar is a success or error
                    val isSnackbarSuccess = remember { mutableStateOf(true) }

                    val showSnackbar: suspend (String, Boolean) -> Unit = { message, isSuccess ->
                        isSnackbarSuccess.value = isSuccess
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short,
                        )
                    }

                    // 401 Unauthorized -> login'e yonlendir
                    LaunchedEffect(Unit) {
                        repository.authExpired.collect {
                            repository.clearToken()
                            snackbarScope.launch {
                                showSnackbar("Oturum suresi doldu, tekrar giris yapin", false)
                            }
                            navController.navigate(LoginRoute) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    // Alerts state
                    val alerts = remember { mutableStateOf<List<EnergyAlert>>(emptyList()) }
                    val dismissedAlerts = remember { mutableStateOf<Set<String>>(emptySet()) }
                    val bellMenuOpen = remember { mutableStateOf(false) }
                    val visibleAlerts = alerts.value.filter { a ->
                        val id = "${a.type}-${a.title}"
                        id !in dismissedAlerts.value
                    }

                    // Load alerts periodically
                    LaunchedEffect(Unit) {
                        while (true) {
                            try {
                                repository.getEnergyAlerts().onSuccess { alerts.value = it }
                            } catch (_: Exception) {}
                            delay(5 * 60 * 1000L)
                        }
                    }

                    Scaffold(
                        bottomBar = {
                            if (!isAuthScreen) {
                                TonbilBottomNav(navController)
                            }
                        },
                        snackbarHost = {
                            SnackbarHost(hostState = snackbarHostState) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = if (isSnackbarSuccess.value) {
                                        OnlineGreen.copy(alpha = 0.95f)
                                    } else {
                                        TonbilError.copy(alpha = 0.95f)
                                    },
                                    contentColor = Color(0xFF1C1B1F),
                                )
                            }
                        },
                    ) { innerPadding ->
                        AppNavHost(
                            navController = navController,
                            modifier = Modifier.padding(innerPadding),
                            showSnackbar = { message, isSuccess ->
                                snackbarScope.launch {
                                    showSnackbar(message, isSuccess)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
