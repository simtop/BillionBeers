package com.simtop.billionbeers.presentation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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

  // URI equality does not identify a delivery: an identical warm link is still a new request.
  private var deepLinkUri by mutableStateOf<android.net.Uri?>(null)
  private var deepLinkDeliveryId by mutableLongStateOf(0L)

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    val appGraph = (applicationContext as BillionBeersApplication).appGraph
    splitInstallManager = appGraph.splitInstallManager
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }
    if (savedInstanceState?.getBoolean(DEEP_LINK_CONSUMED) != true) {
      deepLinkUri = intent?.data
    }
    setContent {
      CompositionLocalProvider(
        LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
        LocalSplitInstallManager provides splitInstallManager,
      ) {
        BillionBeersTheme(darkTheme = isDarkTheme(appGraph.themeController)) {
          DebugDrawerHost(appGraph = appGraph) {
            AppNavigation(
              deepLinkUri = deepLinkUri,
              deepLinkDeliveryId = deepLinkDeliveryId,
              onDeepLinkConsumed = ::consumeDeepLink,
            )
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    deepLinkDeliveryId++
    deepLinkUri = intent.data
  }

  override fun onSaveInstanceState(outState: Bundle) {
    // Do not replay the Activity's old Intent over the user's restored navigation stack.
    outState.putBoolean(DEEP_LINK_CONSUMED, deepLinkUri == null)
    super.onSaveInstanceState(outState)
  }

  private fun consumeDeepLink(deliveryId: Long) {
    // A suspended lookup for an older request must not clear a newer delivery.
    if (deliveryId == deepLinkDeliveryId) deepLinkUri = null
  }

  private companion object {
    const val DEEP_LINK_CONSUMED = "deep_link_consumed"
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
