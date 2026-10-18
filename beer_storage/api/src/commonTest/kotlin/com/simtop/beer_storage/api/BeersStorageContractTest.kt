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
  fun `get beer by id returns matching cached row or null`() = runTest {
    val storage = InMemoryBeersStorage()
    val target = storedBeer("2", "Bravo")
    storage.insertAll(listOf(storedBeer("1", "Alpha"), target, storedBeer("3", "Charlie")))

    assertEquals(target, storage.getBeerById("2"))
    assertEquals(null, storage.getBeerById("missing"))
  }

  @Test
  fun `detail cache preserves local flags catalog membership and paging state`() = runTest {
    val storage = InMemoryBeersStorage()
    val catalogBeer = storedBeer("catalog", "Catalog").copy(availability = false, isFavorite = true)
    storage.insertPage(listOf(catalogBeer), "catalog:en", nextKey = 4, totalCount = 100)
    val bookmark = storage.getPagingState("catalog:en")
    val refreshedCatalogBeer = catalogBeer.copy(name = "Updated", availability = true, isFavorite = false)
    val queryOnlyBeer = storedBeer("query", "Query")

    storage.cacheBeersForDetail(listOf(refreshedCatalogBeer, queryOnlyBeer))

    assertEquals(
      refreshedCatalogBeer.copy(availability = false, isFavorite = true),
      storage.getBeerById("catalog"),
    )
    assertEquals(queryOnlyBeer, storage.getBeerById("query"))
    assertEquals(listOf("catalog"), storage.observeCatalogBeers().first().map(StoredBeer::id))
    assertEquals(bookmark, storage.getPagingState("catalog:en"))
  }

  @Test
  fun `paging duplicate rows keep first local flags and last catalog fields`() = runTest {
    val storage = InMemoryBeersStorage()
    val first = storedBeer("1", "First").copy(availability = false, isFavorite = true)
    val last = first.copy(name = "Last", availability = true, isFavorite = false)

    storage.insertPage(listOf(first, last), "catalog:en", nextKey = 2, totalCount = 2)

    assertEquals(last.copy(availability = first.availability, isFavorite = first.isFavorite), storage.observeBeers().first().single())
  }

  @Test
  fun `paging bookmark merges next key monotonically and retains total count`() = runTest {
    val storage = InMemoryBeersStorage()

    storage.insertPage(listOf(storedBeer("1", "One")), "catalog:en", nextKey = 4, totalCount = 206)
    storage.insertPage(listOf(storedBeer("1", "One")), "catalog:en", nextKey = 2, totalCount = null)

    assertEquals(
      StoredPagingState("catalog:en", nextKey = 4, totalCount = 206, refreshedAt = 0L),
      storage.getPagingState("catalog:en"),
    )
  }

  @Test
  fun `terminal page records completion and later refresh can reopen a grown catalog`() = runTest {
    val storage = InMemoryBeersStorage()
    storage.insertPage(listOf(storedBeer("1", "One")), "catalog:en", nextKey = 2, totalCount = 50)
    storage.insertPage(listOf(storedBeer("2", "Two")), "catalog:en", nextKey = null, totalCount = 50)

    assertEquals(true, storage.getPagingState("catalog:en")?.isComplete)
    assertEquals(2, storage.getPagingState("catalog:en")?.nextKey)

    storage.insertPage(listOf(storedBeer("1", "One")), "catalog:en", nextKey = 2, totalCount = 60)

    assertEquals(false, storage.getPagingState("catalog:en")?.isComplete)
    assertEquals(2, storage.getPagingState("catalog:en")?.nextKey)
  }

  @Test
  fun `catalog rewrite invalidates bookmarks for other languages`() = runTest {
    val storage = InMemoryBeersStorage()
    storage.insertPage(emptyList(), "catalog:en", nextKey = 4, totalCount = 206)
    storage.insertPage(emptyList(), "catalog:fr", nextKey = 2, totalCount = 206)

    assertEquals(null, storage.getPagingState("catalog:en"))
    assertEquals(2, storage.getPagingState("catalog:fr")?.nextKey)
    assertEquals(1, storage.countPagingStates())
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
    private val catalogBeerIds = mutableSetOf<String>()
    private val presets = MutableStateFlow<List<StoredFilterPreset>>(emptyList())
    private val states = mutableMapOf<String, StoredPagingState>()

    override fun observeBeers(): Flow<List<StoredBeer>> = beers

    override suspend fun getBeerById(id: String): StoredBeer? = beers.value.firstOrNull { it.id == id }

    override fun observeCatalogBeers(): Flow<List<StoredBeer>> =
      beers.map { rows -> rows.filter { it.id in catalogBeerIds } }

    override fun observeFavoriteBeers(): Flow<List<StoredBeer>> =
      beers.map { rows -> rows.filter { it.isFavorite }.sortedWith(compareBy({ it.name }, { it.id })) }

    override fun observeSavedFilterPresets(): Flow<List<StoredFilterPreset>> = presets

    override suspend fun saveFilterPreset(preset: StoredFilterPreset) {
      val rows = presets.value.toMutableList()
      val index = rows.indexOfFirst { it.id == preset.id }
      if (index < 0) require(rows.size < MAX_STORED_FILTER_PRESETS)
      if (index < 0) rows += preset else rows[index] = preset
      presets.value = rows.sortedWith(compareByDescending<StoredFilterPreset> { it.updatedAt }.thenBy { it.name }.thenBy { it.id })
    }

    override suspend fun renameFilterPreset(id: String, name: String, updatedAt: Long) {
      presets.value = presets.value.map { if (it.id == id) it.copy(name = name, updatedAt = updatedAt) else it }
    }

    override suspend fun deleteFilterPreset(id: String) {
      presets.value = presets.value.filterNot { it.id == id }
    }

    override suspend fun insertAll(beers: List<StoredBeer>) {
      upsert(beers, isCatalog = true)
    }

    override suspend fun cacheBeersForDetail(beers: List<StoredBeer>) {
      upsert(beers, isCatalog = false)
    }

    private fun upsert(beers: List<StoredBeer>, isCatalog: Boolean) {
      val rows = this.beers.value.toMutableList()
      beers.forEach { incoming ->
        val index = rows.indexOfFirst { it.id == incoming.id }
        if (index < 0) rows += incoming
        else rows[index] = incoming.copy(availability = rows[index].availability, isFavorite = rows[index].isFavorite)
        if (isCatalog) catalogBeerIds += incoming.id
      }
      this.beers.value = rows
    }

    override suspend fun insertPage(
      beers: List<StoredBeer>,
      surface: String,
      nextKey: Int?,
      totalCount: Int?,
      pageKey: Int?,
    ) {
      if (surface.startsWith("catalog:")) {
        states.keys.filter { it.startsWith("catalog:") && it != surface }.forEach(states::remove)
      }
      upsert(beers, isCatalog = surface.startsWith("catalog:"))
      val old = states[surface]
      states[surface] =
        StoredPagingState(
          surface,
          nextKey =
            if (old?.isComplete == true) nextKey ?: old.nextKey
            else listOfNotNull(old?.nextKey, nextKey).maxOrNull(),
          totalCount = totalCount ?: old?.totalCount,
          refreshedAt = 0L,
          isComplete = nextKey == null || beers.isEmpty(),
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
