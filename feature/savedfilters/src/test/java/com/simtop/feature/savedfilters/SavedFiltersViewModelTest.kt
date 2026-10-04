package com.simtop.feature.savedfilters

import com.simtop.beerdomain.domain.errors.MutateFilterPresetError
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.billionbeers.testing_utils.MainDispatcherExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo

@OptIn(ExperimentalCoroutinesApi::class)
class SavedFiltersViewModelTest {

  @JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()

  @Test
  fun `observes presets and delegates rename and delete`() =
    runTest(mainDispatcher.testDispatcher) {
      val repository = FakeBeersRepository()
      val viewModel = SavedFiltersViewModel(repository)
      backgroundScope.launch { viewModel.presets.collect() }
      val preset = SavedFilterPreset("ipa", "IPA", BeersQuery(search = "ipa"), 1L)

      repository.saveFilterPreset(preset)
      runCurrent()
      expectThat(viewModel.presets.value).containsExactly(preset)

      viewModel.rename(preset, " India Pale Ale ")
      runCurrent()
      expectThat(viewModel.presets.value.single().name).isEqualTo("India Pale Ale")

      viewModel.delete(preset)
      runCurrent()
      expectThat(viewModel.presets.value).isEqualTo(emptyList())
    }

  @Test
  fun `ignores invalid names`() =
    runTest(mainDispatcher.testDispatcher) {
      val repository = FakeBeersRepository()
      val viewModel = SavedFiltersViewModel(repository)
      backgroundScope.launch { viewModel.presets.collect() }
      val preset = SavedFilterPreset("ipa", "IPA", BeersQuery(search = "ipa"), 1L)
      repository.saveFilterPreset(preset)
      runCurrent()

      viewModel.rename(preset, " ")
      runCurrent()
      expectThat(viewModel.presets.value.single().name).isEqualTo("IPA")
    }

  @Test
  fun `failed mutations preserve presets and later mutations recover`() =
    runTest(mainDispatcher.testDispatcher) {
      val repository = FakeBeersRepository()
      val viewModel = SavedFiltersViewModel(repository)
      val reportedFailures = mutableListOf<Unit>()
      backgroundScope.launch { viewModel.presets.collect() }
      backgroundScope.launch { viewModel.mutationFailed.collect { reportedFailures += it } }
      val preset = SavedFilterPreset("ipa", "IPA", BeersQuery(), 1L)
      repository.saveFilterPreset(preset)
      runCurrent()
      repository.mutateFilterPresetError =
        MutateFilterPresetError.Unknown(IllegalStateException("storage"))

      expectThat(viewModel.rename(preset, "Renamed")).isEqualTo(false)
      expectThat(viewModel.presets.value).containsExactly(preset)
      expectThat(viewModel.delete(preset)).isEqualTo(false)
      expectThat(viewModel.presets.value).containsExactly(preset)
      runCurrent()
      expectThat(reportedFailures.size).isEqualTo(2)

      repository.mutateFilterPresetError = null
      repository.mutateFilterPresetException = CancellationException("cancel")
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
      expectThat(viewModel.presets.value).containsExactly(preset)

      repository.mutateFilterPresetException = null
      expectThat(viewModel.rename(preset, "Renamed")).isEqualTo(true)
      runCurrent()
      expectThat(viewModel.presets.value.single().name).isEqualTo("Renamed")
      expectThat(viewModel.delete(viewModel.presets.value.single())).isEqualTo(true)
      runCurrent()
      expectThat(viewModel.presets.value).isEqualTo(emptyList())
      expectThat(reportedFailures.size).isEqualTo(2)
    }
}
