package com.simtop.billionbeers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.beerdomain.domain.errors.MutateFilterPresetError
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.billionbeers.di.BaseAppGraph
import com.simtop.billionbeers.di.FakeBeersRepositoryModule
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.feature.savedfilters.R as SavedFiltersR
import com.simtop.presentation_utils.R as PresentationUtilsR
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SavedFiltersMutationUiTest {
  @get:Rule val composeTestRule = createEmptyComposeRule()

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
    FakeBeersRepositoryModule.fakeBeersRepository.mutateFilterPresetError = null
    FakeBeersRepositoryModule.fakeBeersRepository.mutateFilterPresetException = null
    runBlocking {
      FakeBeersRepositoryModule.fakeBeersRepository.observeSavedFilterPresets().first().forEach {
        FakeBeersRepositoryModule.fakeBeersRepository.deleteFilterPreset(it.id)
      }
      FakeBeersRepositoryModule.fakeBeersRepository.saveFilterPreset(preset)
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
        FakeBeersRepositoryModule.fakeBeersRepository.saveFilterPreset(it)
      }
    }
    val expectedIds = fullPresetList.map(SavedFilterPreset::id).toSet()

    ActivityScenario.launch(MainActivity::class.java).use {
      val name = "Recovered filter"
      composeTestRule.onNode(hasSetTextAction()).performTextReplacement(name)
      composeTestRule.onNodeWithText(saveLabel).performClick()

      composeTestRule.onNodeWithText(capacityMessage).assertIsDisplayed()
      composeTestRule.onNode(hasText(name)).assertIsDisplayed()
      assertEquals(expectedIds, persistedPresets().map(SavedFilterPreset::id).toSet())

      runBlocking {
        FakeBeersRepositoryModule.fakeBeersRepository.deleteFilterPreset(fullPresetList.first().id)
      }
      composeTestRule.waitUntil(timeoutMillis = 6_000) {
        composeTestRule.onAllNodesWithText(capacityMessage).fetchSemanticsNodes().isEmpty()
      }
      composeTestRule.onNodeWithText(saveLabel).performClick()

      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        persistedPresets().any { it.name == name }
      }
      val recovered = persistedPresets()
      assertEquals(SavedFilterPreset.MAX_COUNT, recovered.size)
      assertEquals(name, recovered.first { it.name == name }.name)
    }
  }

  @Test
  fun failedRenameShowsFeedbackRetainsDraftAndPresetThenRetryPersistsRename() {
    FakeBeersRepositoryModule.fakeBeersRepository.mutateFilterPresetError = storageFailure()

    ActivityScenario.launch(MainActivity::class.java).use {
      openSavedFilters()
      val draft = "Renamed IPA"
      rowAction(preset, renameLabel).performClick()
      editor(preset).performTextReplacement(draft)
      rowAction(preset, renameLabel).performClick()

      composeTestRule.onNodeWithText(failureMessage).assertIsDisplayed()
      editor(preset).assertIsDisplayed()
      composeTestRule.onNodeWithText(draft).assertIsDisplayed()
      assertEquals(listOf(preset), persistedPresets())

      FakeBeersRepositoryModule.fakeBeersRepository.mutateFilterPresetError = null
      rowAction(preset, renameLabel).performClick()

      composeTestRule.onNodeWithText(draft).assertIsDisplayed()
      val persistedRename = persistedPresets().single()
      assertEquals(preset.id, persistedRename.id)
      assertEquals(draft, persistedRename.name)
      assertEquals(preset.query, persistedRename.query)
    }
  }

  @Test
  fun failedDeleteShowsFeedbackAndKeepsPresetThenRetryRemovesIt() {
    FakeBeersRepositoryModule.fakeBeersRepository.mutateFilterPresetError = storageFailure()

    ActivityScenario.launch(MainActivity::class.java).use {
      openSavedFilters()
      rowAction(preset, deleteLabel).performClick()

      composeTestRule.onNodeWithText(failureMessage).assertIsDisplayed()
      composeTestRule.onNodeWithTag("saved-filter-${preset.id}").assertIsDisplayed()
      assertEquals(listOf(preset), persistedPresets())

      FakeBeersRepositoryModule.fakeBeersRepository.mutateFilterPresetError = null
      rowAction(preset, deleteLabel).performClick()

      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        runBlocking {
          FakeBeersRepositoryModule.fakeBeersRepository.observeSavedFilterPresets().first()
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
    FakeBeersRepositoryModule.fakeBeersRepository.observeSavedFilterPresets().first()
  }

  private fun storageFailure() =
    MutateFilterPresetError.Unknown(IllegalStateException("simulated storage failure"))
}
