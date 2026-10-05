package com.simtop.billionbeers.shared.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.billionbeers.shared.beerbrowse.BrowseStrings
import com.simtop.billionbeers.shared.beerdetail.BeerDetailStrings
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow

/** Desktop Compose renderer checks with controlled width/font scale and in-memory callbacks. */
@OptIn(ExperimentalTestApi::class)
class SavedFiltersLayoutTest {
  @Test
  fun allTenPresetsRemainUsableAtCompactWidthWithLargeText() {
    verifyAllActions(savedFilterStrings("Rename", "Delete"), "english")
  }

  @Test
  fun longFrenchActionsRemainUsableAtCompactWidthWithLargeText() {
    verifyAllActions(savedFilterStrings("Renommer", "Supprimer"), "french")
  }

  @Test
  fun failedRenameKeepsItsDraftAfterScrollingAndReordering() =
    runSkikoComposeUiTest(size = Size(320f, 640f), density = Density(1f, fontScale = 2f)) {
      val fixture = Fixture(renameSucceeds = false)
      val strings = savedFilterStrings("Rename", "Delete")
      showContent(fixture, strings)
      val first = fixture.initialPresets.first()
      scrollTo(fixture, first)
      action(first, strings.renameFilter).performClick()
      editor(first).performTextReplacement("Unsaved draft")
      action(first, strings.renameFilter).performClick()

      scrollTo(fixture, fixture.initialPresets.last())
      editor(first).assertIsNotDisplayed()
      runOnIdle { fixture.presets.value = fixture.presets.value.reversed() }
      scrollTo(fixture, first)
      capture("editor")
      editor(first).assertIsDisplayed().assertWidthIsAtLeast(240.dp)
      action(first, "Unsaved draft").assertIsDisplayed()
      runOnIdle {
        assertEquals(fixture.initialPresets.reversed(), fixture.presets.value)
        assertEquals(listOf(first.id), fixture.renamedIds)
      }
    }

  private fun verifyAllActions(strings: SharedAppStrings, imageName: String) =
    runSkikoComposeUiTest(size = Size(320f, 640f), density = Density(1f, fontScale = 2f)) {
      val fixture = Fixture()
      showContent(fixture, strings)
      capture(imageName)
      fixture.initialPresets.forEach { preset ->
        scrollTo(fixture, preset)
        action(preset, preset.name).assertIsDisplayed().assertWidthIsAtLeast(240.dp).performClick()
      }
      fixture.initialPresets.forEach { preset ->
        scrollTo(fixture, preset)
        action(preset, strings.renameFilter)
          .assertIsDisplayed()
          .assertHeightIsAtLeast(48.dp)
          .performClick()
        editor(preset)
          .assertIsDisplayed()
          .assertWidthIsAtLeast(240.dp)
          .performTextReplacement("Renamed ${preset.name}")
        action(preset, strings.renameFilter).performClick()
        action(preset, "Renamed ${preset.name}").assertIsDisplayed()
        action(preset, strings.deleteFilter)
          .assertIsDisplayed()
          .assertHeightIsAtLeast(48.dp)
          .performClick()
      }
      runOnIdle {
        val expectedIds = fixture.initialPresets.map(SavedFilterPreset::id)
        assertEquals(expectedIds, fixture.appliedIds)
        assertEquals(expectedIds, fixture.renamedIds)
        assertEquals(expectedIds, fixture.deletedIds)
        assertEquals(emptyList(), fixture.presets.value)
      }
    }

  private fun ComposeUiTest.showContent(fixture: Fixture, strings: SharedAppStrings) {
    setContent {
      BillionBeersTheme(darkTheme = false) {
        Surface(Modifier.fillMaxSize()) {
          SavedFiltersDestination(
            entry = fixture.entry,
            strings = strings,
            onApply = { fixture.appliedIds += it.id },
            onRename = { preset, name ->
              fixture.renamedIds += preset.id
              if (fixture.renameSucceeds) {
                fixture.presets.value =
                  fixture.presets.value.map { if (it.id == preset.id) it.copy(name = name) else it }
              }
              fixture.renameSucceeds
            },
            onDelete = { preset ->
              fixture.deletedIds += preset.id
              fixture.presets.value = fixture.presets.value.filterNot { it.id == preset.id }
              true
            },
          )
        }
      }
    }
  }

