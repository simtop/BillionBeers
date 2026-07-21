package com.simtop.billionbeers.presentation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.simtop.feature.beersearch.BeersSearchScreen
import com.simtop.feature.beerslist.BeersListScreen
import com.simtop.navigation.BeerBrowse
import com.simtop.navigation.BeerDetail
import com.simtop.navigation.BeersList
import com.simtop.navigation.BeersSearch
import com.simtop.navigation.DeepLinkDestination
import com.simtop.navigation.DynamicFeatureContent
import com.simtop.navigation.toDeepLinkDestination
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun AppNavigation(deepLinkUri: Uri? = null, viewModel: AppNavigationViewModel = metroViewModel()) {
  val backStack = rememberNavBackStack(BeersList)
  val navigate = rememberDynamicFeatureNavigator(backStack)

  LaunchedEffect(deepLinkUri) {
    when (val destination = deepLinkUri?.toDeepLinkDestination()) {
      null -> Unit
      DeepLinkDestination.BeersList -> {
        backStack.clear()
        backStack.add(BeersList)
      }
      // An unresolvable beer id stays on the current screen.
      is DeepLinkDestination.BeerDetail ->
        viewModel.resolveBeer(destination.beerId)?.let { beer -> navigate(BeerDetail(beer)) }
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<BeersList> {
          BeersListScreen(
            onBeerClick = { beer -> navigate(BeerDetail(beer)) },
            onSearchClick = { navigate(BeersSearch) },
            onBrowseClick = { navigate(BeerBrowse) },
          )
        }

        entry<BeersSearch> {
          BeersSearchScreen(
            onBeerClick = { beer -> navigate(BeerDetail(beer)) },
            onBack = { backStack.removeLastOrNull() },
          )
        }

        entry<BeerDetail> { key ->
          DynamicFeatureContent(key = key, onBack = { backStack.removeLastOrNull() })
        }

        // onNavigate lets browse push a beer's detail from inside the module - routed through
        // `navigate`, so it is gated like every other caller.
        entry<BeerBrowse> { key ->
          DynamicFeatureContent(
            key = key,
            onBack = { backStack.removeLastOrNull() },
            onNavigate = navigate,
          )
        }
      },
  )
}
