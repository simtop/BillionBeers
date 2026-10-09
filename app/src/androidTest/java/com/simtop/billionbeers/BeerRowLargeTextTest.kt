package com.simtop.billionbeers

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.beerdomain.fakes.fakeBeerModel
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.presentation_utils.R as PresentationR
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BeerRowLargeTextTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun compactRowKeepsBothAvailabilityLabelsReadableAtDoubleTextSize() {
    var beer by mutableStateOf(fakeBeerModel.copy(abv = 7.0, ibu = 71.0))
    compose.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
        BillionBeersTheme {
          ComposeBeersListItem(modifier = Modifier.width(360.dp), beer = beer)
        }
      }
    }

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    for (resource in
      listOf(PresentationR.string.beer_available, PresentationR.string.beer_out_of_stock)) {
      compose
        .onNodeWithText(context.getString(resource))
        .assertIsDisplayed()
        .assertWidthIsAtLeast(60.dp)
      val rowHeight =
        compose.onNodeWithTag("beer_list_item").fetchSemanticsNode().boundsInRoot.height
      val density = context.resources.displayMetrics.density
      assertTrue(
        "A beer row should not consume a phone viewport: ${rowHeight / density}dp",
        rowHeight / density <= 320f,
      )
      compose.runOnIdle { beer = beer.copy(availability = false) }
    }
  }
}
