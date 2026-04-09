package com.tonbil.termostat.ui.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tonbil.termostat.ui.screen.DashboardScreen
import com.tonbil.termostat.ui.screen.DevicesScreen
import com.tonbil.termostat.ui.screen.EnergyScreen
import com.tonbil.termostat.ui.screen.LoginScreen
import com.tonbil.termostat.ui.screen.RoomsScreen
import com.tonbil.termostat.ui.screen.SchedulerScreen
import com.tonbil.termostat.ui.screen.SettingsScreen
import com.tonbil.termostat.ui.splash.SplashScreen
import com.tonbil.termostat.ui.theme.TonbilBackground
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import com.tonbil.termostat.ui.theme.TonbilSurface
import kotlinx.serialization.Serializable

// ── Routes ──

@Serializable object SplashRoute
@Serializable object LoginRoute
@Serializable object DashboardRoute
@Serializable object RoomsRoute
@Serializable object DevicesRoute
@Serializable object EnergyRoute
@Serializable object SettingsRoute
@Serializable object SchedulerRoute

// ── Bottom Nav Items ──

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val routeClass: kotlin.reflect.KClass<*>,
)

val bottomNavItems = listOf(
    BottomNavItem("Ana Sayfa", Icons.Default.Home, DashboardRoute, DashboardRoute::class),
    BottomNavItem("Odalar", Icons.Default.MeetingRoom, RoomsRoute, RoomsRoute::class),
    BottomNavItem("Cihazlar", Icons.Default.DeviceThermostat, DevicesRoute, DevicesRoute::class),
    BottomNavItem("Enerji", Icons.Default.BarChart, EnergyRoute, EnergyRoute::class),
    BottomNavItem("Ayarlar", Icons.Default.Settings, SettingsRoute, SettingsRoute::class),
)

// ── Bottom Navigation Bar ──

@Composable
fun TonbilBottomNav(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = TonbilSurface,
        contentColor = TonbilOnSurface,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hasRoute(item.routeClass) == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            popUpTo(DashboardRoute) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TonbilPrimary,
                    selectedTextColor = TonbilPrimary,
                    unselectedIconColor = TonbilOnSurfaceVariant,
                    unselectedTextColor = TonbilOnSurfaceVariant,
                    indicatorColor = TonbilPrimary.copy(alpha = 0.12f),
                ),
            )
        }
    }
}

// ── NavHost ──

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Any = SplashRoute,
    showSnackbar: ((String, Boolean) -> Unit)? = null,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<SplashRoute> {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(LoginRoute) {
                        popUpTo(SplashRoute) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(DashboardRoute) {
                        popUpTo(SplashRoute) { inclusive = true }
                    }
                },
            )
        }

        composable<LoginRoute> {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(DashboardRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                },
            )
        }

        composable<DashboardRoute> {
            DashboardScreen(
                showSnackbar = if (showSnackbar != null) {
                    { msg, ok -> showSnackbar(msg, ok) }
                } else {
                    null
                },
            )
        }

        composable<RoomsRoute> {
            RoomsScreen()
        }

        composable<DevicesRoute> {
            DevicesScreen()
        }

        composable<EnergyRoute> {
            EnergyScreen()
        }

        composable<SettingsRoute> {
            SettingsScreen(
                onLogout = {
                    navController.navigate(LoginRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToScheduler = {
                    navController.navigate(SchedulerRoute)
                },
            )
        }

        composable<SchedulerRoute> {
            SchedulerScreen()
        }
    }
}
