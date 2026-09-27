package com.simtop.feature.savedfilters

import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.billionbeers.testing_utils.MainDispatcherExtension
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
}
