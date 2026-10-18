package com.simtop.benchmark.microbenchmark

import android.util.Log
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_data.repositories.BeersPagerFactoryImpl
import com.simtop.beer_data.repositories.BeersRepositoryImpl
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.localsources.BeersLocalSourceImpl
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.BeersPage
import com.simtop.beer_network.remotesources.BeersRemoteSource
import com.simtop.beer_storage.api.StoredBeer
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.core.core.Either
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.NoOpLogger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueryFlagObservationBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun unrelatedFlagUpdateWith250CachedRows() = benchmarkUnrelatedFlagUpdate(250)

    @Test
    fun unrelatedFlagUpdateWith2500CachedRows() = benchmarkUnrelatedFlagUpdate(2_500)

    @Test
    fun unrelatedFlagUpdateWith25000CachedRows() = benchmarkUnrelatedFlagUpdate(25_000)

    private fun benchmarkUnrelatedFlagUpdate(unrelatedRows: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, BeersDatabase::class.java).build()
        val language = LanguageProvider { "en" }
        val logger = NoOpLogger()
        val localSource = BeersLocalSourceImpl(database)
        val repository =
            BeersRepositoryImpl(
                beersRemoteSource = QueryRemoteSource(),
                beersStorage = localSource,
                beersMapper = BeersMapper(language, logger),
                languageProvider = language,
                logger = logger,
            )
        val pager = BeersPagerFactoryImpl(repository, language, logger)
            .create(BeersQuery(search = "benchmark"))

        try {
            runBlocking {
                Log.i(TAG, "rows=$unrelatedRows seed-start")
                withTimeout(60_000) {
                    localSource.cacheBeersForDetail((1..unrelatedRows).map(::cachedBeer))
                }
                Log.i(TAG, "rows=$unrelatedRows seed-complete")
                withTimeout(60_000) { pager.loadFirstPage() }
                Log.i(TAG, "rows=$unrelatedRows page-complete")
                val initial = withTimeout(60_000) { pager.data.first() }
                check(initial.size == QUERY_RESULT_COUNT)
                Log.i(TAG, "rows=$unrelatedRows initial-emission-complete")

                val unrelatedBeer = checkNotNull(repository.getBeerById("cached-1"))
                val emissions = Channel<List<Beer>>(Channel.UNLIMITED)
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    pager.data.collect(emissions::send)
                }
                try {
                    check(withTimeout(10_000) { emissions.receive() } == initial)
                    Log.i(TAG, "rows=$unrelatedRows collector-ready")

                    var isFavorite = unrelatedBeer.isFavorite
                    var measurementIterations = 0
                    benchmarkRule.measureRepeated {
                        runBlocking {
                            val result = repository.updateFavorite(
                                unrelatedBeer.copy(isFavorite = !isFavorite),
                            )
                            check(result == Either.Right(Unit))
                            isFavorite = !isFavorite

                            val updated = withTimeout(10_000) { emissions.receive() }
                            check(updated == initial)
                            measurementIterations++
                            if (measurementIterations == 1) {
                                Log.i(TAG, "rows=$unrelatedRows first-measurement-complete")
                            }
                        }
                    }
                    Log.i(TAG, "rows=$unrelatedRows benchmark-complete")
                } finally {
                    collector.cancelAndJoin()
                }
            }
        } finally {
            database.close()
        }
    }

    private fun cachedBeer(id: Int) =
        StoredBeer(
            id = "cached-$id",
            name = "Cached beer $id",
            tagline = "",
            description = "",
            imageUrl = "",
            abv = 5.0,
            ibu = 20.0,
            foodPairing = emptyList(),
        )

    private class QueryRemoteSource : BeersRemoteSource {
        override suspend fun getListOfBeers(
            page: Int,
            search: String?,
            typologyId: String?,
            breweryId: String?,
        ) =
            if (page == 1) {
                BeersPage(
                    items = (1..QUERY_RESULT_COUNT).map { id ->
                        BeersApiResponseItem(id = "query-$id", name = "Query beer $id")
                    },
                    totalCount = QUERY_RESULT_COUNT,
                )
            } else {
                BeersPage(items = emptyList(), totalCount = QUERY_RESULT_COUNT)
            }

        override suspend fun getTypologies() = emptyList<com.simtop.beer_network.models.TypologyApiResponseItem>()

        override suspend fun getBreweries() = emptyList<com.simtop.beer_network.models.BreweryApiResponseItem>()
    }

    private companion object {
        const val TAG = "QueryFlagBenchmark"
        const val QUERY_RESULT_COUNT = 25
    }
}
