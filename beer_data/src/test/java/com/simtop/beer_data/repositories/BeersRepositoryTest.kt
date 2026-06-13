package com.simtop.beer_data.repositories

import app.cash.turbine.test
import com.simtop.beer_data.fakes.FakeBeersLocalSource
import com.simtop.beer_data.fakes.FakeBeersRemoteSource
import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beerdomain.domain.models.Beer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import com.simtop.beer_data.mappers.BeersMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@ExperimentalCoroutinesApi
class BeersRepositoryTest {

  private lateinit var beersRemoteSource: FakeBeersRemoteSource
  private lateinit var beersLocalSource: FakeBeersLocalSource
  private lateinit var beersRepository: BeersRepositoryImpl
  private val testDispatcher = UnconfinedTestDispatcher()

  @BeforeEach
  fun setUp() {
    beersRemoteSource = FakeBeersRemoteSource()
    beersLocalSource = FakeBeersLocalSource()
    val beersMapper = BeersMapper()
    beersRepository = BeersRepositoryImpl(beersRemoteSource, beersLocalSource, beersMapper)
  }

  @Test
  fun `getBeersFromSingleSource should load first page when DB is empty`() =
    runTest(testDispatcher) {
      // Arrange
      val remoteBeer =
        BeersApiResponseItem(
          id = "1",
          name = "Beer 1",
          abv = 5.0,
          ibu = 20.0,
          imageId = "url",
          translations = emptyList(),
          foodPairing = emptyList(),
        )
      beersRemoteSource.setBeersResponse(listOf(remoteBeer))

      // Act & Assert
      beersRepository.getBeersFromSingleSource(1).test {
        // PagingMediator triggers loadFirstPage -> fetchRemote -> saveLocal
        // We expect the local source to be updated, which emits new value to flow

        // Wait for the update
        val updatedList = awaitItem()
        expectThat(updatedList.size).isEqualTo(1)
        expectThat(updatedList[0].id).isEqualTo("1")

        // Verify Remote was called (implicitly by result)
        // Verify Local was updated
        expectThat(beersLocalSource.getCountFromDB()).isEqualTo(1)
      }
    }

  @Test
  fun `loadNextPage should trigger remote fetch and insert`() =
    runTest(testDispatcher) {
      // Arrange
      // Pre-populate DB to simulate existing data
      // Note: PagingMediator logic might be tricky here.
      // If we just call loadNextPage, it uses currentKey.

      val remoteBeer =
        BeersApiResponseItem(
          id = "2",
          name = "Beer 2",
          abv = 6.0,
          ibu = 25.0,
          imageId = "url",
          translations = emptyList(),
          foodPairing = emptyList(),
        )
      beersRemoteSource.setBeersResponse(listOf(remoteBeer))

      // Act
      beersRepository.loadNextPage()

      // Assert
      // Since we use UnconfinedTestDispatcher, the coroutine should execute immediately.
      // loadNextPage -> fetchRemote -> saveLocal

      // Check if data is in local source
      val beers = beersLocalSource.getBeers()
      expectThat(beers.size).isEqualTo(1)
      expectThat(beers[0].id).isEqualTo("2")
    }

  @Test
  fun `getListOfBeerFromApi should call remote source`() =
    runTest(testDispatcher) {
      // Arrange
      val remoteBeer =
        BeersApiResponseItem(
          id = "3",
          name = "Beer 3",
          abv = 0.0,
          ibu = 0.0,
          imageId = "url",
          translations = emptyList(),
          foodPairing = emptyList(),
        )
      beersRemoteSource.setBeersResponse(listOf(remoteBeer))

      // Act
      val result = beersRepository.getListOfBeerFromApi(1)

      // Assert
      expectThat(result.size).isEqualTo(1)
      expectThat(result[0].id).isEqualTo("3")
      expectThat(result[0].imageUrl).isEqualTo("https://fake.url/image.jpg")
    }

  @Test
  fun `updateAvailability should call local source`() =
    runTest(testDispatcher) {
      // Arrange
      val beer = Beer.empty.copy(id = "1", availability = true)
      // Pre-populate fake local source
      beersLocalSource.insertAllToDB(
        listOf(
          BeerDbModel(
            id = "1",
            name = "Beer 1",
            tagline = "",
            description = "",
            imageUrl = "",
            abv = 0.0,
            ibu = 0.0,
            foodPairing = "[]",
            availability = true,
          )
        )
      )

      // Act
      beersRepository.updateAvailability(beer.copy(availability = false))

      // Assert
      val updatedBeer = beersLocalSource.getBeers().first()
      expectThat(updatedBeer.availability).isEqualTo(false)
    }

  @Test
  fun `insertAllToDB should call local source`() =
    runTest(testDispatcher) {
      // Arrange
      val beer = Beer.empty.copy(id = "4", name = "Beer 4")

      // Act
      beersRepository.insertAllToDB(listOf(beer))

      // Assert
      val beers = beersLocalSource.getBeers()
      expectThat(beers.size).isEqualTo(1)
      expectThat(beers[0].id).isEqualTo("4")
    }

  @Test
  fun `getAllBeersFromDB should return flow from local source`() =
    runTest(testDispatcher) {
      // Arrange
      val dbBeer =
        com.simtop.beer_database.models.BeerDbModel(
          id = "5",
          name = "Beer 5",
          tagline = "",
          description = "",
          imageUrl = "",
          abv = 0.0,
          ibu = 0.0,
          foodPairing = "[]",
          availability = true,
        )
      beersLocalSource.insertAllToDB(listOf(dbBeer))

      // Act
      val list = beersRepository.getAllBeersFromDB()

      // Assert
      expectThat(list.size).isEqualTo(1)
      expectThat(list[0].id).isEqualTo("5")
    }

  @Test
  fun `refresh should trigger loadFirstPage`() =
    runTest(testDispatcher) {
      // Arrange
      val remoteBeer =
        BeersApiResponseItem(
          id = "6",
          name = "Beer 6",
          abv = 0.0,
          ibu = 0.0,
          imageId = "url",
          translations = emptyList(),
          foodPairing = emptyList(),
        )
      beersRemoteSource.setBeersResponse(listOf(remoteBeer))

      // Act
      beersRepository.refresh()

      // Assert
      val beers = beersLocalSource.getBeers()
      expectThat(beers.size).isEqualTo(1)
      expectThat(beers[0].id).isEqualTo("6")
    }

  @Test
  fun `observePagingState should return paging state from mediator`() =
    runTest(testDispatcher) {
      // Act
      val stateFlow = beersRepository.observePagingState()

      // Assert
      // Initial state should be Idle (from PagingMediator default)
      expectThat(stateFlow.value).isEqualTo(com.simtop.core.core.PagingState.Idle)
    }
}
