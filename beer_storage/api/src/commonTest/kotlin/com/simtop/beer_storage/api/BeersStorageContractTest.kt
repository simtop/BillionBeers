package com.simtop.beer_storage.api

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

class BeersStorageContractTest {

  @Test
  fun `catalog refresh preserves local flags and favorite flow is ordered`() = runTest {
    val storage = InMemoryBeersStorage()
    val cached = storedBeer("2", "Bravo").copy(availability = false, isFavorite = true)
    storage.insertAll(listOf(cached))

    storage.insertAll(
      listOf(
        cached.copy(name = "After", availability = true, isFavorite = false),
        storedBeer("1", "Alpha").copy(isFavorite = true),
      )
    )

    assertEquals(cached.copy(name = "After"), storage.observeBeers().first().single { it.id == "2" })
    storage.observeFavoriteBeers().test {
      assertEquals(listOf("After", "Alpha"), awaitItem().map(StoredBeer::name))
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `paging bookmark merges next key monotonically and retains total count`() = runTest {
    val storage = InMemoryBeersStorage()

    storage.insertPage(listOf(storedBeer("1", "One")), "catalog:en", nextKey = 4, totalCount = 206)
    storage.insertPage(emptyList(), "catalog:en", nextKey = 2, totalCount = null)

    assertEquals(
      StoredPagingState("catalog:en", nextKey = 4, totalCount = 206, refreshedAt = 0L),
      storage.getPagingState("catalog:en"),
    )
  }

  private fun storedBeer(id: String, name: String) =
    StoredBeer(
      id = id,
      name = name,
      tagline = "",
      description = "",
      imageUrl = "",
      abv = 0.0,
      ibu = 0.0,
      foodPairing = emptyList(),
    )

  private class InMemoryBeersStorage : BeersStorage {
    private val beers = MutableStateFlow<List<StoredBeer>>(emptyList())
    private val states = mutableMapOf<String, StoredPagingState>()

    override fun observeBeers(): Flow<List<StoredBeer>> = beers

    override fun observeFavoriteBeers(): Flow<List<StoredBeer>> =
      beers.map { rows -> rows.filter { it.isFavorite }.sortedWith(compareBy({ it.name }, { it.id })) }

    override suspend fun insertAll(beers: List<StoredBeer>) {
      val rows = this.beers.value.toMutableList()
      beers.forEach { incoming ->
        val index = rows.indexOfFirst { it.id == incoming.id }
        if (index < 0) rows += incoming
        else rows[index] = incoming.copy(availability = rows[index].availability, isFavorite = rows[index].isFavorite)
      }
      this.beers.value = rows
    }

    override suspend fun insertPage(
      beers: List<StoredBeer>,
      surface: String,
      nextKey: Int?,
      totalCount: Int?,
    ) {
      insertAll(beers)
      val old = states[surface]
      states[surface] =
        StoredPagingState(
          surface,
          nextKey = listOfNotNull(old?.nextKey, nextKey).maxOrNull(),
          totalCount = totalCount ?: old?.totalCount,
          refreshedAt = 0L,
        )
    }

    override suspend fun getPagingState(surface: String): StoredPagingState? = states[surface]

    override suspend fun countPagingStates(): Int = states.size

    override suspend fun upsertAvailability(beer: StoredBeer) {
      val existing = this.beers.value.firstOrNull { it.id == beer.id }
      insertAll(listOf(existing?.copy(availability = beer.availability) ?: beer))
    }

    override suspend fun upsertFavorite(beer: StoredBeer) {
      val existing = this.beers.value.firstOrNull { it.id == beer.id }
      insertAll(listOf(existing?.copy(isFavorite = beer.isFavorite) ?: beer))
    }

    override suspend fun deleteAll() {
      beers.value = emptyList()
      states.clear()
    }

    override suspend fun count(): Int = beers.value.size
  }
}
