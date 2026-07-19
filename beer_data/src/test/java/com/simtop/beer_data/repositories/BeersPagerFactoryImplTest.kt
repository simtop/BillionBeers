package com.simtop.beer_data.repositories

import com.simtop.beer_data.fakes.FakeBeersLocalSource
import com.simtop.beer_data.fakes.FakeBeersRemoteSource
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.NoOpLogger
import com.simtop.core.core.PagingState
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
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
    factory = BeersPagerFactoryImpl(repository, LanguageProvider { "en" })
  }

  // Regression test: availability is treated as local-only (the server value only seeds first
  // insert), so a pull-to-refresh re-delivering the same beers must not reset a locally edited
  // value.
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
      expectThat(pager.pagingState.value).isEqualTo(PagingState.Success())
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

  // Regression test: refresh upserts into the cache without deleting, so afterwards the cache -
  // not the freshly fetched page - dictates where "load more" resumes.
  @Test
  fun `load more after a refresh resumes at the first uncached page`() =
    runTest(testDispatcher) {
      // One full page cached by a previous process; the server still returns the same page 1
      repository.insertAllToDB((1..25).map { Beer.empty.copy(id = "$it") })
      beersRemoteSource.setBeersResponse((1..25).map { apiItem(id = "$it") })
      val pager = factory.create()

      pager.loadFirstPage() // pull-to-refresh: upsert keeps all 25 rows
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "26")))
      pager.loadNextPage()

      expectThat(beersRemoteSource.requestedPages.toList()).isEqualTo(listOf(1, 2))
      expectThat(repository.countDBEntries()).isEqualTo(26)
    }

  // The paging_state bookmark is exact where the row-count estimate is not: a partial page leaves
  // fewer rows than pages fetched, so resume must trust the stored next_key, not rows/pageSize.
  @Test
  fun `load more resumes from the stored bookmark, not the row-count estimate`() =
    runTest(testDispatcher) {
      // Pages 1-2 fetched previously; page 2 was partial, so only 30 rows cached but next page is
      // 3.
      repository.insertPage(
        (1..30).map { Beer.empty.copy(id = "$it") },
        surface = "catalog:en",
        nextKey = 3,
        totalCount = null,
      )
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "31")))
      val pager = factory.create()

      pager.loadNextPage()

      // Row-count estimate would wrongly say page 2 (30/25 + 1); the bookmark says 3.
      expectThat(beersRemoteSource.requestedPages.toList()).isEqualTo(listOf(3))
    }

  // Regression guard for the monotonic next_key policy: a refresh re-fetching page 1 must not
  // rewind
  // a warm cache's bookmark, or "load more" would re-walk pages the cache already holds.
  @Test
  fun `refresh does not rewind the stored bookmark`() =
    runTest(testDispatcher) {
      // Warm cache already reached page 6 (25 rows of page 1 present, bookmark points past page 5).
      repository.insertPage(
        (1..25).map { Beer.empty.copy(id = "$it") },
        surface = "catalog:en",
        nextKey = 6,
        totalCount = 206,
      )
      beersRemoteSource.setBeersResponse((1..25).map { apiItem(id = "$it") }, totalCount = 206)
      val pager = factory.create()

      pager.loadFirstPage() // refresh: page 1 fetched, its own nextKey would be 2

      expectThat(repository.pagingNextKey("catalog:en")).isEqualTo(6)
    }

  @Test
  fun `each create returns an independent pager`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")))
      val first = factory.create()
      val second = factory.create()

      first.loadFirstPage()

      expectThat(first.pagingState.value).isEqualTo(PagingState.Success())
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
  fun `HTTP 429 is classified as RateLimited`() =
    runTest(testDispatcher) {
      val http429 = HttpException(Response.error<Any>(429, "".toResponseBody(null)))
      beersRemoteSource.setShouldThrowError(true, http429)
      val pager = factory.create()

      pager.loadFirstPage()

      expectThat(pager.pagingState.value)
        .isEqualTo(PagingState.Error(FetchBeersError.RateLimited, isFirstPage = true))
    }

  @Test
  fun `a search pager fetches with the query term and keeps results in memory`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")), totalCount = 1)
      val pager = factory.create(BeersQuery(search = "ipa"))

      pager.loadFirstPage()

      expectThat(pager.data.first().map { it.id }).isEqualTo(listOf("1"))
      expectThat(beersRemoteSource.requestedSearches.toList()).isEqualTo(listOf("ipa"))
      // Pure in-memory surface: a search must never write into the catalog's beers table.
      expectThat(repository.countDBEntries()).isEqualTo(0)
    }

  @Test
  fun `a style-filtered pager fetches with the typology id and keeps results in memory`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")), totalCount = 1)
      val pager = factory.create(BeersQuery(styleId = "style-1"))

      pager.loadFirstPage()

      expectThat(pager.data.first().map { it.id }).isEqualTo(listOf("1"))
      expectThat(beersRemoteSource.requestedTypologyIds.toList()).isEqualTo(listOf("style-1"))
      // One page load = exactly one /beers request (the browse analogue of the N+1 guard).
      expectThat(beersRemoteSource.requestedPages.toList()).isEqualTo(listOf(1))
      expectThat(repository.countDBEntries()).isEqualTo(0)
    }

  @Test
  fun `a brewery-filtered pager fetches with the brewery id`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")), totalCount = 1)
      val pager = factory.create(BeersQuery(breweryId = "brew-1"))

      pager.loadFirstPage()

      expectThat(beersRemoteSource.requestedBreweryIds.toList()).isEqualTo(listOf("brew-1"))
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
      translations = emptyList(),
      foodPairing = emptyList(),
    )
}
