package com.simtop.billionbeers

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.feature.savedfilters.R as SavedFiltersR
import com.simtop.feature.savedfilters.SavedFiltersContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runtime checks for the Android renderer, isolated from storage and host feedback. */
class SavedFiltersLayoutTest {
  @get:Rule val compose = createComposeRule()

  private val initialPresets =
    List(SavedFilterPreset.MAX_COUNT) { index ->
      SavedFilterPreset("preset-$index", "Favorite IPA ${index + 1}", BeersQuery(), index.toLong())
    }
  private var presets by mutableStateOf(initialPresets)
  private val appliedIds = mutableListOf<String>()
  private val renamedIds = mutableListOf<String>()
  private val deletedIds = mutableListOf<String>()
  private val renameLabel =
    InstrumentationRegistry.getInstrumentation()
      .targetContext
      .getString(SavedFiltersR.string.savedfilters_rename)
  private val deleteLabel =
    InstrumentationRegistry.getInstrumentation()
      .targetContext
      .getString(SavedFiltersR.string.savedfilters_delete)

  @Test
  fun allTenPresetsCanBeAppliedRenamedAndDeletedAtCompactWidthWithLargeText() {
    showContent()

    initialPresets.forEach { preset ->
      scrollTo(preset)
      action(preset, preset.name).assertIsDisplayed().assertWidthIsAtLeast(240.dp).performClick()
    }
    initialPresets.forEach { preset ->
      scrollTo(preset)
      action(preset, renameLabel).assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
      editor(preset)
        .assertIsDisplayed()
        .assertWidthIsAtLeast(240.dp)
        .performTextReplacement("Renamed ${preset.name}")
      action(preset, renameLabel).performClick()
      action(preset, "Renamed ${preset.name}").assertIsDisplayed()
      action(preset, deleteLabel).assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
    }

    compose.runOnIdle {
      val expectedIds = initialPresets.map(SavedFilterPreset::id)
      assertEquals(expectedIds, appliedIds)
      assertEquals(expectedIds, renamedIds)
      assertEquals(expectedIds, deletedIds)
      assertEquals(emptyList<SavedFilterPreset>(), presets)
    }
  }

  @Test
  fun failedRenameKeepsItsDraftAfterScrollingAndReorderingAtLargeText() {
    showContent(renameSucceeds = false)
    val first = initialPresets.first()
    scrollTo(first)
    action(first, renameLabel).performClick()
    editor(first).performTextReplacement("Unsaved draft")
    action(first, renameLabel).performClick()

    scrollTo(initialPresets.last())
    // Compose can retain a focused offscreen editor; verify scrolling rather than disposal.
    editor(first).assertIsNotDisplayed()
    compose.runOnIdle { presets = presets.reversed() }
    scrollTo(first)
    editor(first).assertIsDisplayed().assertWidthIsAtLeast(240.dp)
    action(first, "Unsaved draft").assertIsDisplayed()
    compose.runOnIdle {
      assertEquals(initialPresets.reversed(), presets)
      assertEquals(listOf(first.id), renamedIds)
    }
  }

  private fun showContent(renameSucceeds: Boolean = true) {
    compose.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
        BillionBeersTheme {
          SavedFiltersContent(
            presets = presets,
            onApply = { appliedIds += it.id },
            onRename = { preset, name ->
              renamedIds += preset.id
              if (renameSucceeds) {
                presets = presets.map { if (it.id == preset.id) it.copy(name = name) else it }
              }
              renameSucceeds
            },
            onDelete = { preset ->
              deletedIds += preset.id
              presets = presets.filterNot { it.id == preset.id }
              true
            },
            modifier = Modifier.width(320.dp).fillMaxHeight(),
          )
        }
      }
    }
  }

  private fun row(preset: SavedFilterPreset): SemanticsNodeInteraction =
    compose.onNodeWithTag("saved-filter-${preset.id}")

  private fun scrollTo(preset: SavedFilterPreset) {
    var index = -1
    compose.runOnIdle { index = presets.indexOfFirst { it.id == preset.id } }
    compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
    row(preset).performScrollTo()
  }

  private fun action(preset: SavedFilterPreset, text: String): SemanticsNodeInteraction =
    compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("saved-filter-${preset.id}")))

  private fun editor(preset: SavedFilterPreset): SemanticsNodeInteraction =
    compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("saved-filter-${preset.id}")))
}
