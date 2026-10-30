package com.simtop.billionbeers.shared.beerdetail

import app.cash.turbine.test
import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.errors.UpdateFavoriteError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.beerdomain.fakes.fakeBeerModel
import com.simtop.beerdomain.fakes.fakeException
import com.simtop.billionbeers.testing_utils.MainDispatcherExtension
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.Either
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

@ExperimentalCoroutinesApi
internal class BeerDetailViewModelTest {

  private val fakeBeersRepository = FakeBeersRepository()
  @JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()
  private val testDispatcher
    get() = mainDispatcher.testDispatcher

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
  fun `detail route resolves its beer from the repository by id`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(fakeBeerModel))
      val viewModel = BeerDetailViewModel(fakeBeersRepository, fakeBeerModel.id)

      viewModel.beerDetailViewState.test {
        expectThat(awaitItem()).isEqualTo(CommonUiState.Loading)
        testDispatcher.scheduler.advanceUntilIdle()
        expectThat(awaitItem()).isEqualTo(CommonUiState.Success(fakeBeerModel))
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

          beerDetailViewModel.updateAvailability()
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

          beerDetailViewModel.updateAvailability()

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
          viewModel.updateFavorite()
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
          viewModel.updateFavorite()
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

      viewModel.updateFavorite()
      testDispatcher.scheduler.runCurrent()
      firstStarted.await()
      viewModel.updateFavorite()
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
      val favoriteBeer =
        fakeBeerModel.copy(
          availability = !fakeBeerModel.availability,
          isFavorite = !fakeBeerModel.isFavorite,
        )
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

      viewModel.updateAvailability()
      viewModel.updateFavorite()
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

      viewModel.updateAvailability()
      testDispatcher.scheduler.runCurrent()
      firstUpdateStarted.await()

      viewModel.updateAvailability()
      testDispatcher.scheduler.runCurrent()
      expectThat(secondUpdateStarted.isCompleted).isEqualTo(false)

      releaseFirstUpdate.complete(Unit)
      testDispatcher.scheduler.advanceUntilIdle()
      secondUpdateStarted.await()

      expectThat((viewModel.beerDetailViewState.value as CommonUiState.Success).data)
        .isEqualTo(secondUpdatedBeer)
    }

  @Test
  fun `retry recovers a failed lookup and ignores duplicate in flight requests`() =
    runTest(testDispatcher) {
      val repository = mockk<BeersRepository>()
      val releaseRetry = CompletableDeferred<Beer?>()
      var reads = 0
      coEvery { repository.getBeerById(fakeBeerModel.id) } coAnswers
        {
          if (++reads == 1) throw fakeException
          releaseRetry.await()
        }
      val model = BeerDetailViewModel(repository, fakeBeerModel.id)
      testDispatcher.scheduler.runCurrent()
      expectThat(model.beerDetailViewState.value).isA<CommonUiState.Error>()

      model.retryLoad()
      model.retryLoad()
      testDispatcher.scheduler.runCurrent()
      expectThat(reads).isEqualTo(2)
      expectThat(model.beerDetailViewState.value).isEqualTo(CommonUiState.Loading)
      releaseRetry.complete(fakeBeerModel)
      testDispatcher.scheduler.runCurrent()

      expectThat(model.beerDetailViewState.value).isEqualTo(CommonUiState.Success(fakeBeerModel))
      model.retryLoad()
      testDispatcher.scheduler.runCurrent()
      expectThat(reads).isEqualTo(2)
    }

  @Test
  fun `retry resolves a beer that was missing on the first lookup`() =
    runTest(testDispatcher) {
      val repository = FakeBeersRepository()
      val model = BeerDetailViewModel(repository, fakeBeerModel.id)
      testDispatcher.scheduler.runCurrent()
      expectThat(model.beerDetailViewState.value).isA<CommonUiState.Error>()
      repository.setBeers(listOf(fakeBeerModel))

      model.retryLoad()
      testDispatcher.scheduler.runCurrent()

      expectThat(model.beerDetailViewState.value).isEqualTo(CommonUiState.Success(fakeBeerModel))
    }

  @Test
  fun `cancelled lookup propagates cancellation and can be explicitly retried`() =
    runTest(testDispatcher) {
      val repository = mockk<BeersRepository>()
      var reads = 0
      coEvery { repository.getBeerById(fakeBeerModel.id) } coAnswers
        {
          if (++reads == 1) throw CancellationException("lookup cancelled")
          fakeBeerModel
        }
      val model = BeerDetailViewModel(repository, fakeBeerModel.id)
      testDispatcher.scheduler.runCurrent()
      expectThat(model.beerDetailViewState.value).isEqualTo(CommonUiState.Loading)

      model.retryLoad()
      testDispatcher.scheduler.runCurrent()

      expectThat(reads).isEqualTo(2)
      expectThat(model.beerDetailViewState.value).isEqualTo(CommonUiState.Success(fakeBeerModel))
    }

  @Test
  fun `two failed favorite writes restore the persisted favorite`() =
    assertFailedSecondWriteRestoresCommittedBeer(favorite = true, firstWriteFails = true)

  @Test
  fun `failed favorite write preserves an earlier committed write`() =
    assertFailedSecondWriteRestoresCommittedBeer(favorite = true, firstWriteFails = false)

  @Test
  fun `two failed availability writes restore the persisted availability`() =
    assertFailedSecondWriteRestoresCommittedBeer(favorite = false, firstWriteFails = true)

  @Test
  fun `failed availability write preserves an earlier committed write`() =
    assertFailedSecondWriteRestoresCommittedBeer(favorite = false, firstWriteFails = false)

  private fun assertFailedSecondWriteRestoresCommittedBeer(
    favorite: Boolean,
    firstWriteFails: Boolean,
  ) =
    runTest(testDispatcher) {
      val initial = fakeBeerModel.copy(isFavorite = false, availability = true)
      val persisted = FakeBeersRepository(listOf(initial))
      val repository = mockk<BeersRepository>()
      val releaseFirst = CompletableDeferred<Unit>()
      var writes = 0
      suspend fun shouldCommit(): Boolean {
        val isFirst = ++writes == 1
        if (isFirst) releaseFirst.await()
        return isFirst && !firstWriteFails
      }
      coEvery { repository.updateFavorite(any()) } coAnswers
        {
          if (shouldCommit()) persisted.updateFavorite(firstArg())
          else Either.Left(UpdateFavoriteError.Unknown(fakeException))
        }
      coEvery { repository.updateAvailability(any()) } coAnswers
        {
          if (shouldCommit()) persisted.updateAvailability(firstArg())
          else Either.Left(UpdateAvailabilityError.Unknown(fakeException))
        }
      val model = BeerDetailViewModel(repository, initial)
      val toggle = if (favorite) model::updateFavorite else model::updateAvailability
      toggle()
      testDispatcher.scheduler.runCurrent()
      toggle()
      testDispatcher.scheduler.runCurrent()
      expectThat(writes).isEqualTo(1)

      releaseFirst.complete(Unit)
      testDispatcher.scheduler.advanceUntilIdle()

      expectThat(writes).isEqualTo(2)
      expectThat((model.beerDetailViewState.value as CommonUiState.Success).data)
        .isEqualTo(persisted.getBeerById(initial.id))
    }

  @Test
  fun `failed edits after an id lookup restore the loaded flags`() =
    runTest(testDispatcher) {
      val loaded = fakeBeerModel.copy(availability = false, isFavorite = true)
      val repository = FakeBeersRepository(listOf(loaded))
      val model = BeerDetailViewModel(repository, loaded.id)
      testDispatcher.scheduler.advanceUntilIdle()
      repository.setExceptionToThrow(fakeException)

      model.updateAvailability()
      model.updateFavorite()
      testDispatcher.scheduler.advanceUntilIdle()

      expectThat(model.beerDetailViewState.value).isEqualTo(CommonUiState.Success(loaded))
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
