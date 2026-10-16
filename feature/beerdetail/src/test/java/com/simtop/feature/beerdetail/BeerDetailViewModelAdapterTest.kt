package com.simtop.feature.beerdetail

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
  fun `loads beer by route ID and reflects repository state after recreation`() =
    runTest(mainDispatcher.testDispatcher) {
      val restoredBeer = fakeBeerModel.copy(isFavorite = true)
      val repository = FakeBeersRepository(listOf(fakeBeerModel))
      val firstViewModel = BeerDetailViewModel(repository, fakeBeerModel.id)
      advanceUntilIdle()
      firstViewModel.updateFavorite(fakeBeerModel)
      advanceUntilIdle()
      expectThat((firstViewModel.beerDetailViewState.value as CommonUiState.Success).data)
        .isEqualTo(restoredBeer)

      val recreated = BeerDetailViewModel(repository, fakeBeerModel.id)
      advanceUntilIdle()

      expectThat((recreated.beerDetailViewState.value as CommonUiState.Success).data)
        .isEqualTo(restoredBeer)
    }

  @Test
  fun `optimistic update persists latest beer in repository`() =
    runTest(mainDispatcher.testDispatcher) {
      val repository = FakeBeersRepository(listOf(fakeBeerModel))
      val viewModel = BeerDetailViewModel(repository, fakeBeerModel.id)

      viewModel.updateAvailability(fakeBeerModel)
      advanceUntilIdle()

      val recreated = BeerDetailViewModel(repository, fakeBeerModel.id)
      advanceUntilIdle()
      expectThat((recreated.beerDetailViewState.value as CommonUiState.Success).data.availability)
        .isEqualTo(!fakeBeerModel.availability)
    }
}
