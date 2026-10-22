package com.simtop.billionbeers.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import com.simtop.billionbeers.shared.designsystem.components.rememberNavigationLabel
import com.simtop.billionbeers.shared.designsystem.controls.BrandIcon
import com.simtop.billionbeers.shared.designsystem.controls.BrandNavigationBar
import com.simtop.billionbeers.shared.designsystem.controls.BrandNavigationBarItem
import com.simtop.billionbeers.shared.designsystem.controls.BrandNavigationRail
import com.simtop.billionbeers.shared.designsystem.controls.BrandNavigationRailItem
import com.simtop.navigation.contract.PortableRoute
import com.simtop.presentation_utils.R

@Composable
fun AppBottomNavigation(
  selectedTab: NavKey?,
  onTabSelect: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val navigationLabel =
    rememberNavigationLabel(
      listOf(
        stringResource(R.string.home_tab),
        stringResource(R.string.favorites_title),
        stringResource(R.string.saved_filters_title),
      )
    )

  BrandNavigationBar(modifier = modifier.testTag("app_bottom_navigation")) {
    BrandNavigationBarItem(
      modifier = Modifier.testTag("home_tab"),
      selected = selectedTab == PortableRoute.BeersList,
      onClick = { onTabSelect(PortableRoute.BeersList) },
      icon = {
        BrandIcon(Icons.Filled.Home, contentDescription = stringResource(R.string.home_tab))
      },
      label = { navigationLabel(stringResource(R.string.home_tab)) },
    )
    BrandNavigationBarItem(
      modifier = Modifier.testTag("favorites_tab"),
      selected = selectedTab == PortableRoute.Favorites,
      onClick = { onTabSelect(PortableRoute.Favorites) },
      icon = {
        BrandIcon(
          Icons.Filled.Favorite,
          contentDescription = stringResource(R.string.favorites_title),
        )
      },
      label = { navigationLabel(stringResource(R.string.favorites_title)) },
    )
    BrandNavigationBarItem(
      modifier = Modifier.testTag("saved_filters_tab"),
      selected = selectedTab == PortableRoute.SavedFilterPresets,
      onClick = { onTabSelect(PortableRoute.SavedFilterPresets) },
      icon = {
        BrandIcon(
          Icons.AutoMirrored.Filled.List,
          contentDescription = stringResource(R.string.saved_filters_title),
        )
      },
      label = { navigationLabel(stringResource(R.string.saved_filters_title)) },
    )
  }
}

@Composable
fun AppNavigationRail(
  selectedTab: NavKey?,
  onTabSelect: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val navigationLabel =
    rememberNavigationLabel(
      listOf(
        stringResource(R.string.home_tab),
        stringResource(R.string.favorites_title),
        stringResource(R.string.saved_filters_title),
      )
    )
  BrandNavigationRail(modifier = modifier.testTag("app_navigation_rail")) {
    BrandNavigationRailItem(
      modifier = Modifier.testTag("home_tab"),
      selected = selectedTab == PortableRoute.BeersList,
      onClick = { onTabSelect(PortableRoute.BeersList) },
      icon = {
        BrandIcon(Icons.Filled.Home, contentDescription = stringResource(R.string.home_tab))
      },
      label = { navigationLabel(stringResource(R.string.home_tab)) },
    )
    BrandNavigationRailItem(
      modifier = Modifier.testTag("favorites_tab"),
      selected = selectedTab == PortableRoute.Favorites,
      onClick = { onTabSelect(PortableRoute.Favorites) },
      icon = {
        BrandIcon(
          Icons.Filled.Favorite,
          contentDescription = stringResource(R.string.favorites_title),
        )
      },
      label = { navigationLabel(stringResource(R.string.favorites_title)) },
    )
    BrandNavigationRailItem(
      modifier = Modifier.testTag("saved_filters_tab"),
      selected = selectedTab == PortableRoute.SavedFilterPresets,
      onClick = { onTabSelect(PortableRoute.SavedFilterPresets) },
      icon = {
        BrandIcon(
          Icons.AutoMirrored.Filled.List,
          contentDescription = stringResource(R.string.saved_filters_title),
        )
      },
      label = { navigationLabel(stringResource(R.string.saved_filters_title)) },
    )
  }
}
