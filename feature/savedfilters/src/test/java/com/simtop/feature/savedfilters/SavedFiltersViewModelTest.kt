package com.simtop.feature.savedfilters

import com.simtop.beerdomain.domain.errors.MutateFilterPresetError
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.fakes.FakeSavedFilterPresetsRepository
import com.simtop.billionbeers.testing_utils.MainDispatcherExtension
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.FLOW_RETRY_DELAY_MILLIS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

@OptIn(ExperimentalCoroutinesApi::class)
class SavedFiltersViewModelTest {

  @JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()

  @Test
  fun `observes presets and delegates rename and delete`() =
    runTest(mainDispatcher.testDispatcher) {
      val repository = FakeSavedFilterPresetsRepository()
      val viewModel = SavedFiltersViewModel(repository)
      backgroundScope.launch { viewModel.presets.collect() }
      val preset = SavedFilterPreset("ipa", "IPA", BeersQuery(search = "ipa"), 1L)

      repository.saveFilterPreset(preset)
      runCurrent()
      expectThat(presets(viewModel)).containsExactly(preset)

      viewModel.rename(preset, " India Pale Ale ")
      runCurrent()
      expectThat(presets(viewModel).single().name).isEqualTo("India Pale Ale")

      viewModel.delete(preset)
      runCurrent()
      expectThat(viewModel.presets.value).isEqualTo(CommonUiState.Empty)
    }

  @Test
  fun `ignores invalid names`() =
    runTest(mainDispatcher.testDispatcher) {
      val repository = FakeSavedFilterPresetsRepository()
      val viewModel = SavedFiltersViewModel(repository)
      val reportedFailures = mutableListOf<Unit>()
      backgroundScope.launch { viewModel.presets.collect() }
      backgroundScope.launch { viewModel.mutationFailed.collect { reportedFailures += it } }
      val preset = SavedFilterPreset("ipa", "IPA", BeersQuery(search = "ipa"), 1L)
      repository.saveFilterPreset(preset)
      runCurrent()

      listOf(" ", "x".repeat(SavedFilterPreset.MAX_NAME_LENGTH + 1)).forEach { name ->
        expectThat(viewModel.rename(preset, name)).isEqualTo(false)
      }
      runCurrent()

      expectThat(presets(viewModel).single().name).isEqualTo("IPA")
      expectThat(repository.renameRequests).isEmpty()
      expectThat(reportedFailures).isEmpty()
    }

  @Test
  fun `failed mutations preserve presets and later mutations recover`() =
    runTest(mainDispatcher.testDispatcher) {
      val repository = FakeSavedFilterPresetsRepository()
      val viewModel = SavedFiltersViewModel(repository)
      val reportedFailures = mutableListOf<Unit>()
      backgroundScope.launch { viewModel.presets.collect() }
      backgroundScope.launch { viewModel.mutationFailed.collect { reportedFailures += it } }
      val preset = SavedFilterPreset("ipa", "IPA", BeersQuery(), 1L)
      repository.saveFilterPreset(preset)
      runCurrent()
      repository.mutateError = MutateFilterPresetError.Unknown(IllegalStateException("storage"))

      expectThat(viewModel.rename(preset, "Renamed")).isEqualTo(false)
      expectThat(presets(viewModel)).containsExactly(preset)
      expectThat(viewModel.delete(preset)).isEqualTo(false)
      expectThat(presets(viewModel)).containsExactly(preset)
      runCurrent()
      expectThat(reportedFailures.size).isEqualTo(2)

      repository.mutateError = null
      repository.mutateException = CancellationException("cancel")
      var renameCancelled = false
      var deleteCancelled = false
      try {
        viewModel.rename(preset, "Never committed")
      } catch (_: CancellationException) {
        renameCancelled = true
      }
      try {
        viewModel.delete(preset)
      } catch (_: CancellationException) {
        deleteCancelled = true
      }
      expectThat(renameCancelled).isEqualTo(true)
      expectThat(deleteCancelled).isEqualTo(true)
      expectThat(presets(viewModel)).containsExactly(preset)

      repository.mutateException = null
      expectThat(viewModel.rename(preset, "Renamed")).isEqualTo(true)
      runCurrent()
      expectThat(presets(viewModel).single().name).isEqualTo("Renamed")
      expectThat(viewModel.delete(presets(viewModel).single())).isEqualTo(true)
      runCurrent()
      expectThat(viewModel.presets.value).isEqualTo(CommonUiState.Empty)
      expectThat(reportedFailures.size).isEqualTo(2)
    }

  @Test
  fun `preset observation emits error and recovers after retry`() =
    runTest(mainDispatcher.testDispatcher) {
      var subscriptions = 0
      val preset = SavedFilterPreset("ipa", "IPA", BeersQuery(), 1L)
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
      val viewModel = SavedFiltersViewModel(repository)
      backgroundScope.launch { viewModel.presets.collect() }
      runCurrent()
      expectThat(viewModel.presets.value).isEqualTo(CommonUiState.Error())

      advanceTimeBy(FLOW_RETRY_DELAY_MILLIS)
      runCurrent()

      expectThat(presets(viewModel)).containsExactly(preset)
      expectThat(subscriptions).isEqualTo(2)
    }

  private fun presets(viewModel: SavedFiltersViewModel): List<SavedFilterPreset> =
    (viewModel.presets.value as CommonUiState.Success<List<SavedFilterPreset>>).data
}
