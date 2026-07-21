package com.simtop.billionbeers.devbeerslist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.feature.beerslist.BeersListScreen
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

/**
 * Hosts only [BeersListScreen] against a fake, in-memory
 * [com.simtop.beerdomain.domain.repositories.BeersRepository] (see
 * [com.simtop.billionbeers.devbeerslist.di.DevBeersRepositoryModule]) - no network, no database, no
 * beerdetail dynamic feature. The screen just reports taps to its caller, so there is nothing to
 * navigate to here.
 */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val appGraph = (applicationContext as DevBeersListApplication).appGraph
    enableEdgeToEdge()
    setContent {
      CompositionLocalProvider(LocalMetroViewModelFactory provides appGraph.metroViewModelFactory) {
        BillionBeersTheme {
          BeersListScreen(onBeerClick = {}, onSearchClick = {}, onBrowseClick = {})
        }
      }
    }
  }
}
