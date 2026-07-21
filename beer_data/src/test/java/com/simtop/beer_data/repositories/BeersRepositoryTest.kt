package com.simtop.beer_data.repositories

import app.cash.turbine.test
import com.simtop.beer_data.fakes.FakeBeersLocalSource
import com.simtop.beer_data.fakes.FakeBeersRemoteSource
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.BreweryApiResponseItem
import com.simtop.beer_network.models.EmbeddedCountry
import com.simtop.beer_network.models.EmbeddedImage
import com.simtop.beer_network.models.TypologyApiResponseItem
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.beerdomain.domain.models.CatalogCacheStatus
import com.simtop.core.core.Either
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.NoOpLogger
import java.io.IOException
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
    beersRepository =
      BeersRepositoryImpl(
        beersRemoteSource,
        beersLocalSource,
        beersMapper,
        LanguageProvider { "en" },
      )
  }

  @Test
  fun `getBeersPageFromApi maps beers with the embedded image url and carries the total count`() =
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
      beersRemoteSource.setBeersResponse(listOf(remoteBeer), totalCount = 206)

      // Act
      val result = beersRepository.getBeersPageFromApi(1)

      // Assert: the embedded url is used, the total count flows through, one request only.
      expectThat(result.items.size).isEqualTo(1)
      expectThat(result.items[0].id).isEqualTo("3")
      expectThat(result.items[0].imageUrl).isEqualTo("https://embedded.url/image.jpg")
      expectThat(result.totalCount).isEqualTo(206)
      expectThat(beersRemoteSource.requestedPages.toList()).isEqualTo(listOf(1))
    }

  @Test
  fun `getBeersPageFromApi forwards the search term to the remote source`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(emptyList(), totalCount = 0)

      beersRepository.getBeersPageFromApi(1, BeersQuery(search = "stout"))

      expectThat(beersRemoteSource.requestedSearches.toList()).isEqualTo(listOf("stout"))
    }

  @Test
  fun `getBeersPageFromApi forwards the style and brewery filters to the remote source`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(emptyList(), totalCount = 0)

      beersRepository.getBeersPageFromApi(1, BeersQuery(styleId = "style-1"))
      beersRepository.getBeersPageFromApi(1, BeersQuery(breweryId = "brew-1"))

      expectThat(beersRemoteSource.requestedTypologyIds.toList()).isEqualTo(listOf("style-1", null))
      expectThat(beersRemoteSource.requestedBreweryIds.toList()).isEqualTo(listOf(null, "brew-1"))
    }

  @Test
  fun `getBeerStyles maps typologies to domain styles`() =
    runTest(testDispatcher) {
      beersRemoteSource.typologiesResponse =
        listOf(TypologyApiResponseItem(id = "t1", name = "IPA (Indian Pale Ale)"))

      val result = beersRepository.getBeerStyles()

      expectThat(result)
        .isEqualTo(Either.Right(listOf(BeerStyle(id = "t1", name = "IPA (Indian Pale Ale)"))))
    }

  @Test
  fun `getBreweries maps embedded country and image to a domain brewery`() =
    runTest(testDispatcher) {
      beersRemoteSource.breweriesResponse =
        listOf(
          BreweryApiResponseItem(
            id = "b1",
            name = "Supreme Suds Collective",
            foundedYear = 1972,
            country = EmbeddedCountry(code = "KP"),
            image = EmbeddedImage("https://embedded.url/brewery.jpg"),
          )
        )

      val result = beersRepository.getBreweries()

      expectThat(result)
        .isEqualTo(
          Either.Right(
            listOf(
              Brewery(
                id = "b1",
                name = "Supreme Suds Collective",
                countryCode = "KP",
                foundedYear = 1972,
                imageUrl = "https://embedded.url/brewery.jpg",
              )
            )
          )
        )
    }

  @Test
  fun `unpaged fetch failures come back as classified errors, not thrown`() =
    runTest(testDispatcher) {
      beersRemoteSource.setShouldThrowError(true, IOException("no network"))

      expectThat(beersRepository.getBeerStyles()).isEqualTo(Either.Left(FetchBeersError.Network))
      expectThat(beersRepository.getBreweries()).isEqualTo(Either.Left(FetchBeersError.Network))
    }

  @Test
  fun `cache status is Empty with no rows`() =
    runTest(testDispatcher) {
      expectThat(beersRepository.catalogCacheStatus()).isEqualTo(CatalogCacheStatus.Empty)
    }

  @Test
  fun `cache status is Fresh with a recent bookmark for the current language`() =
    runTest(testDispatcher) {
      beersLocalSource.insertAllToDB(listOf(dbBeer("1")))
      beersLocalSource.setPagingState(
        "catalog:en",
        nextKey = 2,
        refreshedAt = System.currentTimeMillis() - ONE_HOUR_MILLIS,
      )

      expectThat(beersRepository.catalogCacheStatus()).isEqualTo(CatalogCacheStatus.Fresh)
    }

  @Test
  fun `cache status is Stale once the bookmark is older than the policy TTL`() =
    runTest(testDispatcher) {
      beersLocalSource.insertAllToDB(listOf(dbBeer("1")))
      beersLocalSource.setPagingState(
        "catalog:en",
        nextKey = 2,
        refreshedAt = System.currentTimeMillis() - TWENTY_FIVE_HOURS_MILLIS,
      )

      expectThat(beersRepository.catalogCacheStatus()).isEqualTo(CatalogCacheStatus.Stale)
    }

  // A cache written before the paging_state table existed: rows but zero bookmarks anywhere.
  // Its age is unknowable, so it counts as stale rather than trusted.
  @Test
  fun `cache status is Stale for a legacy cache with no bookmarks at all`() =
    runTest(testDispatcher) {
      beersLocalSource.insertAllToDB(listOf(dbBeer("1")))

      expectThat(beersRepository.catalogCacheStatus()).isEqualTo(CatalogCacheStatus.Stale)
    }

  @Test
  fun `cache status is LanguageMismatch when bookmarks belong to another language`() =
    runTest(testDispatcher) {
      beersLocalSource.insertAllToDB(listOf(dbBeer("1")))
      beersLocalSource.setPagingState(
        "catalog:es",
        nextKey = 2,
        refreshedAt = System.currentTimeMillis(),
      )

      expectThat(beersRepository.catalogCacheStatus())
        .isEqualTo(CatalogCacheStatus.LanguageMismatch)
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

  // Regression test: a beer reached through search/browse may not be in the catalog cache, and
  // the old plain UPDATE then matched zero rows - the edit silently evaporated. The upsert must
  // insert the row instead.
  @Test
  fun `updateAvailability persists the edit for a beer not yet cached`() =
    runTest(testDispatcher) {
      val beer = Beer.empty.copy(id = "42", availability = true)

      beersRepository.updateAvailability(beer.copy(availability = false))

      val inserted = beersLocalSource.getBeers().single()
      expectThat(inserted.id).isEqualTo("42")
      expectThat(inserted.availability).isEqualTo(false)
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

  private fun dbBeer(id: String) =
    BeerDbModel(
      id = id,
      name = "Beer $id",
      tagline = "",
      description = "",
      imageUrl = "",
      abv = 0.0,
      ibu = 0.0,
      foodPairing = "[]",
      availability = true,
    )

  private companion object {
    const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
    const val TWENTY_FIVE_HOURS_MILLIS = 25 * ONE_HOUR_MILLIS
  }
}
