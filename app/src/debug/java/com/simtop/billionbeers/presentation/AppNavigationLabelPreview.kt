package com.simtop.billionbeers.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.navigation.contract.PortableRoute

@Preview(locale = "es", name = "Spanish", group = "Navigation labels")
@Preview(locale = "es", fontScale = 1.5f, name = "Spanish large", group = "Navigation labels")
@Preview(locale = "es", fontScale = 2f, name = "Spanish largest", group = "Navigation labels")
@Composable
internal fun AppNavigationLabelPreview() {
  BillionBeersTheme {
    AppBottomNavigation(
      selectedTab = PortableRoute.SavedFilterPresets,
      onTabSelect = {},
      modifier = Modifier.width(360.dp),
    )
  }
}

@Preview(locale = "es", name = "Spanish rail", group = "Navigation labels")
@Preview(locale = "es", fontScale = 1.5f, name = "Spanish rail large", group = "Navigation labels")
@Preview(locale = "es", fontScale = 2f, name = "Spanish rail largest", group = "Navigation labels")
@Composable
internal fun AppNavigationRailLabelPreview() {
  BillionBeersTheme {
    Box(Modifier.height(420.dp)) {
      AppNavigationRail(selectedTab = PortableRoute.SavedFilterPresets, onTabSelect = {})
    }
  }
}
