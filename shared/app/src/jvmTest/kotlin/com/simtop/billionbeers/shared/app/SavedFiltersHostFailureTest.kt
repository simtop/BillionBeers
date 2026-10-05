package com.simtop.billionbeers.shared.app

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.simtop.beerdomain.domain.errors.MutateFilterPresetError
import com.simtop.beerdomain.domain.errors.SaveFilterPresetError
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import com.simtop.core.core.Either
import com.simtop.navigation.contract.PortableRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalTestApi::class)
class SavedFiltersHostFailureTest {
  @Test
  fun capacityFailureKeepsNameAndRetriesAfterCapacityBecomesAvailable() = runSkikoComposeUiTest {
    val fixture = Fixture(presetCount = SavedFilterPreset.MAX_COUNT)
    showShell(fixture, PortableRoute.BeersList)

    onNode(hasSetTextAction()).performTextReplacement("New saved filter")
    onNodeWithText("Save filter").performClick()

    onNodeWithTag(HOST_MESSAGE_TAG).assertIsDisplayed()
    onNodeWithText("Ten filters already saved").assertIsDisplayed()
    onNode(hasText("New saved filter")).assertIsDisplayed()
    runOnIdle {
      assertEquals(SavedFilterPreset.MAX_COUNT, fixture.presets.value.size)
      assertEquals(0, fixture.repository.savedPresetWrites)
      // Model a slot becoming available while the user keeps the list entry open.
      fixture.presets.value = fixture.presets.value.dropLast(1)
    }

    onNodeWithText("Save filter").performClick()

    runOnIdle {
      assertEquals(SavedFilterPreset.MAX_COUNT, fixture.presets.value.size)
      assertTrue(fixture.presets.value.any { it.name == "New saved filter" })
      assertEquals(1, fixture.repository.savedPresetWrites)
    }
  }

  @Test
  fun mutationFailureKeepsDraftAndPresetUntilRenameAndDeleteRecover() = runSkikoComposeUiTest {
    val fixture = Fixture(presetCount = 1)
    val preset = fixture.presets.value.single()
    showShell(fixture, PortableRoute.SavedFilterPresets)

    action(preset, "Rename").performClick()
    editor(preset).performTextReplacement("Recovered name")
    action(preset, "Rename").performClick()

    onNodeWithTag(HOST_MESSAGE_TAG).assertIsDisplayed()
    onNodeWithText("Could not update saved filters").assertIsDisplayed()
    action(preset, "Recovered name").assertIsDisplayed()
    runOnIdle {
      assertEquals(preset, fixture.presets.value.single())
      fixture.repository.mutationFails = false
    }

    action(preset, "Rename").performClick()
    onNode(hasText("Recovered name")).assertIsDisplayed()
    runOnIdle { assertEquals("Recovered name", fixture.presets.value.single().name) }

    val renamed = fixture.presets.value.single()
    fixture.repository.mutationFails = true
    action(renamed, "Delete").performClick()
    onNodeWithText("Could not update saved filters").assertIsDisplayed()
    runOnIdle {
      assertEquals(listOf(renamed), fixture.presets.value)
      fixture.repository.mutationFails = false
    }

    action(renamed, "Delete").performClick()
    onNodeWithText("No saved filters").assertIsDisplayed()
    runOnIdle { assertEquals(emptyList(), fixture.presets.value) }
  }

  private fun androidx.compose.ui.test.ComposeUiTest.showShell(
    fixture: Fixture,
    initialRoute: PortableRoute,
  ) {
    setContent {
      SharedAppShell(
        repository = fixture.repository,
        pagerFactory = FakeBeersPagerFactory(fixture.backingRepository),
        coroutineDispatcher = DefaultCoroutineDispatcherProvider(),
        strings = savedFilterStrings("Rename", "Delete"),
        host =
          SharedAppHost(
            beerRow = { _, _ -> },
            errorContent = { _, _ -> Text("Load error") },
            backIcon = {},
            favoriteIcon = { _, _ -> },
            imageContent = { _, _, _ -> },
            messageContent = { message ->
              Text(message, modifier = Modifier.testTag(HOST_MESSAGE_TAG))
            },
          ),
        initialRoute = initialRoute,
      )
    }
  }

  private fun androidx.compose.ui.test.ComposeUiTest.action(
    preset: SavedFilterPreset,
    text: String,
  ): SemanticsNodeInteraction =
    onNode(hasText(text) and hasAnyAncestor(hasTestTag("saved-filter-${preset.id}")))

  private fun androidx.compose.ui.test.ComposeUiTest.editor(
    preset: SavedFilterPreset
  ): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("saved-filter-${preset.id}")))

  private class Fixture(presetCount: Int) {
    val backingRepository = FakeBeersRepository()
    val presets =
      MutableStateFlow(
        List(presetCount) { index ->
          SavedFilterPreset(
            "saved-$index",
            "Saved filter ${index + 1}",
            BeersQuery(),
            index.toLong(),
          )
        }
      )
    val repository = ShellFailureRepository(backingRepository, presets)
  }

  private class ShellFailureRepository(
    backingRepository: FakeBeersRepository,
    private val presets: MutableStateFlow<List<SavedFilterPreset>>,
  ) : BeersRepository by backingRepository {
    var mutationFails = true
    var savedPresetWrites = 0

    override fun observeSavedFilterPresets(): Flow<List<SavedFilterPreset>> = presets

    override suspend fun saveFilterPreset(
      preset: SavedFilterPreset
    ): Either<SaveFilterPresetError, Unit> {
      if (
        presets.value.none { it.id == preset.id } &&
          presets.value.size >= SavedFilterPreset.MAX_COUNT
      ) {
        return Either.Left(SaveFilterPresetError.CapacityReached)
      }
      presets.value = presets.value.filterNot { it.id == preset.id } + preset
      savedPresetWrites += 1
      return Either.Right(Unit)
    }

    override suspend fun renameFilterPreset(
      id: String,
      name: String,
      updatedAt: Long,
    ): Either<MutateFilterPresetError, Unit> {
      if (mutationFails) {
        return Either.Left(MutateFilterPresetError.Unknown(IllegalStateException("storage")))
      }
      presets.value = presets.value.map { if (it.id == id) it.copy(name = name) else it }
      return Either.Right(Unit)
    }

    override suspend fun deleteFilterPreset(id: String): Either<MutateFilterPresetError, Unit> {
      if (mutationFails) {
        return Either.Left(MutateFilterPresetError.Unknown(IllegalStateException("storage")))
      }
      presets.value = presets.value.filterNot { it.id == id }
      return Either.Right(Unit)
    }
  }

  private companion object {
    const val HOST_MESSAGE_TAG = "saved-filter-host-message"
  }
}
