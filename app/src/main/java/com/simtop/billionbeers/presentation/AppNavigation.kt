package com.simtop.billionbeers.presentation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.feature.beersearch.BeersSearchScreen
import com.simtop.feature.beerslist.BeersListScreen
import com.simtop.navigation.BeerBrowse
import com.simtop.navigation.BeerDetail
import com.simtop.navigation.BeersList
import com.simtop.navigation.BeersSearch
import com.simtop.navigation.DeepLinkDestination
import com.simtop.navigation.DynamicFeatureContent
import com.simtop.navigation.FeatureConstants
import com.simtop.navigation.toDeepLinkDestination
import com.simtop.presentation_utils.core.DynamicFeatureLoader
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun AppNavigation(deepLinkUri: Uri? = null, viewModel: AppNavigationViewModel = metroViewModel()) {
  val backStack = rememberNavBackStack(BeersList)

  // Beer resolved from an incoming deep link, pending the beerdetail dynamic feature install -
  // mirrors BeersListScreen's own installingBeer/DynamicFeatureLoader gate, since BeerDetail can
  // only be pushed onto the back stack once that module is installed.
  var pendingDeepLinkBeer by remember { mutableStateOf<Beer?>(null) }

  LaunchedEffect(deepLinkUri) {
    when (val destination = deepLinkUri?.toDeepLinkDestination()) {
      null -> Unit
      DeepLinkDestination.BeersList -> {
        backStack.clear()
        backStack.add(BeersList)
      }
      is DeepLinkDestination.BeerDetail -> {
        pendingDeepLinkBeer = viewModel.resolveBeer(destination.beerId)
      }
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<BeersList> {
          BeersListScreen(
            onBeerClick = { beer -> backStack.add(BeerDetail(beer)) },
            onSearchClick = { backStack.add(BeersSearch) },
            onBrowseClick = { backStack.add(BeerBrowse) },
          )
        }

        entry<BeersSearch> {
          BeersSearchScreen(
            onBeerClick = { beer -> backStack.add(BeerDetail(beer)) },
            onBack = { backStack.removeLastOrNull() },
          )
        }

        entry<BeerDetail> { key ->
          // The module is guaranteed installed (gated on the list screen before navigating).
          // DynamicFeatureContent remembers the reflective lookup so it runs once, not per
          // recomposition.
          DynamicFeatureContent(
            key = key,
            className = FeatureConstants.BEER_DETAIL_PROVIDER_CLASS,
            onBack = { backStack.removeLastOrNull() },
          )
        }

        entry<BeerBrowse> { key ->
          // Same install guarantee as BeerDetail (gated on the list screen's browse icon).
          // onNavigate lets browse push a beer's detail onto this back stack from inside the
          // module.
          DynamicFeatureContent(
            key = key,
            className = FeatureConstants.BEER_BROWSE_PROVIDER_CLASS,
            onBack = { backStack.removeLastOrNull() },
            onNavigate = { destination -> backStack.add(destination) },
          )
        }
      },
  )

  pendingDeepLinkBeer?.let { beer ->
    DynamicFeatureLoader(featureName = FeatureConstants.BEER_DETAIL_MODULE) {
      LaunchedEffect(beer) {
        backStack.add(BeerDetail(beer))
        pendingDeepLinkBeer = null
      }
    }
  }
}
