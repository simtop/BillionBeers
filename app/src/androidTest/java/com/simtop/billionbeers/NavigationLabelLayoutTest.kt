package com.simtop.billionbeers

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.presentation.AppBottomNavigation
import com.simtop.billionbeers.presentation.AppNavigationRail
import com.simtop.navigation.contract.PortableRoute
import com.simtop.testing_utils_android.withFailureScreenshot
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationLabelLayoutTest {
  private val compose = createComposeRule()
  @get:Rule val failureScreenshots = withFailureScreenshot(compose)

  @Test fun spanishBottomLabelsRemainCentered() = verifyLabels(rail = false, fontScale = 1f)

  @Test fun largeSpanishBottomLabelsRemainCentered() = verifyLabels(rail = false, fontScale = 2f)

  @Test fun spanishRailLabelsRemainCentered() = verifyLabels(rail = true, fontScale = 1f)

  @Test fun largeSpanishRailLabelsRemainCentered() = verifyLabels(rail = true, fontScale = 2f)

  private fun verifyLabels(rail: Boolean, fontScale: Float) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    var textScale by mutableStateOf(fontScale)
    var selected by mutableStateOf<androidx.navigation3.runtime.NavKey>(PortableRoute.BeersList)
    compose.setContent {
      val configuration =
        Configuration(context.resources.configuration).apply {
          setLocale(Locale.forLanguageTag("es"))
          this.fontScale = textScale
        }
      val spanishContext = context.createConfigurationContext(configuration)
      val density = LocalDensity.current.density
      CompositionLocalProvider(
        LocalContext provides spanishContext,
        LocalConfiguration provides configuration,
        LocalDensity provides Density(density, textScale),
      ) {
        BillionBeersTheme {
          Box(Modifier.width(360.dp).height(700.dp)) {
            if (rail) {
              AppNavigationRail(selectedTab = selected, onTabSelect = { selected = it })
            } else {
              AppBottomNavigation(selectedTab = selected, onTabSelect = { selected = it })
            }
          }
        }
      }
    }
    assertLabels(rail)
    if (fontScale > 1f) {
      val largeHeight =
        if (rail) null
        else compose.onNodeWithTag("app_bottom_navigation").fetchSemanticsNode().boundsInRoot.height
      compose.runOnIdle { textScale = 1f }
      assertLabels(rail)
      if (largeHeight != null) {
        val normalHeight =
          compose.onNodeWithTag("app_bottom_navigation").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Label space must shrink again with smaller text", normalHeight < largeHeight)
      }
    }
  }

  private fun assertLabels(rail: Boolean) {
    val iconCenters = mutableListOf<Float>()
    for (label in listOf("Inicio", "Favoritos", "Filtros guardados")) {
      val icon = compose.onNodeWithContentDescription(label, useUnmergedTree = true)
      val text = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
      val iconBounds = icon.fetchSemanticsNode().boundsInRoot
      val textBounds = text.fetchSemanticsNode().boundsInRoot
      iconCenters += iconBounds.center.y
      val layouts = mutableListOf<TextLayoutResult>()
      text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
      val layout = layouts.single()
      assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1))
      for (line in 0 until layout.lineCount) {
        assertFalse("$label must not be truncated", layout.isLineEllipsized(line))
        assertTrue(
          "$label must fit horizontally",
          layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f,
        )
        if (layout.getLineStart(line) == layout.getLineEnd(line)) continue
        val center = textBounds.left + (layout.getLineLeft(line) + layout.getLineRight(line)) / 2
        assertEquals(
          "$label line $line must be centered below its icon",
          iconBounds.center.x,
          center,
          2f,
        )
      }
    }
    if (!rail) {
      iconCenters.forEach {
        assertEquals("Bottom icons must share a baseline", iconCenters.first(), it, 2f)
      }
    }
    compose.onNodeWithTag("saved_filters_tab").performClick().assertIsSelected()
    compose.onNodeWithTag("home_tab").performClick().assertIsSelected()
  }
}
