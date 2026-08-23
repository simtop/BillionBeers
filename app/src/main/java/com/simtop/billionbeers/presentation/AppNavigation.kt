package com.simtop.billionbeers.presentation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import com.simtop.beerdomain.domain.models.Beer
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppNavigation(
  modifier: Modifier = Modifier,
  deepLinkUri: Uri? = null,
  viewModel: AppNavigationViewModel = metroViewModel(),
) {
  val backStack = rememberNavBackStack(BeersList)
  val navigate = rememberDynamicFeatureNavigator(backStack)
  val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
  val isListDetailStack =
    backStack.size > 1 &&
      backStack.firstOrNull() == BeersList &&
      backStack.drop(1).all { it is BeerDetail }
  val isExpandedWindow =
    currentWindowAdaptiveInfoV2()
      .windowSizeClass
      .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
  val showDetailBackButton = !(isListDetailStack && isExpandedWindow)

  fun navigateToBeerDetail(beer: Beer) {
    backStack.removeAll { it is BeerDetail }
    navigate(BeerDetail(beer))
  }

  LaunchedEffect(deepLinkUri) {
    when (val destination = deepLinkUri?.toDeepLinkDestination()) {
      null -> Unit
      DeepLinkDestination.BeersList -> {
        backStack.clear()
        backStack.add(BeersList)
      }
      // An unresolvable beer id stays on the current screen.
      is DeepLinkDestination.BeerDetail ->
        viewModel.resolveBeer(destination.beerId)?.let(::navigateToBeerDetail)
    }
  }

  NavDisplay(
    modifier = modifier,
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    sceneStrategies = listOf(listDetailStrategy),
    entryProvider =
      entryProvider {
        entry<BeersList>(
          metadata =
            ListDetailSceneStrategy.listPane(
              detailPlaceholder = {
                Box(modifier = Modifier.fillMaxSize().testTag("beer_detail_placeholder"))
              }
            )
        ) {
          BeersListScreen(
            onBeerClick = ::navigateToBeerDetail,
            onSearchClick = { navigate(BeersSearch) },
            onBrowseClick = { navigate(BeerBrowse) },
          )
        }

        entry<BeersSearch> {
          BeersSearchScreen(
            onBeerClick = ::navigateToBeerDetail,
            onBack = { backStack.removeLastOrNull() },
          )
        }

        entry<BeerDetail>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
          DynamicFeatureContent(
            key = key,
            onBack = { backStack.removeLastOrNull() },
            showBackButton = showDetailBackButton,
          )
        }

        // onNavigate lets browse push a beer's detail from inside the module - routed through
        // `navigate`, so it is gated like every other caller.
        entry<BeerBrowse> { key ->
          DynamicFeatureContent(
            key = key,
            onBack = { backStack.removeLastOrNull() },
            onNavigate = { destination ->
              if (destination is BeerDetail) {
                navigateToBeerDetail(destination.beer)
              } else {
                navigate(destination)
              }
            },
          )
        }
      },
  )
}
