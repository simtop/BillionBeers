package com.simtop.feature.beerdetail

import androidx.lifecycle.SavedStateHandle
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.beerdomain.fakes.fakeBeerModel
import com.simtop.billionbeers.testing_utils.MainDispatcherExtension
import com.simtop.core.core.CommonUiState
import com.simtop.feature.beerdetail.presentation.BeerDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@OptIn(ExperimentalCoroutinesApi::class)
internal class BeerDetailViewModelAdapterTest {
  @JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()

  @Test
  fun `restored beer takes precedence over navigation beer`() =
    runTest(mainDispatcher.testDispatcher) {
      val restoredBeer = fakeBeerModel.copy(isFavorite = true)
      val savedStateHandle = SavedStateHandle()
      val firstViewModel =
        BeerDetailViewModel(FakeBeersRepository(), fakeBeerModel, savedStateHandle)
      firstViewModel.updateFavorite(fakeBeerModel)
      advanceUntilIdle()
      expectThat((firstViewModel.beerDetailViewState.value as CommonUiState.Success).data)
        .isEqualTo(restoredBeer)

      val recreated =
        BeerDetailViewModel(
          FakeBeersRepository(),
          fakeBeerModel.copy(isFavorite = false),
          SavedStateHandle.createHandle(savedStateHandle.savedStateProvider().saveState(), null),
        )

      expectThat((recreated.beerDetailViewState.value as CommonUiState.Success).data)
        .isEqualTo(restoredBeer)
    }

  @Test
  fun `optimistic update persists latest beer in saved state`() =
    runTest(mainDispatcher.testDispatcher) {
      val savedStateHandle = SavedStateHandle()
      val viewModel = BeerDetailViewModel(FakeBeersRepository(), fakeBeerModel, savedStateHandle)

      viewModel.updateAvailability(fakeBeerModel)
      advanceUntilIdle()

      val recreated =
        BeerDetailViewModel(
          FakeBeersRepository(),
          fakeBeerModel,
          SavedStateHandle.createHandle(savedStateHandle.savedStateProvider().saveState(), null),
        )
      expectThat((recreated.beerDetailViewState.value as CommonUiState.Success).data.availability)
        .isEqualTo(!fakeBeerModel.availability)
    }
}
