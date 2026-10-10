package com.example.vani

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.vani.ui.overlay.OverlayManager
import com.example.vani.ui.components.BottomTab
import com.example.vani.ui.components.VaniBottomBar
import com.example.vani.ui.screens.CommandsScreen
import com.example.vani.ui.screens.HistoryScreen
import com.example.vani.ui.screens.HomeScreen
import com.example.vani.ui.screens.SettingsScreen
import com.example.vani.ui.theme.VaniTheme
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.vani.ui.wakeword.WakeWordController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDark by rememberSaveable { mutableStateOf(systemDark) }

            // keeps status/navigation bar icons readable when the app theme differs from the system theme
            DisposableEffect(isDark) {
                this@MainActivity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDark },
                )
                onDispose {}
            }

            VaniTheme(darkTheme = isDark) {
                VaniApp(onToggleTheme = { isDark = !isDark })
            }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val COMMANDS = "commands"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

private fun BottomTab.route() = when (this) {
    BottomTab.Home -> Routes.HOME
    BottomTab.Commands -> Routes.COMMANDS
    BottomTab.History -> Routes.HISTORY
    BottomTab.Settings -> Routes.SETTINGS
}

@Composable
fun VaniApp(onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) OverlayManager.show(context) }

    var wakeEnabled by remember { mutableStateOf(WakeWordController.isEnabled(context)) }

    fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    val wakePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            WakeWordController.start(context)
            wakeEnabled = true
        } else {
            wakeEnabled = false
        }
    }

    fun setWakeWord(enable: Boolean) {
        if (!enable) {
            WakeWordController.stop(context)
            wakeEnabled = false
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Allow “Display over other apps”, then turn on Hey VANI again", Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
            )
            return
        }
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filterNot { hasPermission(it) }
        if (missing.isEmpty()) {
            WakeWordController.start(context)
            wakeEnabled = true
        } else {
            wakePermissions.launch(missing.toTypedArray())
        }
    }

// restart the service if it was on and the app process was recreated
    LaunchedEffect(Unit) {
        if (wakeEnabled && hasPermission(Manifest.permission.RECORD_AUDIO)) WakeWordController.start(context)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun startVoice() {
        if (!Settings.canDrawOverlays(context)) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri(),
                )
            )
            return
        }
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (hasMic) OverlayManager.show(context) else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedTab = BottomTab.entries.firstOrNull { it.route() == currentRoute } ?: BottomTab.Home

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = VaniTheme.colors.bgBottom,
        bottomBar = {
            VaniBottomBar(selected = selectedTab, onSelect = { navigateTo(it.route()) })
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onMicClick = { startVoice() },
                    onThemeToggle = onToggleTheme,
                    onRecentClick = { navigateTo(Routes.HISTORY) },
                )
            }
            composable(Routes.COMMANDS) { CommandsScreen() }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}