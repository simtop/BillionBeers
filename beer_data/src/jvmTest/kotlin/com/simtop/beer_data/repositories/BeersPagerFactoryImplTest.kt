package com.simtop.beer_data.repositories

import app.cash.turbine.test
import com.simtop.beer_data.fakes.FakeBeersLocalSource
import com.simtop.beer_data.fakes.FakeBeersRemoteSource
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.Language
import com.simtop.beer_network.models.Translation
import com.simtop.beer_network.network.BeersServiceHttpException
import com.simtop.beer_network.network.BeersServiceNetworkException
import com.simtop.beer_storage.api.BeersStorage
import com.simtop.beer_storage.api.StoredBeer
import com.simtop.beer_storage.api.StoredPagingState
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.CatalogCacheStatus
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.Diagnostic
import com.simtop.core.core.DiagnosticArea
import com.simtop.core.core.DiagnosticCode
import com.simtop.core.core.DiagnosticLevel
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.LogTag
import com.simtop.core.core.Logger
import com.simtop.core.core.NoOpLogger
import com.simtop.core.core.PagingEvent
import com.simtop.core.core.PagingState
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
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
  private var currentLanguage = "en"
  private val languageProvider = LanguageProvider { currentLanguage }

  private val diagnostics = mutableListOf<Pair<LogTag, Diagnostic>>()
  private val recordingLogger = Logger { tag, diagnostic -> diagnostics += tag to diagnostic }

  @BeforeEach
  fun setUp() {
    beersRemoteSource = FakeBeersRemoteSource()
    beersLocalSource = FakeBeersLocalSource()
    currentLanguage = "en"
    diagnostics.clear()
    installStorage(beersLocalSource)
  }

  private fun installStorage(storage: BeersStorage, logger: Logger = recordingLogger) {
    repository =
      BeersRepositoryImpl(
        beersRemoteSource,
        storage,
        BeersMapper(languageProvider, NoOpLogger()),
        languageProvider,
        logger,
      )
    factory = BeersPagerFactoryImpl(repository, languageProvider, logger)
  }

  @Test
  fun `catalog and query fetch failures report exactly once`() =
    runTest(testDispatcher) {
      val cases =
        listOf(
          Triple(
            BeersServiceHttpException(503),
            FetchBeersError.Server,
            Diagnostic(
              DiagnosticArea.NETWORK,
              DiagnosticCode.SERVER_FAILURE,
              DiagnosticLevel.ERROR,
            ),
          ),
          Triple(
            SerializationException("private payload"),
            FetchBeersError.Parse,
            Diagnostic(
              DiagnosticArea.DATA_MAPPING,
              DiagnosticCode.PARSE_FAILURE,
              DiagnosticLevel.ERROR,
            ),
          ),
        )
      cases.forEach { (failure, expectedError, expectedDiagnostic) ->
        beersRemoteSource.setShouldThrowError(true, failure)
        listOf(factory.create(), factory.create(BeersQuery(search = "ipa"))).forEach { pager ->
          diagnostics.clear()
          pager.loadFirstPage()
          assertEquals(
            PagingState.Error(expectedError, isFirstPage = true),
            pager.pagingState.value,
          )
          assertEquals(listOf(LogTag.BEERS_REMOTE_SOURCE to expectedDiagnostic), diagnostics)
        }
      }
    }

  @Test
  fun `failed first-page and append writes report database failures and retry the same page`() =
    runTest(testDispatcher) {
      val failure = IllegalStateException("storage")
      var writeFailure: Exception? = failure
      val storage =
        object : BeersStorage by beersLocalSource {
          override suspend fun insertPage(
            beers: List<StoredBeer>,
            surface: String,
            nextKey: Int?,
            totalCount: Int?,
            pageKey: Int?,
          ) {
            writeFailure?.let { throw it }
            beersLocalSource.insertPage(beers, surface, nextKey, totalCount, pageKey)
          }
        }
      installStorage(storage)
      beersRemoteSource.setBeersResponse(listOf(apiItem("1")), totalCount = 100)
      val pager = factory.create()
      pager.loadFirstPage()
      assertEquals(
        PagingState.Error(FetchBeersError.Unknown(failure), true),
        pager.pagingState.value,
      )
      assertEquals(emptyList(), pager.data.first())
      assertDatabaseFailure()

      writeFailure = null
      pager.loadNextPage()
      assertEquals(PagingState.Success(100), pager.pagingState.value)
      assertEquals(listOf("1"), pager.data.first().map { it.id })

      diagnostics.clear()
      writeFailure = failure
      beersRemoteSource.setBeersResponse(listOf(apiItem("2")), totalCount = 100)
      pager.events.test {
        pager.loadNextPage()
        assertEquals(PagingEvent.LoadMoreFailed(FetchBeersError.Unknown(failure)), awaitItem())
        cancelAndIgnoreRemainingEvents()
      }
      assertEquals(
        PagingState.Error(FetchBeersError.Unknown(failure), false),
        pager.pagingState.value,
      )
      assertEquals(listOf("1"), pager.data.first().map { it.id })
      assertDatabaseFailure()

      writeFailure = null
      pager.loadNextPage()
      assertEquals(listOf(1, 1, 2, 2), beersRemoteSource.requestedPages)
      assertEquals(listOf("1", "2"), pager.data.first().map { it.id })
      assertEquals(PagingState.Success(100), pager.pagingState.value)
    }

  @Test
  fun `initial and post-refresh bookmark failures report database origin without losing cached data`() =
    runTest(testDispatcher) {
      val failure = IllegalStateException("bookmark")
      var readFailure: Exception? = failure
      val storage =
        object : BeersStorage by beersLocalSource {
          override suspend fun getPagingState(surface: String): StoredPagingState? {
            readFailure?.let { throw it }
            return beersLocalSource.getPagingState(surface)
          }
        }
      installStorage(storage)
      beersRemoteSource.setBeersResponse(listOf(apiItem("1")), totalCount = 100)
      val pager = factory.create()
      pager.loadNextPage()
      assertEquals(
        PagingState.Error(FetchBeersError.Unknown(failure), false),
        pager.pagingState.value,
      )
      assertEquals(emptyList(), beersRemoteSource.requestedPages)
      assertDatabaseFailure()

      readFailure = null
      pager.loadFirstPage()
      assertEquals(PagingState.Success(100), pager.pagingState.value)
      diagnostics.clear()
      readFailure = failure
      pager.loadFirstPage()
      assertEquals(
        PagingState.Error(FetchBeersError.Unknown(failure), true),
        pager.pagingState.value,
      )
      assertEquals(listOf("1"), pager.data.first().map { it.id })
      assertDatabaseFailure()

      readFailure = null
      beersRemoteSource.setBeersResponse(listOf(apiItem("2")), totalCount = 100)
      pager.loadNextPage()
      assertEquals(listOf(1, 1, 2), beersRemoteSource.requestedPages)
      assertEquals(PagingState.Success(100), pager.pagingState.value)
    }

  @Test
  fun `continuation cache-status and count failures are reported as database failures`() =
    runTest(testDispatcher) {
      val failure = IllegalStateException("cache read")
      listOf(true, false).forEach { failCacheStatus ->
        val failingRepository =
          object : BeersRepository by repository {
            override suspend fun catalogCacheStatus(
              policy: com.simtop.core.core.CachePolicy
            ): CatalogCacheStatus {
              if (failCacheStatus) throw failure
              return CatalogCacheStatus.Empty
            }

            override suspend fun countCatalogDBEntries(): Int = throw failure
          }
        val pager =
          BeersPagerFactoryImpl(failingRepository, languageProvider, recordingLogger).create()
        diagnostics.clear()
        pager.loadNextPage()
        assertEquals(
          PagingState.Error(FetchBeersError.Unknown(failure), false),
          pager.pagingState.value,
        )
        assertDatabaseFailure()
      }
    }

  @Test
  fun `storage cancellation escapes without diagnostics and leaves paging retryable`() =
    runTest(testDispatcher) {
      val cancellation = CancellationException("cancel")
      var cancelled = true
      val storage =
        object : BeersStorage by beersLocalSource {
          override suspend fun insertPage(
            beers: List<StoredBeer>,
            surface: String,
            nextKey: Int?,
            totalCount: Int?,
            pageKey: Int?,
          ) {
            if (cancelled) throw cancellation
            beersLocalSource.insertPage(beers, surface, nextKey, totalCount, pageKey)
          }

          override suspend fun getPagingState(surface: String): StoredPagingState? {
            if (cancelled) throw cancellation
            return beersLocalSource.getPagingState(surface)
          }
        }
      installStorage(storage)
      beersRemoteSource.setBeersResponse(listOf(apiItem("1")), totalCount = 100)
      val pager = factory.create()
      assertSame(cancellation, assertFailsWith<CancellationException> { pager.loadNextPage() })
      assertSame(cancellation, assertFailsWith<CancellationException> { pager.loadFirstPage() })
      assertEquals(emptyList(), diagnostics)
      cancelled = false
      pager.loadFirstPage()
      assertEquals(PagingState.Success(100), pager.pagingState.value)
    }

  @Test
  fun `a failing logger does not leave pagers loading or replace the original cause`() =
    runTest(testDispatcher) {
      installStorage(beersLocalSource, Logger { _, _ -> throw IllegalArgumentException("adapter") })
      val failure = IllegalStateException("original")
      beersRemoteSource.setShouldThrowError(true, failure)
      listOf(factory.create(), factory.create(BeersQuery(search = "ipa"))).forEach { pager ->
        pager.loadFirstPage()
        assertEquals(
          PagingState.Error(FetchBeersError.Unknown(failure), true),
          pager.pagingState.value,
        )
      }
    }

  private fun assertDatabaseFailure() {
    assertEquals(
      listOf(
        LogTag.BEERS_LOCAL_SOURCE to
          Diagnostic(
            DiagnosticArea.DATABASE,
            DiagnosticCode.LOCAL_STORAGE_FAILURE,
            DiagnosticLevel.ERROR,
          )
      ),
      diagnostics,
    )
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

  @Test
  fun `failed refresh over a warm catalog preserves the stored load-more bookmark`() =
    runTest(testDispatcher) {
      repository.insertPage(
        (1..75).map { Beer.empty.copy(id = "$it") },
        surface = "catalog:en",
        nextKey = 4,
        totalCount = 206,
      )
      beersRemoteSource.setShouldThrowError(true, BeersServiceNetworkException())
      val pager = factory.create()

      pager.loadFirstPage()
      expectThat(pager.pagingState.value)
        .isEqualTo(PagingState.Error(FetchBeersError.Network, isFirstPage = true))

      beersRemoteSource.setShouldThrowError(false)
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "76")), totalCount = 206)
      pager.loadNextPage()

      expectThat(beersRemoteSource.requestedPages.toList()).isEqualTo(listOf(1, 4))
      expectThat(repository.getBeerById("76")).isNotNull()
      expectThat(pager.pagingState.value).isEqualTo(PagingState.Success(totalCount = 206))
    }

  // Language switch (Paging 2.0 Phase 4): the cached rows and bookmark belong to another
  // language's surface, so "load more" must restart from page 1 and re-walk (re-translating via
  // the upsert) - the row-count estimate would skip pages 1-2 and leave them in the old language.
  @Test
  fun `load more over a language-mismatched cache restarts from page 1`() =
    runTest(testDispatcher) {
      // Two full pages cached under Spanish; the estimate would resume at page 3.
      repository.insertPage(
        (1..50).map { Beer.empty.copy(id = "$it") },
        surface = "catalog:es",
        nextKey = 3,
        totalCount = 206,
      )
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")), totalCount = 206)
      val pager = factory.create() // built for "catalog:en"

      pager.loadNextPage()

      expectThat(beersRemoteSource.requestedPages.toList()).isEqualTo(listOf(1))
    }

  @Test
  fun `rewriting catalog rows under a new language invalidates the previous bookmark`() =
    runTest(testDispatcher) {
      val translatedBeer =
        BeersApiResponseItem(
          id = "1",
          name = "Beer 1",
          translations =
            listOf(
              Translation(Language("en"), "English tagline", "English description"),
              Translation(Language("fr"), "French tagline", "French description"),
            ),
          foodPairing = emptyList(),
        )
      beersRemoteSource.setBeersResponse(listOf(translatedBeer), totalCount = 206)
      val pager = factory.create()
      pager.loadFirstPage()
      expectThat(repository.getBeerById("1")?.tagline).isEqualTo("English tagline")
      expectThat(repository.pagingNextKey("catalog:en")).isEqualTo(2)

      currentLanguage = "fr"
      pager.loadFirstPage()

      expectThat(repository.getBeerById("1")?.tagline).isEqualTo("French tagline")
      expectThat(repository.pagingNextKey("catalog:en")).isEqualTo(null)
      expectThat(repository.pagingNextKey("catalog:fr")).isEqualTo(2)
      currentLanguage = "en"
      expectThat(repository.catalogCacheStatus()).isEqualTo(CatalogCacheStatus.LanguageMismatch)
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
  fun `transport failure is classified as Network error`() =
    runTest(testDispatcher) {
      beersRemoteSource.setShouldThrowError(true, BeersServiceNetworkException())
      val pager = factory.create()

      pager.loadFirstPage()

      expectThat(pager.pagingState.value)
        .isEqualTo(PagingState.Error(FetchBeersError.Network, isFirstPage = true))
      expectThat(repository.countDBEntries()).isEqualTo(0)
      expectThat(repository.pagingNextKey("catalog:en")).isEqualTo(null)
    }

  @Test
  fun `HTTP 429 is classified as RateLimited`() =
    runTest(testDispatcher) {
      val http429 = BeersServiceHttpException(429)
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
  fun `a full page at the reported total ends pagination immediately`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(
        (1..25).map { apiItem(id = "$it") },
        totalCount = 25,
      )
      val pager = factory.create()

      pager.loadFirstPage()
      pager.loadNextPage()

      expectThat(pager.pagingState.value).isEqualTo(PagingState.EndOfPagination(totalCount = 25))
      expectThat(beersRemoteSource.requestedPages.toList()).isEqualTo(listOf(1))
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
  fun `a browse pager removes duplicate beers returned on overlapping pages`() =
    runTest(testDispatcher) {
      beersRemoteSource.setBeersResponse(listOf(apiItem(id = "1")), totalCount = 50)
      val pager = factory.create(BeersQuery(styleId = "style-1"))

      pager.loadFirstPage()
      pager.loadNextPage()

      expectThat(pager.data.first().map { it.id }).isEqualTo(listOf("1"))
      expectThat(beersRemoteSource.requestedPages.toList()).isEqualTo(listOf(1, 2))
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

  // Regression test: search/browse results are an in-memory snapshot, so a detail-screen
  // availability toggle (written to the beers table) used to be invisible when navigating back to
  // the query surface. The overlay must reflect it - including for a beer the catalog never
  // cached, which the upsert writes as a fresh row.
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
