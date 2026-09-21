package com.simtop.feature.beersearch

import androidx.lifecycle.SavedStateHandle
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.billionbeers.testing_utils.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@OptIn(ExperimentalCoroutinesApi::class)
class BeersSearchViewModelAdapterTest {

  @JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()

  @Test
  fun `restored query is exposed and query changes are saved`() =
    runTest(mainDispatcher.testDispatcher) {
      val savedStateHandle = SavedStateHandle(mapOf("search_query" to "ipa"))
      val viewModel =
        BeersSearchViewModel(
          coroutineDispatcher = mainDispatcher.dispatcherProvider,
          beersPagerFactory = FakeBeersPagerFactory(FakeBeersRepository()),
          savedStateHandle = savedStateHandle,
        )

      expectThat(viewModel.query.value).isEqualTo("ipa")

      viewModel.onQueryChange("stout")
      runCurrent()

      expectThat(savedStateHandle.get<String>("search_query")).isEqualTo("stout")
    }
}