  private fun ComposeUiTest.scrollTo(fixture: Fixture, preset: SavedFilterPreset) {
    var index = -1
    runOnIdle { index = fixture.presets.value.indexOfFirst { it.id == preset.id } }
    onNode(hasScrollToIndexAction()).performScrollToIndex(index)
    onNodeWithTag("saved-filter-${preset.id}").performScrollTo()
  }

  private fun ComposeUiTest.action(
    preset: SavedFilterPreset,
    text: String,
  ): SemanticsNodeInteraction =
    onNode(hasText(text) and hasAnyAncestor(hasTestTag("saved-filter-${preset.id}")))

  private fun ComposeUiTest.editor(preset: SavedFilterPreset): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("saved-filter-${preset.id}")))

  private fun ComposeUiTest.capture(name: String) {
    val output = File("build/reports/compose-ui/saved-filters-$name.png")
    output.parentFile.mkdirs()
    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output)
  }

  private class Fixture(val renameSucceeds: Boolean = true) {
    val initialPresets =
      List(SavedFilterPreset.MAX_COUNT) { index ->
        SavedFilterPreset(
          "preset-$index",
          "Favorite IPA ${index + 1}",
          BeersQuery(),
          index.toLong(),
        )
      }
    val presets = MutableStateFlow(initialPresets)
    private val repository =
      object : BeersRepository by FakeBeersRepository() {
        override fun observeSavedFilterPresets() = presets
      }
    val entry = SavedFiltersEntry(id = 1L, repository = repository)
    val appliedIds = mutableListOf<String>()
    val renamedIds = mutableListOf<String>()
    val deletedIds = mutableListOf<String>()
  }
}

// Only the saved-filter labels are rendered by this isolated destination fixture.
internal fun savedFilterStrings(rename: String, delete: String) =
  SharedAppStrings(
    appTitle = "Billion Beers",
    back = "",
    list = "List",
    favorites = "",
    search = "Search",
    browse = "",
    savedFilters = "Saved filters",
    savedFiltersEmpty = "No saved filters",
    saveFilter = "Save filter",
    filterPresetLimitReached = "Ten filters already saved",
    saveFilterFailed = "Could not save filter",
    mutateFilterFailed = "Could not update saved filters",
    filterNameHint = "Filter name",
    renameFilter = rename,
    deleteFilter = delete,
    retry = "",
    error = "",
    listLoadMoreFailed = "",
    listEndOfList = { "" },
    searchHint = "",
    styleFilter = "",
    allStyles = "",
    clearStyle = "",
    clearFilters = "",
    searchPrompt = "",
    searchNoResults = { "" },
    searchResultCount = { "" },
    searchEndOfList = { "" },
    favoritesEmpty = "",
    browseStrings =
      BrowseStrings(
        back = "",
        title = "",
        stylesTab = "",
        breweriesTab = "",
        emptyState = "",
        noBeers = "",
        retry = "",
        loadMoreFailed = "",
        breweryFounded = { _, _ -> "" },
        beersCount = { "" },
        endOfList = { "" },
      ),
    detailStrings =
      BeerDetailStrings(
        back = "",
        imageDescription = { "" },
        addToFavorites = "",
        removeFromFavorites = "",
        available = "",
        outOfStock = "",
        markAsEmpty = "",
        refillBarrels = "",
        styleAndBrewery = { _, _ -> "" },
        description = "",
        foodPairing = "",
        abv = "",
        ibu = "",
        details = "",
        srm = "",
        released = "",
        servingTemperature = "",
        servingTemperatureValue = { _, _ -> "" },
        fermentation = "",
        ingredients = "",
        recommendedGlasses = "",
      ),
  )
