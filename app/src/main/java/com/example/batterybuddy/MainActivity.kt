package com.example.batterybuddy

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clear = AndroidColor.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(clear, clear),
            navigationBarStyle = SystemBarStyle.light(clear, clear)
        )

        // needed on Android 13+ for the charge-limit and overheat alerts
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }

        setContent { BuddyTheme { BuddyApp() } }
    }
}

private val TABS = listOf(
    "🏠" to "Home",
    "🌸" to "Characters",
    "🎨" to "Create",
    "🛡️" to "Safety"
)

@Composable
fun BuddyApp() {
    var tab by rememberSaveable { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    // kept here so the Top 200 and your search survive switching tabs
    val top = remember { Feed(null) }
    val search = remember { Feed("") }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Blush, Color(0xFFF1EBFF))))) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = Color.White.copy(alpha = 0.94f)) {
                    TABS.forEachIndexed { i, (emoji, label) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(emoji, fontSize = 22.sp) },
                            label = {
                                Text(label, fontSize = 11.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = SakuraSoft,
                                selectedTextColor = Plum,
                                unselectedTextColor = PlumSoft
                            )
                        )
                    }
                }
            }
        ) { inner ->
            Box(Modifier.padding(inner).statusBarsPadding()) {
                Crossfade(targetState = tab, label = "tabs") { t ->
                    when (t) {
                        0 -> HomeScreen()
                        1 -> CharactersScreen(top, search, snackbar)
                        2 -> CreateScreen()
                        else -> SafetyScreen()
                    }
                }
            }
        }
    }
}
