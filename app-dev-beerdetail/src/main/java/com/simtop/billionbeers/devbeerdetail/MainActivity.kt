package com.simtop.billionbeers.devbeerdetail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.presentation_utils.core.LocalSplitInstallManager
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val appGraph = (applicationContext as DevBeerDetailApplication).appGraph
    enableEdgeToEdge()
    setContent {
      CompositionLocalProvider(
        LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
        LocalSplitInstallManager provides appGraph.splitInstallManager,
      ) {
        BillionBeersTheme {
          // TODO: host BeerDetail's real screen here, e.g.:
          //   BeerDetailScreen(onBackClick = {})
          // If the screen needs data its ViewModel doesn't fetch itself (like BeerDetail's Beer
          // parameter), construct a sample instance here or seed it via a fake repository in
          // di/DevFakesModule.kt instead.
        }
      }
    }
  }
}
