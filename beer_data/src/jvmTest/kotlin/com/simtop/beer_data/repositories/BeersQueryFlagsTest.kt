package com.simtop.beer_data.repositories

import app.cash.turbine.test
import com.simtop.beer_data.fakes.FakeBeersLocalSource
import com.simtop.beer_data.fakes.FakeBeersRemoteSource
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_storage.api.BeersStorage
import com.simtop.beer_storage.api.StoredBeerLocalFlags
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.NoOpLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@OptIn(ExperimentalCoroutinesApi::class)
class BeersQueryFlagsTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private val languageProvider = LanguageProvider { "en" }
  private val logger = NoOpLogger()
  private lateinit var beersRemoteSource: FakeBeersRemoteSource
  private lateinit var beersLocalSource: FakeBeersLocalSource
  private lateinit var repository: BeersRepository
  private lateinit var factory: BeersPagerFactoryImpl

  @BeforeEach
  fun setUp() {
    beersRemoteSource = FakeBeersRemoteSource()
    beersLocalSource = FakeBeersLocalSource()
    installStorage(beersLocalSource)
  }

  private fun installStorage(storage: BeersStorage) {
    repository =
      BeersRepositoryImpl(
        beersRemoteSource,
        storage,
        BeersMapper(languageProvider, logger),
        languageProvider,
        logger,
      )
    factory = BeersPagerFactoryImpl(repository, languageProvider, logger)
  }

  @Test
  fun `a query pager reflects an availability edit made on the detail screen`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")), totalCount = 1)
      val pager = factory.create(BeersQuery(search = "ipa"))
      pager.loadFirstPage()
      val beer = pager.data.first().single()
      expectThat(beer.availability).isEqualTo(true)

      repository.updateAvailability(beer.copy(availability = false))

      expectThat(pager.data.first().single().availability).isEqualTo(false)
    }

  @Test
  fun `a query pager reflects a favorite edit without losing catalog fields`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")), totalCount = 1)
      val pager = factory.create(BeersQuery(search = "ipa"))
      pager.loadFirstPage()
      val beer = pager.data.first().single()

      repository.updateFavorite(beer.copy(isFavorite = true))

      expectThat(pager.data.first().single()).isEqualTo(beer.copy(isFavorite = true))
      expectThat(repository.countDBEntries()).isEqualTo(1)
    }

  @Test
  fun `query pager keeps last flags and retries a transient local flag read failure`() =
    runTest(testDispatcher) {
      var failFlagReads = true
      val storage =
        object : BeersStorage by beersLocalSource {
          override fun observeLocalFlags(ids: Set<String>): Flow<List<StoredBeerLocalFlags>> =
            flow {
              if (failFlagReads) error("transient flag read")
              emitAll(beersLocalSource.observeLocalFlags(ids))
            }
        }
      installStorage(storage)
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")), totalCount = 1)
      val pager = factory.create(BeersQuery(search = "ipa"))
      pager.loadFirstPage()

      pager.data.test {
        val initial = awaitItem().single()
        expectThat(initial.isFavorite).isEqualTo(false)

        repository.updateFavorite(initial.copy(isFavorite = true))
        failFlagReads = false
        advanceTimeBy(1_000L)
        runCurrent()

        expectThat(awaitItem().single()).isEqualTo(initial.copy(isFavorite = true))
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `query pager keeps visible flags across append when the expanded flag read fails`() =
    runTest(testDispatcher) {
      var failFlagReads = false
      val storage =
        object : BeersStorage by beersLocalSource {
          override fun observeLocalFlags(ids: Set<String>): Flow<List<StoredBeerLocalFlags>> =
            flow {
              if (failFlagReads) error("transient flag read")
              emitAll(beersLocalSource.observeLocalFlags(ids))
            }
        }
      installStorage(storage)
      beersRemoteSource.setBeersResponse(
        page = 1,
        beers = listOf(apiItem(id = "1")),
        totalCount = 50,
      )
      beersRemoteSource.setBeersResponse(
        page = 2,
        beers = listOf(apiItem(id = "2")),
        totalCount = 50,
      )
      val pager = factory.create(BeersQuery(search = "ipa"))
      pager.loadFirstPage()

      pager.data.test {
        val initial = awaitItem().single()
        repository.updateAvailability(initial.copy(availability = false))
        val unavailable = awaitItem().single()
        expectThat(unavailable.availability).isEqualTo(false)

        repository.updateFavorite(unavailable.copy(isFavorite = true))
        val edited = awaitItem().single()
        expectThat(edited.isFavorite).isEqualTo(true)

        failFlagReads = true
        pager.loadNextPage()

        var appended = awaitItem()
        while (appended.map(Beer::id) != listOf("1", "2")) appended = awaitItem()
        expectThat(appended.first().availability).isEqualTo(false)
        expectThat(appended.first().isFavorite).isEqualTo(true)

        val freshCollector = pager.data.first().first()
        expectThat(freshCollector.availability).isEqualTo(true)
        expectThat(freshCollector.isFavorite).isEqualTo(false)

        failFlagReads = false
        advanceTimeBy(1_000L)
        runCurrent()
        var recovered = awaitItem()
        while (
          recovered.size != 2 || recovered.first().availability || !recovered.first().isFavorite
        ) {
          recovered = awaitItem()
        }
        expectThat(recovered.first().availability).isEqualTo(false)
        expectThat(recovered.first().isFavorite).isEqualTo(true)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `query flag cache drops removed ids before they are reintroduced`() =
    runTest(testDispatcher) {
      var failFlagReads = false
      val storage =
        object : BeersStorage by beersLocalSource {
          override fun observeLocalFlags(ids: Set<String>): Flow<List<StoredBeerLocalFlags>> =
            flow {
              if (failFlagReads) error("transient flag read")
              emitAll(beersLocalSource.observeLocalFlags(ids))
            }
        }
      installStorage(storage)
      beersRemoteSource.setBeersResponse(
        page = 1,
        beers = listOf(apiItem(id = "1"), apiItem(id = "2")),
        totalCount = 50,
      )
      val pager = factory.create(BeersQuery(search = "ipa"))
      pager.loadFirstPage()

      pager.data.test {
        val initial = awaitItem().first()
        repository.updateFavorite(initial.copy(isFavorite = true))
        var favoriteUpdate = awaitItem()
        while (!favoriteUpdate.first().isFavorite) favoriteUpdate = awaitItem()

        beersRemoteSource.setBeersResponse(
          page = 1,
          beers = listOf(apiItem(id = "2"), apiItem(id = "3")),
          totalCount = 50,
        )
        pager.loadFirstPage()
        var refreshed = awaitItem()
        while (refreshed.map(Beer::id) != listOf("2", "3")) refreshed = awaitItem()

        failFlagReads = true
        beersRemoteSource.setBeersResponse(
          page = 1,
          beers = listOf(apiItem(id = "1"), apiItem(id = "3")),
          totalCount = 50,
        )
        pager.loadFirstPage()
        var reintroduced = awaitItem()
        while (reintroduced.map(Beer::id) != listOf("1", "3")) reintroduced = awaitItem()
        expectThat(reintroduced.first().isFavorite).isEqualTo(false)
        cancelAndIgnoreRemainingEvents()
      }
    }

  private fun apiItem(id: String, name: String = "Beer $id") =
    BeersApiResponseItem(
      id = id,
      name = name,
      abv = 5.0,
      ibu = 20.0,
      translations = emptyList(),
      foodPairing = emptyList(),
    )
}
