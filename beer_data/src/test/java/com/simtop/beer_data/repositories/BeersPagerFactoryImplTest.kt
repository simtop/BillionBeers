package com.simtop.beer_data.repositories

import com.simtop.beer_data.fakes.FakeBeersLocalSource
import com.simtop.beer_data.fakes.FakeBeersRemoteSource
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.NoOpLogger
import com.simtop.core.core.PagingState
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

@ExperimentalCoroutinesApi
class BeersPagerFactoryImplTest {

  private lateinit var beersRemoteSource: FakeBeersRemoteSource
  private lateinit var beersLocalSource: FakeBeersLocalSource
  private lateinit var repository: BeersRepository
  private lateinit var factory: BeersPagerFactoryImpl
  private val testDispatcher = UnconfinedTestDispatcher()

  @BeforeEach
  fun setUp() {
    beersRemoteSource = FakeBeersRemoteSource()
    beersLocalSource = FakeBeersLocalSource()
    val beersMapper = BeersMapper(LanguageProvider { "en" }, NoOpLogger())
    repository = BeersRepositoryImpl(beersRemoteSource, beersLocalSource, beersMapper)
    factory = BeersPagerFactoryImpl(repository)
  }

  // Regression test: availability only exists locally (the API has no such field), so a
  // pull-to-refresh re-delivering the same beers must not reset a locally edited availability.
  @Test
  fun `refresh keeps locally edited availability`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")))
      val pager = factory.create()
      pager.loadFirstPage()
      val beer = repository.getBeerById("1")
      expectThat(beer).isNotNull()
      repository.updateAvailability(beer!!.copy(availability = false))

      pager.loadFirstPage() // pull-to-refresh: same server payload, no availability in it

      expectThat(repository.getBeerById("1")?.availability).isEqualTo(false)
      expectThat(pager.pagingState.value).isEqualTo(PagingState.Success)
    }

  @Test
  fun `refresh upserts instead of wiping the cache`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1"), apiItem(id = "2")))
      val pager = factory.create()
      pager.loadFirstPage()

      // Beer 2 falls off the server's first page; refresh must not destroy the cached row
      // (deleting and re-inserting is what used to reset availability).
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")))
      pager.loadFirstPage()

      expectThat(repository.getAllBeersFromDB().map { it.id }).isEqualTo(listOf("1", "2"))
    }

  // Regression test: the Room cache outlives any pager, so a warm launch (cache already populated,
  // no first-page load) must resume "load more" after the cached pages instead of silently
  // re-fetching page 1 onwards over the network.
  @Test
  fun `load more over a warm cache resumes at the first uncached page`() =
    runTest(testDispatcher) {
      // One full page (25 beers) cached by a previous process
      repository.insertAllToDB((1..25).map { Beer.empty.copy(id = "$it") })
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "26")))
      val pager = factory.create()

      pager.loadNextPage()

      expectThat(beersRemoteSource.requestedPages.toList()).isEqualTo(listOf(2))
      expectThat(repository.countDBEntries()).isEqualTo(26)
    }

  @Test
  fun `each create returns an independent pager`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")))
      val first = factory.create()
      val second = factory.create()

      first.loadFirstPage()

      expectThat(first.pagingState.value).isEqualTo(PagingState.Success)
      expectThat(second.pagingState.value).isEqualTo(PagingState.Idle)
    }

  @Test
  fun `IOException is classified as Network error`() =
    runTest(testDispatcher) {
      beersRemoteSource.setShouldThrowError(true, IOException("no connection"))
      val pager = factory.create()

      pager.loadFirstPage()

      expectThat(pager.pagingState.value)
        .isEqualTo(PagingState.Error(FetchBeersError.Network, isFirstPage = true))
      expectThat(repository.countDBEntries()).isEqualTo(0)
    }

  @Test
  fun `pager data emits beers from the local source`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")))
      val pager = factory.create()

      pager.loadFirstPage()

      expectThat(beersLocalSource.getBeers().map { it.id }).isEqualTo(listOf("1"))
      expectThat(pager.data.first().map { it.id }).isEqualTo(listOf("1"))
    }

  private fun apiItem(id: String, name: String = "Beer $id") =
    BeersApiResponseItem(
      id = id,
      name = name,
      abv = 5.0,
      ibu = 20.0,
      imageId = "url",
      translations = emptyList(),
      foodPairing = emptyList(),
    )
}
