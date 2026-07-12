package com.simtop.beer_data.repositories

import app.cash.turbine.test
import com.simtop.beer_data.fakes.FakeBeersLocalSource
import com.simtop.beer_data.fakes.FakeBeersRemoteSource
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.EmbeddedImage
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.NoOpLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
    val beersMapper = BeersMapper(LanguageProvider { "en" }, NoOpLogger())
    beersRepository = BeersRepositoryImpl(beersRemoteSource, beersLocalSource, beersMapper)
  }

  @Test
  fun `getListOfBeerFromApi maps beers using the embedded image url without a second request`() =
    runTest(testDispatcher) {
      // Arrange
      val remoteBeer =
        BeersApiResponseItem(
          id = "3",
          name = "Beer 3",
          abv = 0.0,
          ibu = 0.0,
          image = EmbeddedImage("https://embedded.url/image.jpg"),
          translations = emptyList(),
          foodPairing = emptyList(),
        )
      beersRemoteSource.setBeersResponse(listOf(remoteBeer))

      // Act
      val result = beersRepository.getListOfBeerFromApi(1)

      // Assert: the embedded url is used, and only the single list request was made.
      expectThat(result.size).isEqualTo(1)
      expectThat(result[0].id).isEqualTo("3")
      expectThat(result[0].imageUrl).isEqualTo("https://embedded.url/image.jpg")
      expectThat(beersRemoteSource.requestedPages.toList()).isEqualTo(listOf(1))
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
  fun `getAllBeersFromDB should return current local list`() =
    runTest(testDispatcher) {
      // Arrange
      val dbBeer =
        BeerDbModel(
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
  fun `observeBeers should emit local source updates`() =
    runTest(testDispatcher) {
      beersRepository.observeBeers().test {
        expectThat(awaitItem()).isEqualTo(emptyList())

        beersRepository.insertAllToDB(listOf(Beer.empty.copy(id = "6", name = "Beer 6")))

        val beers = awaitItem()
        expectThat(beers.size).isEqualTo(1)
        expectThat(beers[0].id).isEqualTo("6")
      }
    }
}
