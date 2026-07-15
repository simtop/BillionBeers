package com.simtop.billionbeers.devbeerslist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.feature.beerslist.BeersListScreen
import com.simtop.presentation_utils.core.LocalSplitInstallManager
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

/**
 * Hosts only [BeersListScreen] against a fake, in-memory
 * [com.simtop.beerdomain.domain.repositories.BeersRepository] (see
 * [com.simtop.billionbeers.devbeerslist.di.DevBeersRepositoryModule]) - no network, no database, no
 * beerdetail dynamic feature. Tapping a beer still triggers the module's own DynamicFeatureLoader
 * gate; the install attempt fails harmlessly since beerdetail isn't part of this app.
 */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val appGraph = (applicationContext as DevBeersListApplication).appGraph
    enableEdgeToEdge()
    setContent {
      CompositionLocalProvider(
        LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
        LocalSplitInstallManager provides appGraph.splitInstallManager,
      ) {
        BillionBeersTheme { BeersListScreen(onBeerClick = {}, onSearchClick = {}) }
      }
    }
  }
}
