package com.simtop.billionbeers.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.billionbeers.BillionBeersApplication
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

class MainActivity : ComponentActivity() {

  lateinit var splitInstallManager: SplitInstallManager

  override fun onCreate(savedInstanceState: Bundle?) {
    val appGraph = (applicationContext as BillionBeersApplication).appGraph
    splitInstallManager = appGraph.splitInstallManager
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      CompositionLocalProvider(LocalMetroViewModelFactory provides appGraph.metroViewModelFactory) {
        BillionBeersTheme { AppNavigation(splitInstallManager = splitInstallManager) }
      }
    }
  }
}
