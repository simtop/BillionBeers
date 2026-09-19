package com.simtop.beer_storage.browser

import com.simtop.beer_storage.api.StoredBeer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class IndexedDbBeersStorageBrowserTest {
  @Test
  fun committedRowsSurviveCloseAndReopen() = runTest {
    val databaseName = "billionbeers-test-${hashCode()}"
    val beer = StoredBeer(
      id = "browser-1",
      name = "Browser Lager",
      tagline = "Durable",
      description = "IndexedDB",
      imageUrl = "https://example.test/browser.png",
      abv = 4.8,
      ibu = 20.0,
      foodPairing = listOf("chips"),
      availability = false,
      isFavorite = true,
    )

    val first = IndexedDbBeersStorage(databaseName)
    first.insertAll(listOf(beer))
    first.insertAll(listOf(beer.copy(name = "Updated Lager", availability = true, isFavorite = false)))
    first.insertPage(listOf(beer.copy(name = "Updated Lager", availability = true, isFavorite = false)), surface = "catalog", nextKey = 2, totalCount = 1)
    first.close()

    val expected = beer.copy(name = "Updated Lager")
    val reopened = IndexedDbBeersStorage(databaseName)
    assertEquals(listOf(expected), reopened.observeBeers().first())
    assertEquals(expected, reopened.observeFavoriteBeers().first().single())
    assertEquals(2, reopened.getPagingState("catalog")?.nextKey)
    assertEquals(1, reopened.getPagingState("catalog")?.totalCount)
    assertTrue(reopened.count() == 1)
    reopened.deleteAll()
    reopened.close()
  }

  @Test
  fun fieldUpdatesPreserveTheOtherLocalFlagAndCatalogFields() = runTest {
    val databaseName = "billionbeers-field-${hashCode()}"
    val stored = StoredBeer(
      id = "field-1",
      name = "Current Name",
      tagline = "Current",
      description = "Current description",
      imageUrl = "https://example.test/current.png",
      abv = 5.0,
      ibu = 30.0,
      foodPairing = listOf("food"),
      availability = true,
      isFavorite = true,
    )
    val storage = IndexedDbBeersStorage(databaseName)
    storage.insertAll(listOf(stored))

    storage.upsertAvailability(stored.copy(name = "Stale Name", availability = false, isFavorite = false))
    assertEquals(stored.copy(availability = false), storage.observeBeers().first().single())

    storage.upsertFavorite(stored.copy(name = "Another Stale Name", availability = true, isFavorite = false))
    assertEquals(stored.copy(availability = false, isFavorite = false), storage.observeBeers().first().single())

    storage.deleteAll()
    storage.close()
  }
}
