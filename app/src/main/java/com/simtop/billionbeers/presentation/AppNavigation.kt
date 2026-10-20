package com.simtop.billionbeers.presentation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import com.simtop.beerdomain.domain.errors.SaveFilterPresetError
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.billionbeers.R
import com.simtop.core.core.Either
import com.simtop.feature.beerslist.BeersListScreen
import com.simtop.navigation.AndroidNavigationActions
import com.simtop.navigation.DynamicFeatureContent
import com.simtop.navigation.contract.PortableRoute
import com.simtop.navigation.toPortableRoute
import dev.zacsweers.metrox.viewmodel.metroViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppNavigation(
  modifier: Modifier = Modifier,
  deepLinkUri: Uri? = null,
  deepLinkDeliveryId: Long = 0L,
  onDeepLinkConsumed: (Long) -> Unit = {},
  viewModel: AppNavigationViewModel = metroViewModel(),
) {
  val backStack = rememberNavBackStack(PortableRoute.BeersList)
  val navigate = rememberDynamicFeatureNavigator(backStack)
  val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
  val isListDetailStack =
    backStack.size > 1 &&
      backStack.firstOrNull() in
        setOf(PortableRoute.BeersList, PortableRoute.Favorites, PortableRoute.SavedFilterPresets) &&
      backStack.drop(1).all { it is PortableRoute.BeerDetail }
  val isExpandedWindow =
    currentWindowAdaptiveInfoV2()
      .windowSizeClass
      .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
  val showDetailBackButton = !(isListDetailStack && isExpandedWindow)

  fun navigateToBeerDetail(beerId: String) {
    backStack.removeAll { it is PortableRoute.BeerDetail }
    navigate(PortableRoute.BeerDetail(beerId))
  }

  LaunchedEffect(deepLinkUri, deepLinkDeliveryId) {
    val uri = deepLinkUri ?: return@LaunchedEffect
    handleDeepLink(uri, backStack, viewModel, ::navigateToBeerDetail)
    // Handoff to the split navigator consumes the request; it owns any pending installation.
    onDeepLinkConsumed(deepLinkDeliveryId)
  }

  val selectedTab = backStack.firstOrNull()
  val showBottomBar =
    selectedTab == PortableRoute.BeersList ||
      selectedTab == PortableRoute.Favorites ||
      selectedTab == PortableRoute.SavedFilterPresets
  val navigationPresentation = resolveNavigationPresentation(showBottomBar, isExpandedWindow)
  val snackbarHostState = remember { SnackbarHostState() }
  val presetLimitMessage = stringResource(R.string.filter_preset_limit_reached)
  val presetSaveFailedMessage = stringResource(R.string.filter_preset_save_failed)

  suspend fun savePreset(name: String, query: BeersQuery): Boolean =
    handleSavePresetResult(
      result = viewModel.savePreset(name, query),
      snackbarHostState = snackbarHostState,
      presetLimitMessage = presetLimitMessage,
      presetSaveFailedMessage = presetSaveFailedMessage,
    )
  val navigationActions =
    AndroidNavigationActions(
      navigate = navigate,
      onBeerClick = { beer -> navigateToBeerDetail(beer.id) },
      onBack = { backStack.removeLastOrNull() },
      onSaveQuery = ::savePreset,
    )

  Scaffold(
    modifier = modifier,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    // The destination screens own their top-bar and content insets. Keeping the root inset-free
    // lets those top bars draw behind the status bar instead of placing the entire NavDisplay
    // below it. The compact NavigationBar contributes its measured height to innerPadding.
    contentWindowInsets = WindowInsets(0),
    bottomBar = {
      if (navigationPresentation == NavigationPresentation.BOTTOM_BAR) {
        AppBottomNavigation(
          selectedTab = selectedTab,
          onTabSelect = { tab ->
            backStack.clear()
            backStack.add(tab)
          },
        )
      }
    },
  ) { innerPadding ->
    Row(Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
      if (navigationPresentation == NavigationPresentation.RAIL) {
        AppNavigationRail(
          selectedTab = selectedTab,
          onTabSelect = { tab ->
            backStack.clear()
            backStack.add(tab)
          },
        )
      }
      NavDisplay(
        modifier = Modifier.weight(1f),
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(listDetailStrategy),
        entryProvider =
          entryProvider {
            entry<PortableRoute.BeersList>(
              metadata =
                ListDetailSceneStrategy.listPane(
                  detailPlaceholder = {
                    Box(modifier = Modifier.fillMaxSize().testTag("beer_detail_placeholder"))
                  }
                )
            ) {
              BeersListScreen(
                onBeerClick = { beer -> navigateToBeerDetail(beer.id) },
                onSearchClick = { navigate(PortableRoute.BeersSearch) },
                onBrowseClick = { navigate(PortableRoute.BeerBrowse) },
                onSaveQuery = ::savePreset,
              )
            }

            viewModel.navigationContributions.forEach { contribution ->
              contribution.contribute(this, navigationActions)
            }

            entry<PortableRoute.BeerDetail>(metadata = ListDetailSceneStrategy.detailPane()) { key
              ->
              DynamicFeatureContent(
                key = key,
                onBack = { backStack.removeLastOrNull() },
                showBackButton = showDetailBackButton,
              )
            }

            // onNavigate lets browse push a beer's detail from inside the module - routed through
            // `navigate`, so it is gated like every other caller.
            entry<PortableRoute.BeerBrowse> { key ->
              DynamicFeatureContent(
                key = key,
                onBack = { backStack.removeLastOrNull() },
                onNavigate = { destination ->
                  if (destination is PortableRoute.BeerDetail) {
                    navigateToBeerDetail(destination.beerId)
                  } else {
                    navigate(destination)
                  }
                },
                onSaveQuery = ::savePreset,
              )
            }
          },
      )
    }
  }
}

private enum class NavigationPresentation {
  HIDDEN,
  BOTTOM_BAR,
  RAIL,
}

private fun resolveNavigationPresentation(
  showNavigation: Boolean,
  isExpandedWindow: Boolean,
): NavigationPresentation =
  when {
    !showNavigation -> NavigationPresentation.HIDDEN
    isExpandedWindow -> NavigationPresentation.RAIL
    else -> NavigationPresentation.BOTTOM_BAR
  }

private suspend fun handleDeepLink(
  uri: Uri,
  backStack: MutableList<NavKey>,
  viewModel: AppNavigationViewModel,
  navigateToBeerDetail: (String) -> Unit,
) {
  when (val destination = uri.toPortableRoute()) {
    null -> Unit
    PortableRoute.BeersList -> {
      backStack.clear()
      backStack.add(PortableRoute.BeersList)
    }
    PortableRoute.Favorites -> {
      backStack.clear()
      backStack.add(PortableRoute.Favorites)
    }
    // An unresolvable beer id stays on the current screen.
    is PortableRoute.BeerDetail ->
      viewModel.resolveBeer(destination.beerId)?.let { navigateToBeerDetail(destination.beerId) }
    else -> Unit
  }
}

private suspend fun handleSavePresetResult(
  result: Either<SaveFilterPresetError, Unit>,
  snackbarHostState: SnackbarHostState,
  presetLimitMessage: String,
  presetSaveFailedMessage: String,
): Boolean =
  when (result) {
    is Either.Right -> true
    is Either.Left -> {
      when (result.value) {
        SaveFilterPresetError.InvalidName -> Unit
        SaveFilterPresetError.CapacityReached -> snackbarHostState.showSnackbar(presetLimitMessage)
        is SaveFilterPresetError.Unknown -> snackbarHostState.showSnackbar(presetSaveFailedMessage)
      }
      false
    }
  }
