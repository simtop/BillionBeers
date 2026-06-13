package com.simtop.billionbeers.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.simtop.feature.beerslist.BeersListScreen
import com.simtop.navigation.BeerDetail
import com.simtop.navigation.BeersList
import com.simtop.navigation.DynamicFeatureContent
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
                // The module is guaranteed installed (gated on the list screen before navigating).
                // DynamicFeatureContent remembers the reflective lookup so it runs once, not per recomposition.
                DynamicFeatureContent(
                    key = key,
                    className = FeatureConstants.BEER_DETAIL_PROVIDER_CLASS,
                    onBack = { backStack.removeLastOrNull() },
                  )
      }
    }
  )
}
