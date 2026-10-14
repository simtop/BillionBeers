package com.simtop.billionbeers.shared.savedfilters

import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.fakes.FakeSavedFilterPresetsRepository
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.Either
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SavedFiltersModelTest {
  @Test
  fun `presets reflect repository changes and rename and delete delegate to repository`() =
    runTest {
      val repository = FakeSavedFilterPresetsRepository()
      val model = SavedFiltersModel(repository)
      val preset = SavedFilterPreset("preset-1", "IPA", BeersQuery(search = "ipa"), 1L)
      assertIs<Either.Right<Unit>>(repository.saveFilterPreset(preset))

      assertEquals(
        listOf(preset),
        assertIs<CommonUiState.Success<List<SavedFilterPreset>>>(model.presets.first()).data,
      )
      assertIs<Either.Right<Unit>>(model.rename(preset.id, " Pale Ale "))
      assertEquals(
        "Pale Ale",
        assertIs<CommonUiState.Success<List<SavedFilterPreset>>>(model.presets.first())
          .data
          .single()
          .name,
      )
      assertIs<Either.Right<Unit>>(model.delete(preset.id))
      assertEquals(CommonUiState.Empty, model.presets.first())
    }

  @Test
  fun `preset observation shows error and recovers after retry`() = runTest {
    var subscriptions = 0
    val preset = SavedFilterPreset("preset-1", "IPA", BeersQuery(search = "ipa"), 1L)
    val repository =
      FakeSavedFilterPresetsRepository().apply {
        setObservationFlow(
          flow {
            if (subscriptions++ == 0) error("database unavailable")
            emit(listOf(preset))
            awaitCancellation()
          }
        )
      }
    val model = SavedFiltersModel(repository)

    val emissions = model.presets.take(2).toList()

    assertIs<CommonUiState.Error>(emissions.first())
    assertEquals(
      listOf(preset),
      assertIs<CommonUiState.Success<List<SavedFilterPreset>>>(emissions.last()).data,
    )
    assertEquals(2, subscriptions)
  }
}
