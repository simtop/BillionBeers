package com.simtop.billionbeers.shared.beerdetail

import app.cash.turbine.test
import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.errors.UpdateFavoriteError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.beerdomain.fakes.fakeBeerModel
import com.simtop.beerdomain.fakes.fakeException
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.Either
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

@ExperimentalCoroutinesApi
internal class BeerDetailViewModelTest {

  private val fakeBeersRepository = FakeBeersRepository()
  private val testDispatcher = StandardTestDispatcher()

  @BeforeEach
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @AfterEach
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `when creating viewmodel we get success state`() =
    runTest(testDispatcher) {
      // Arrange & Act
      val beerDetailViewModel =
        BeerDetailViewModel(
          fakeBeersRepository,
          fakeBeerModel,
        )

      // Assert
      beerDetailViewModel.beerDetailViewState.test {
        val item = awaitItem()
        expectThat(item).isA<CommonUiState.Success<Beer>>()
        expectThat((item as CommonUiState.Success).data).isEqualTo(fakeBeerModel)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `when usecase fails we rollback and emit error event`() =
    runTest(testDispatcher) {
      // Arrange
      fakeBeersRepository.setExceptionToThrow(fakeException)

      val beerDetailViewModel =
        BeerDetailViewModel(
          fakeBeersRepository,
          fakeBeerModel,
        )

      // Act
      beerDetailViewModel.events.test {
        beerDetailViewModel.beerDetailViewState.test {
          // Initial success state
          expectThat(awaitItem()).isA<CommonUiState.Success<Beer>>()

          beerDetailViewModel.updateAvailability(fakeBeerModel)
          testDispatcher.scheduler.advanceUntilIdle()

          // Optimistic update
          expectThat(awaitItem()).isA<CommonUiState.Success<Beer>>()

          val rollbackItem = awaitItem()
          expectThat(rollbackItem).isA<CommonUiState.Success<Beer>>()
          expectThat((rollbackItem as CommonUiState.Success).data).isEqualTo(fakeBeerModel)
          cancelAndIgnoreRemainingEvents()
        }

        val event = awaitItem()
        expectThat(event).isA<BeerDetailEvent.ShowError>()
        expectThat((event as BeerDetailEvent.ShowError).error)
          .isEqualTo(BeerDetailError.AvailabilityUpdate)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `when usecase succeeds we get success state with different availability`() =
    runTest(testDispatcher) {
      // Arrange
      fakeBeersRepository.setExceptionToThrow(null)
      fakeBeersRepository.setBeers(listOf(fakeBeerModel))

      val testExpectedResponse = fakeBeerModel.copy(availability = !fakeBeerModel.availability)

      val beerDetailViewModel =
        BeerDetailViewModel(
          fakeBeersRepository,
          fakeBeerModel,
        )

      // Act
      beerDetailViewModel.events.test {
        beerDetailViewModel.beerDetailViewState.test {
          // Initial success state
          expectThat(awaitItem()).isA<CommonUiState.Success<Beer>>()

          beerDetailViewModel.updateAvailability(fakeBeerModel)

          // Optimistic update (toggled availability)
          val updatedItem = awaitItem()
          expectThat(updatedItem).isA<CommonUiState.Success<Beer>>()
          expectThat((updatedItem as CommonUiState.Success).data.availability)
            .isEqualTo(testExpectedResponse.availability)
          cancelAndIgnoreRemainingEvents()
        }

        expectNoEvents()

        val storedBeer = fakeBeersRepository.getBeers().first()
        expectThat(storedBeer.availability).isEqualTo(testExpectedResponse.availability)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `favorite update optimistically changes state and emits success event`() =
    runTest(testDispatcher) {
      val repository = FakeBeersRepository()
      val toggledBeer = fakeBeerModel.copy(isFavorite = !fakeBeerModel.isFavorite)
      val viewModel = BeerDetailViewModel(repository, fakeBeerModel)

      viewModel.events.test {
        viewModel.beerDetailViewState.test {
          expectThat(awaitItem()).isA<CommonUiState.Success<Beer>>()
          viewModel.updateFavorite(fakeBeerModel)
          val updated = awaitItem()
          expectThat((updated as CommonUiState.Success).data).isEqualTo(toggledBeer)
          cancelAndIgnoreRemainingEvents()
        }

        expectThat(awaitItem()).isEqualTo(BeerDetailEvent.FavoriteUpdated)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `failed favorite update rolls back and emits error event`() =
    runTest(testDispatcher) {
      val repository = mockk<BeersRepository>()
      val toggledBeer = fakeBeerModel.copy(isFavorite = !fakeBeerModel.isFavorite)
      coEvery { repository.updateFavorite(toggledBeer) } returns
        Either.Left(UpdateFavoriteError.Unknown(fakeException))
      val viewModel = BeerDetailViewModel(repository, fakeBeerModel)

      viewModel.events.test {
        viewModel.beerDetailViewState.test {
          expectThat(awaitItem()).isA<CommonUiState.Success<Beer>>()
          viewModel.updateFavorite(fakeBeerModel)
          expectThat((awaitItem() as CommonUiState.Success).data).isEqualTo(toggledBeer)
          expectThat((awaitItem() as CommonUiState.Success).data).isEqualTo(fakeBeerModel)
          cancelAndIgnoreRemainingEvents()
        }

        expectThat(awaitItem()).isEqualTo(BeerDetailEvent.ShowError(BeerDetailError.FavoriteUpdate))
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `overlapping favorite updates are serialized`() =
    runTest(testDispatcher) {
      val firstStarted = CompletableDeferred<Unit>()
      val releaseFirst = CompletableDeferred<Unit>()
      val repository = mockk<BeersRepository>()
      val firstBeer = fakeBeerModel.copy(isFavorite = !fakeBeerModel.isFavorite)
      val secondBeer = firstBeer.copy(isFavorite = fakeBeerModel.isFavorite)
      coEvery { repository.updateFavorite(firstBeer) } coAnswers
        {
          firstStarted.complete(Unit)
          releaseFirst.await()
          Either.Left(UpdateFavoriteError.Unknown(fakeException))
        }
      coEvery { repository.updateFavorite(secondBeer) } returns Either.Right(Unit)
      val viewModel = BeerDetailViewModel(repository, fakeBeerModel)

      viewModel.updateFavorite(fakeBeerModel)
      testDispatcher.scheduler.runCurrent()
      firstStarted.await()
      viewModel.updateFavorite(firstBeer)
      testDispatcher.scheduler.runCurrent()
      expectThat(viewModel.beerDetailViewState.value).isEqualTo(CommonUiState.Success(firstBeer))

      releaseFirst.complete(Unit)
      testDispatcher.scheduler.advanceUntilIdle()
      expectThat(viewModel.beerDetailViewState.value).isEqualTo(CommonUiState.Success(secondBeer))
    }

  @Test
  fun `availability and favorite updates can be in flight independently`() =
    runTest(testDispatcher) {
      val availabilityStarted = CompletableDeferred<Unit>()
      val favoriteStarted = CompletableDeferred<Unit>()
      val release = CompletableDeferred<Unit>()
      val repository = mockk<BeersRepository>()
      val availabilityBeer = fakeBeerModel.copy(availability = !fakeBeerModel.availability)
      val favoriteBeer = fakeBeerModel.copy(isFavorite = !fakeBeerModel.isFavorite)
      coEvery { repository.updateAvailability(availabilityBeer) } coAnswers
        {
          availabilityStarted.complete(Unit)
          release.await()
          Either.Right(Unit)
        }
      coEvery { repository.updateFavorite(favoriteBeer) } coAnswers
        {
          favoriteStarted.complete(Unit)
          release.await()
          Either.Right(Unit)
        }
      val viewModel = BeerDetailViewModel(repository, fakeBeerModel)

      viewModel.updateAvailability(fakeBeerModel)
      viewModel.updateFavorite(fakeBeerModel)
      testDispatcher.scheduler.runCurrent()
      availabilityStarted.await()
      favoriteStarted.await()
      release.complete(Unit)
      testDispatcher.scheduler.advanceUntilIdle()
    }

  @Test
  fun `overlapping updates are serialized so a failed update cannot roll back a newer update`() =
    runTest(testDispatcher) {
      val firstUpdateStarted = CompletableDeferred<Unit>()
      val releaseFirstUpdate = CompletableDeferred<Unit>()
      val secondUpdateStarted = CompletableDeferred<Unit>()
      val repository = mockk<BeersRepository>()
      val firstUpdatedBeer = fakeBeerModel.copy(availability = !fakeBeerModel.availability)
      val secondUpdatedBeer = firstUpdatedBeer.copy(availability = fakeBeerModel.availability)
      coEvery { repository.updateAvailability(firstUpdatedBeer) } coAnswers
        {
          firstUpdateStarted.complete(Unit)
          releaseFirstUpdate.await()
          Either.Left(UpdateAvailabilityError.Unknown(fakeException))
        }
      coEvery { repository.updateAvailability(secondUpdatedBeer) } coAnswers
        {
          secondUpdateStarted.complete(Unit)
          Either.Right(Unit)
        }
      val viewModel = BeerDetailViewModel(repository, fakeBeerModel)

      viewModel.updateAvailability(fakeBeerModel)
      testDispatcher.scheduler.runCurrent()
      firstUpdateStarted.await()

      viewModel.updateAvailability(firstUpdatedBeer)
      testDispatcher.scheduler.runCurrent()
      expectThat(secondUpdateStarted.isCompleted).isEqualTo(false)

      releaseFirstUpdate.complete(Unit)
      testDispatcher.scheduler.advanceUntilIdle()
      secondUpdateStarted.await()

      expectThat((viewModel.beerDetailViewState.value as CommonUiState.Success).data)
        .isEqualTo(secondUpdatedBeer)
    }

  @Test
  fun `older serialized beer payload receives current default fields`() = runTest {
    val oldPayload =
      "{\"id\":\"1\",\"name\":\"Old\",\"tagline\":\"\",\"description\":\"\",\"imageUrl\":\"\",\"abv\":0.0," +
        "\"ibu\":0.0,\"foodPairing\":[],\"availability\":true}"

    val decoded = kotlinx.serialization.json.Json.decodeFromString<Beer>(oldPayload)

    expectThat(decoded.isFavorite).isEqualTo(false)
    expectThat(decoded.styleName).isEqualTo("")
    expectThat(decoded.breweryName).isEqualTo("")
    expectThat(decoded.srm).isEqualTo(null)
    expectThat(decoded.ingredients).isEqualTo(emptyList())
  }
}
