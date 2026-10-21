@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simtop.feature.beerdetail.presentation

import androidx.compose.runtime.Composable
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.component.AccessibilityMatrixPreview
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme

@PreviewLightDark
@Composable
internal fun ComposeBeerDetailPreview() {
  BillionBeersTheme {
    ComposeBeerDetail(
      beer =
        Beer.empty.copy(
          name = "Buzz",
          tagline = "A Real Bitter Experience.",
          description =
            "A light, crisp and bitter IPA brewed with English and American hops. A small batch brewed only once.",
          abv = 4.5,
          ibu = 60.0,
          foodPairing =
            listOf("Spicy chicken tikka masala", "Grilled chicken quesadilla", "Pastrami on rye"),
          styleName = "IPA (Indian Pale Ale)",
          breweryName = "ChuckleCraft Brewery",
          srm = 9,
          releasedYear = 1980,
          minServingTemperature = 4,
          maxServingTemperature = 8,
          fermentationMethod = "Ale",
          ingredients = listOf("Pale malt", "Cascade hops", "American ale yeast"),
          recommendedGlasses = listOf("Pint glass", "Tulip"),
        ),
      onBackClick = {},
      onToggleAvailability = {},
    )
  }
}

@PreviewLightDark
@Composable
internal fun ComposeBeerDetailWithoutEnrichedFieldsPreview() {
  BillionBeersTheme {
    ComposeBeerDetail(
      beer =
        Beer.empty.copy(
          name = "Buzz",
          tagline = "A Real Bitter Experience.",
          description = "A light, crisp and bitter IPA.",
          abv = 4.5,
          ibu = 60.0,
        ),
      onBackClick = {},
      onToggleAvailability = {},
    )
  }
}

@AccessibilityMatrixPreview
@Composable
@Suppress("PreviewPublic")
internal fun ComposeBeerDetailAccessibilityMatrixPreview() {
  BillionBeersTheme {
    ComposeBeerDetail(
      beer =
        Beer.empty.copy(
          name = "A Very Long Beer Name That Must Wrap Correctly",
          tagline = "A long tagline for large-font accessibility coverage.",
          description =
            "A deliberately long description that exercises scrolling, wrapping, RTL layout, and " +
              "compact versus expanded widths.",
          abv = 5.6,
          ibu = 41.5,
          foodPairing = listOf("Spicy chicken tikka masala", "Grilled chicken quesadilla"),
          styleName = "IPA (Indian Pale Ale)",
          breweryName = "A Brewery With A Deliberately Long Name For Accessibility Testing",
          availability = true,
          ingredients = listOf("Pale malt", "Cascade hops", "American ale yeast"),
          recommendedGlasses = listOf("Pint glass", "Tulip"),
        ),
      onBackClick = {},
      onToggleAvailability = {},
    )
  }
}
