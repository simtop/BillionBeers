package com.simtop.billionbeers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.beerdomain.domain.errors.MutateFilterPresetError
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.billionbeers.di.BaseAppGraph
import com.simtop.billionbeers.di.FakeBeersRepositoryModule
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.utils.useWithFailureScreenshot
import com.simtop.feature.beersearch.SEARCH_FIELD_TAG
import com.simtop.feature.beerslist.R as CatalogR
import com.simtop.feature.savedfilters.R as SavedFiltersR
import com.simtop.presentation_utils.R as PresentationUtilsR
import com.simtop.testing_utils_android.withFailureScreenshot
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SavedFiltersMutationUiTest {
  private val composeTestRule = createEmptyComposeRule()
  @get:Rule val failureScreenshots = withFailureScreenshot(composeTestRule)

  private val preset =
    SavedFilterPreset("saved-ipa", "Favorite IPA", BeersQuery(search = "ipa"), 1L)
  private val failureMessage by lazy {
    InstrumentationRegistry.getInstrumentation()
      .targetContext
      .getString(SavedFiltersR.string.savedfilters_mutation_failed)
  }
  private val renameLabel by lazy {
    InstrumentationRegistry.getInstrumentation()
      .targetContext
      .getString(SavedFiltersR.string.savedfilters_rename)
  }
  private val deleteLabel by lazy {
    InstrumentationRegistry.getInstrumentation()
      .targetContext
      .getString(SavedFiltersR.string.savedfilters_delete)
  }
  private val saveLabel by lazy {
    InstrumentationRegistry.getInstrumentation()
      .targetContext
      .getString(PresentationUtilsR.string.save_filter)
  }
  private val capacityMessage by lazy {
    InstrumentationRegistry.getInstrumentation()
      .targetContext
      .getString(R.string.filter_preset_limit_reached)
  }

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository.mutateError = null
    FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository.mutateException = null
    runBlocking {
      FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository
        .observeSavedFilterPresets()
        .first()
        .forEach {
          FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository.deleteFilterPreset(it.id)
        }
      FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository.saveFilterPreset(preset)
    }
    val graph = createGraphFactory<TestAppGraph.Factory>().create(context = context) as BaseAppGraph
    (graph.splitInstallManager as com.simtop.billionbeers.fakes.FakeSplitInstallManager).reset()
    (context as BillionBeersApplication).activateAppGraph(graph)
  }

  @Test
  fun capacitySaveShowsFeedbackKeepsNameAndSucceedsWhenCapacityOpens() {
    val fullPresetList =
      listOf(preset) +
        List(SavedFilterPreset.MAX_COUNT - 1) { index ->
          SavedFilterPreset("capacity-$index", "Capacity $index", BeersQuery(), index.toLong())
        }
    runBlocking {
      fullPresetList.drop(1).forEach {
        FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository.saveFilterPreset(it)
      }
    }
    val expectedIds = fullPresetList.map(SavedFilterPreset::id).toSet()

    ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot {
      val name = "Recovered filter"
      composeTestRule.onNodeWithText(saveLabel).performClick()
      composeTestRule.onNode(hasSetTextAction()).performTextReplacement(name)
      composeTestRule.onNodeWithTag("filter_preset_save").performClick()

      composeTestRule.onNodeWithText(capacityMessage).assertIsDisplayed()
      composeTestRule.onNode(hasText(name)).assertIsDisplayed()
      assertEquals(expectedIds, persistedPresets().map(SavedFilterPreset::id).toSet())

      runBlocking {
        FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository.deleteFilterPreset(
          fullPresetList.first().id
        )
      }
      composeTestRule.waitUntil(timeoutMillis = 6_000) {
        composeTestRule.onAllNodesWithText(capacityMessage).fetchSemanticsNodes().isEmpty()
      }
      composeTestRule.onNodeWithTag("filter_preset_save").performClick()

      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        persistedPresets().any { it.name == name }
      }
      val recovered = persistedPresets()
      assertEquals(SavedFilterPreset.MAX_COUNT, recovered.size)
      assertEquals(name, recovered.first { it.name == name }.name)
    }
  }

  @Test
  fun catalogInlineSearchSavesTheTypedCriteriaAndKeepsTheSavedPreset() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot {
      composeTestRule
        .onNodeWithContentDescription(context.getString(CatalogR.string.beers_search))
        .performClick()
      composeTestRule.onNodeWithTag(SEARCH_FIELD_TAG).performTextReplacement("  pale ale  ")
      composeTestRule.onNodeWithText(saveLabel).performClick()
      composeTestRule
        .onNode(hasSetTextAction() and !hasTestTag(SEARCH_FIELD_TAG))
        .performTextReplacement("Pale ales")
      composeTestRule.onNodeWithTag("filter_preset_save").performClick()
      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        persistedPresets().any { it.name == "Pale ales" }
      }
      assertEquals(
        BeersQuery(search = "pale ale"),
        persistedPresets().single { it.name == "Pale ales" }.query,
      )
      composeTestRule.onNodeWithTag("filter_preset_save").assertDoesNotExist()
      composeTestRule
        .onNodeWithContentDescription(context.getString(CatalogR.string.beers_close_search))
        .performClick()
      composeTestRule.onNodeWithTag(SEARCH_FIELD_TAG).assertDoesNotExist()
      assertTrue(persistedPresets().any { it.name == "Pale ales" })
    }
  }

  @Test
  fun failedRenameShowsFeedbackRetainsDraftAndPresetThenRetryPersistsRename() {
    FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository.mutateError = storageFailure()

    ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot {
      openSavedFilters()
      val draft = "Renamed IPA"
      rowAction(preset, renameLabel).performClick()
      editor(preset).performTextReplacement(draft)
      rowAction(preset, renameLabel).performClick()

      composeTestRule.onNodeWithText(failureMessage).assertIsDisplayed()
      editor(preset).assertIsDisplayed()
      composeTestRule.onNodeWithText(draft).assertIsDisplayed()
      assertEquals(listOf(preset), persistedPresets())

      FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository.mutateError = null
      rowAction(preset, renameLabel).performClick()

      composeTestRule.onNodeWithText(draft).assertIsDisplayed()
      val persistedRename = persistedPresets().single()
      assertEquals(preset.id, persistedRename.id)
      assertEquals(draft, persistedRename.name)
      assertEquals(preset.query, persistedRename.query)
    }
  }

  @Test
  fun renameEditorRemainsSingleLineWhenTextContainsNewline() {
    ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot {
      openSavedFilters()
      rowAction(preset, renameLabel).performClick()
      editor(preset).performTextReplacement("First line\nSecond line")

      val editorHeight = editor(preset).fetchSemanticsNode().size.height
      assertTrue(
        "single-line editor height was $editorHeight",
        editorHeight <= with(composeTestRule.density) { 64.dp.toPx() },
      )
    }
  }

  @Test
  fun failedDeleteShowsFeedbackAndKeepsPresetThenRetryRemovesIt() {
    FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository.mutateError = storageFailure()

    ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot {
      openSavedFilters()
      rowAction(preset, deleteLabel).performClick()

      composeTestRule.onNodeWithText(failureMessage).assertIsDisplayed()
      composeTestRule.onNodeWithTag("saved-filter-${preset.id}").assertIsDisplayed()
      assertEquals(listOf(preset), persistedPresets())

      FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository.mutateError = null
      rowAction(preset, deleteLabel).performClick()

      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        runBlocking {
          FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository
            .observeSavedFilterPresets()
            .first()
        }
          .isEmpty()
      }
      composeTestRule
        .onNodeWithText(
          InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(SavedFiltersR.string.savedfilters_empty)
        )
        .assertIsDisplayed()
      assertEquals(emptyList<SavedFilterPreset>(), persistedPresets())
    }
  }

  private fun openSavedFilters() {
    composeTestRule.onNodeWithTag("saved_filters_tab").performClick()
    composeTestRule.onNodeWithText(preset.name).assertIsDisplayed()
  }

  private fun rowAction(preset: SavedFilterPreset, text: String) =
    composeTestRule.onNode(
      hasText(text) and hasAnyAncestor(hasTestTag("saved-filter-${preset.id}"))
    )

  private fun editor(preset: SavedFilterPreset) =
    composeTestRule.onNode(
      hasSetTextAction() and hasAnyAncestor(hasTestTag("saved-filter-${preset.id}"))
    )

  private fun persistedPresets(): List<SavedFilterPreset> = runBlocking {
    FakeBeersRepositoryModule.fakeSavedFilterPresetsRepository.observeSavedFilterPresets().first()
  }

  private fun storageFailure() =
    MutateFilterPresetError.Unknown(IllegalStateException("simulated storage failure"))
}
