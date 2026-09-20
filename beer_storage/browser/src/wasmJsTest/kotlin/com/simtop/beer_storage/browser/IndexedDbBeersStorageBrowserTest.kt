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
  fun liveInstancesObserveCommittedUpdatesAndDeletion() = runTest {
    val databaseName = "billionbeers-peer-${hashCode()}"
    val beer = StoredBeer(
      id = "peer-1",
      name = "Peer Lager",
      tagline = "Committed",
      description = "Broadcast",
      imageUrl = "https://example.test/peer.png",
      abv = 4.5,
      ibu = 18.0,
      foodPairing = listOf("pretzels"),
      availability = true,
      isFavorite = false,
    )
    val writer = IndexedDbBeersStorage(databaseName)
    val peer = IndexedDbBeersStorage(databaseName)
    peer.observeBeers().first()

    writer.insertAll(listOf(beer))
    assertEquals(listOf(beer), peer.observeBeers().first { it == listOf(beer) })

    writer.upsertFavorite(beer.copy(isFavorite = true))
    assertEquals(
      listOf(beer.copy(isFavorite = true)),
      peer.observeFavoriteBeers().first { it == listOf(beer.copy(isFavorite = true)) },
    )

    writer.deleteAll()
    assertTrue(peer.observeBeers().first { it.isEmpty() }.isEmpty())
    assertTrue(peer.observeFavoriteBeers().first { it.isEmpty() }.isEmpty())

    writer.close()
    peer.close()
  }

  @Test
  fun closingPeerStopsItFromParticipatingInInvalidation() = runTest {
    val databaseName = "billionbeers-peer-close-${hashCode()}"
    val beer = StoredBeer(
      id = "peer-close-1",
      name = "Closed Peer Lager",
      tagline = "Closed",
      description = "No refresh",
      imageUrl = "https://example.test/closed-peer.png",
      abv = 4.5,
      ibu = 18.0,
      foodPairing = listOf("pretzels"),
    )
    val writer = IndexedDbBeersStorage(databaseName)
    val peer = IndexedDbBeersStorage(databaseName)
    peer.observeBeers().first()
    peer.close()

    writer.insertAll(listOf(beer))
    assertEquals(listOf(beer), writer.observeBeers().first())
    writer.close()
  }

  @Test
  fun duplicateInsertRowsKeepFirstLocalFlagsAndLastCatalogFields() = runTest {
    val databaseName = "billionbeers-duplicate-${hashCode()}"
    val first = StoredBeer(
      id = "duplicate-1",
      name = "First Catalog Name",
      tagline = "First",
      description = "First description",
      imageUrl = "https://example.test/first.png",
      abv = 4.0,
      ibu = 10.0,
      foodPairing = listOf("first food"),
      availability = false,
      isFavorite = true,
    )
    val last = first.copy(
      name = "Last Catalog Name",
      tagline = "Last",
      description = "Last description",
      imageUrl = "https://example.test/last.png",
      foodPairing = listOf("last food"),
      availability = true,
      isFavorite = false,
    )
    val storage = IndexedDbBeersStorage(databaseName)

    storage.insertAll(listOf(first, last))

    assertEquals(last.copy(availability = first.availability, isFavorite = first.isFavorite), storage.observeBeers().first().single())
    storage.deleteAll()
    storage.close()
  }

  @Test
  fun insertAllPreservesFlagsForExistingRowsAndAddsNewRows() = runTest {
    val databaseName = "billionbeers-mixed-${hashCode()}"
    val existing = StoredBeer(
      id = "mixed-existing",
      name = "Existing Catalog",
      tagline = "Existing",
      description = "Existing description",
      imageUrl = "https://example.test/existing.png",
      abv = 5.0,
      ibu = 30.0,
      foodPairing = listOf("existing food"),
      availability = false,
      isFavorite = true,
    )
    val incomingExisting = existing.copy(name = "Refreshed Catalog", availability = true, isFavorite = false)
    val incomingNew = existing.copy(id = "mixed-new", name = "New Catalog", availability = true, isFavorite = false)
    val storage = IndexedDbBeersStorage(databaseName)
    storage.insertAll(listOf(existing))

    storage.insertAll(listOf(incomingExisting, incomingNew))

    assertEquals(
      listOf(incomingExisting.copy(availability = existing.availability, isFavorite = existing.isFavorite), incomingNew)
        .sortedBy { it.name },
      storage.observeBeers().first(),
    )
    storage.deleteAll()
    storage.close()
  }

  @Test
  fun pageInsertUpdatesLiveSnapshotAndPagingState() = runTest {
    val databaseName = "billionbeers-page-live-${hashCode()}"
    val beer = StoredBeer(
      id = "page-live-1",
      name = "Page Live Lager",
      tagline = "Immediate",
      description = "Committed delta",
      imageUrl = "https://example.test/page-live.png",
      abv = 4.5,
      ibu = 18.0,
      foodPairing = listOf("pretzels"),
      availability = true,
      isFavorite = true,
    )
    val storage = IndexedDbBeersStorage(databaseName)

    storage.insertPage(listOf(beer), surface = "catalog", nextKey = 2, totalCount = 1)

    assertEquals(listOf(beer), storage.observeBeers().first())
    assertEquals(listOf(beer), storage.observeFavoriteBeers().first())
    assertEquals(2, storage.getPagingState("catalog")?.nextKey)
    assertEquals(1, storage.count())
    storage.deleteAll()
    storage.close()
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
