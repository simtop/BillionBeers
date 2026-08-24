package com.simtop.billionbeers.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.billionbeers.BillionBeersApplication
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.debug.DebugDrawerHost
import com.simtop.core.core.ThemeController
import com.simtop.core.core.ThemeMode
import com.simtop.presentation_utils.core.LocalSplitInstallManager
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

class MainActivity : ComponentActivity() {

  lateinit var splitInstallManager: SplitInstallManager

  // Holds the current deep link Uri as Compose state so both a cold start (onCreate) and a
  // deep link arriving while already running (onNewIntent) feed AppNavigation the same way.
  private var deepLinkUri by mutableStateOf<android.net.Uri?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    val appGraph = (applicationContext as BillionBeersApplication).appGraph
    splitInstallManager = appGraph.splitInstallManager
    enableEdgeToEdge()
    deepLinkUri = intent?.data
    setContent {
      CompositionLocalProvider(
        LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
        LocalSplitInstallManager provides splitInstallManager,
      ) {
        BillionBeersTheme(darkTheme = isDarkTheme(appGraph.themeController)) {
          DebugDrawerHost(appGraph = appGraph) { AppNavigation(deepLinkUri = deepLinkUri) }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    deepLinkUri = intent.data
  }
}

// SYSTEM (the only mode reachable outside a debug build with the drawer wired in) falls straight
// back to isSystemInDarkTheme(), so this changes nothing for release builds.
@Composable
private fun isDarkTheme(themeController: ThemeController): Boolean {
  val mode by themeController.mode.collectAsState()
  return when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
  }
}
