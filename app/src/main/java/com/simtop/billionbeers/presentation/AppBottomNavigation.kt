package com.simtop.billionbeers.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.simtop.navigation.BeersList
import com.simtop.navigation.Favorites
import com.simtop.presentation_utils.R

@Composable
fun AppBottomNavigation(
  selectedTab: NavKey?,
  onTabSelect: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val itemColors =
    NavigationBarItemDefaults.colors(
      selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
      selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
      indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
      unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
      unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    )

  NavigationBar(
    modifier = modifier.testTag("app_bottom_navigation"),
    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
  ) {
    NavigationBarItem(
      modifier = Modifier.testTag("home_tab"),
      selected = selectedTab == BeersList,
      onClick = { onTabSelect(BeersList) },
      icon = { Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.home_tab)) },
      label = { Text(stringResource(R.string.home_tab)) },
      colors = itemColors,
    )
    NavigationBarItem(
      modifier = Modifier.testTag("favorites_tab"),
      selected = selectedTab == Favorites,
      onClick = { onTabSelect(Favorites) },
      icon = {
        Icon(
          Icons.Filled.Favorite,
          contentDescription = stringResource(R.string.favorites_title),
        )
      },
      label = { Text(stringResource(R.string.favorites_title)) },
      colors = itemColors,
    )
  }
}
