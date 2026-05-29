package com.simtop.billionbeers.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.simtop.feature.beerslist.BeersListScreen
import com.simtop.navigation.BeerDetail
import com.simtop.navigation.BeerDetailProvider
import com.simtop.navigation.BeersList
import com.simtop.navigation.FeatureConstants

@Composable
fun AppNavigation(
  splitInstallManager: SplitInstallManager = SplitInstallManagerFactory.create(LocalContext.current)
) {
  val backStack = rememberNavBackStack(BeersList)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
      entry<BeersList> {
        BeersListScreen(
          onBeerClick = { beer -> backStack.add(BeerDetail(beer)) },
          splitInstallManager = splitInstallManager,
        )
      }

      entry<BeerDetail> { key ->
        // Load the provider via reflection
        // We can assume it's installed because we check before navigating
        val providerClass = Class.forName(FeatureConstants.BEER_DETAIL_PROVIDER_CLASS)
        val provider = providerClass.getDeclaredConstructor().newInstance() as BeerDetailProvider

        provider.BeerDetailScreen(beer = key.beer, onBackClick = { backStack.removeLastOrNull() })
      }
    }
  )
}
