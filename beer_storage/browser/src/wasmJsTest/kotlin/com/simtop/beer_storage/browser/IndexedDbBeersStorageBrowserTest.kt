package com.simtop.beer_storage.browser

import com.simtop.beer_storage.api.StoredBeer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
  fun insertPageDeduplicatesRowsPreservesFlagsAndRetainsPagingState() = runTest {
    val databaseName = "billionbeers-page-duplicate-${hashCode()}"
    val existing = StoredBeer(
      id = "page-existing",
      name = "Existing Before",
      tagline = "Before",
      description = "Before description",
      imageUrl = "https://example.test/before.png",
      abv = 4.0,
      ibu = 10.0,
      foodPairing = listOf("before food"),
      availability = false,
      isFavorite = true,
    )
    val existingFirst = existing.copy(
      name = "Existing First",
      tagline = "First",
      description = "First description",
      imageUrl = "https://example.test/first.png",
      foodPairing = listOf("first food"),
      availability = true,
      isFavorite = false,
    )
    val existingLast = existing.copy(
      name = "Existing Last",
      tagline = "Last",
      description = "Last description",
      imageUrl = "https://example.test/last.png",
      foodPairing = listOf("last food"),
      availability = true,
      isFavorite = false,
    )
    val newFirst = existing.copy(
      id = "page-new",
      name = "New First",
      tagline = "New first",
      description = "New first description",
      imageUrl = "https://example.test/new-first.png",
      availability = true,
      isFavorite = false,
    )
    val newLast = newFirst.copy(
      name = "New Last",
      tagline = "New last",
      description = "New last description",
      imageUrl = "https://example.test/new-last.png",
      foodPairing = listOf("new last food"),
      availability = false,
      isFavorite = true,
    )
    val storage = IndexedDbBeersStorage(databaseName)
    storage.insertAll(listOf(existing))

    storage.insertPage(
      listOf(existingFirst, existingLast, newFirst, newLast),
      surface = "catalog",
      nextKey = 5,
      totalCount = 10,
    )

    assertEquals(
      existingLast.copy(availability = existing.availability, isFavorite = existing.isFavorite),
      storage.observeBeers().first().single { it.id == existing.id },
    )
    assertEquals(
      newLast.copy(availability = newFirst.availability, isFavorite = newFirst.isFavorite),
      storage.observeBeers().first().single { it.id == newFirst.id },
    )
    assertEquals(2, storage.count())
    assertEquals(5, storage.getPagingState("catalog")?.nextKey)
    assertEquals(10, storage.getPagingState("catalog")?.totalCount)

    storage.insertPage(
      listOf(existingLast, newLast),
      surface = "catalog",
      nextKey = 3,
      totalCount = null,
    )
    assertEquals(5, storage.getPagingState("catalog")?.nextKey)
    assertEquals(10, storage.getPagingState("catalog")?.totalCount)

    storage.close()
    val reopened = IndexedDbBeersStorage(databaseName)
    assertEquals(2, reopened.count())
    assertEquals(
      listOf(existingLast.copy(availability = existing.availability, isFavorite = existing.isFavorite)),
      reopened.observeFavoriteBeers().first(),
    )
    assertEquals(5, reopened.getPagingState("catalog")?.nextKey)
    assertEquals(10, reopened.getPagingState("catalog")?.totalCount)
    reopened.deleteAll()
    reopened.close()
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

  @Test
  fun concurrentMutationsSerializeAndRemainDurable() = runTest {
    val databaseName = "billionbeers-concurrent-${hashCode()}"
    val existing = StoredBeer(
      id = "concurrent-existing",
      name = "Concurrent Existing",
      tagline = "Seed",
      description = "Seed row",
      imageUrl = "https://example.test/concurrent-existing.png",
      abv = 4.5,
      ibu = 18.0,
      foodPairing = listOf("pretzels"),
      availability = false,
      isFavorite = true,
    )
    val firstNew = existing.copy(
      id = "concurrent-first",
      name = "Concurrent First",
      availability = true,
      isFavorite = false,
    )
    val secondNew = existing.copy(
      id = "concurrent-second",
      name = "Concurrent Second",
      availability = true,
      isFavorite = false,
    )
    val storage = IndexedDbBeersStorage(databaseName)
    storage.insertAll(listOf(existing))

    listOf(
      async { storage.insertAll(listOf(firstNew)) },
      async { storage.insertAll(listOf(secondNew)) },
      async { storage.upsertAvailability(existing.copy(availability = true)) },
      async { storage.upsertFavorite(existing.copy(isFavorite = false)) },
    ).awaitAll()

    val expectedExisting = existing.copy(availability = true, isFavorite = false)
    assertEquals(
      listOf(expectedExisting, firstNew, secondNew).sortedBy { it.name },
      storage.observeBeers().first(),
    )
    assertEquals(3, storage.count())
    storage.close()

    val reopened = IndexedDbBeersStorage(databaseName)
    assertEquals(
      listOf(expectedExisting, firstNew, secondNew).sortedBy { it.name },
      reopened.observeBeers().first(),
    )
    assertTrue(reopened.observeFavoriteBeers().first().isEmpty())
    reopened.deleteAll()
    reopened.close()
  }

  @Test
  fun concurrentPeerMutationsConvergeWithoutDroppingRows() = runTest {
    val databaseName = "billionbeers-concurrent-peer-${hashCode()}"
    val firstBeer = StoredBeer(
      id = "concurrent-peer-first",
      name = "Concurrent Peer First",
      tagline = "First",
      description = "First peer row",
      imageUrl = "https://example.test/concurrent-peer-first.png",
      abv = 4.5,
      ibu = 18.0,
      foodPairing = listOf("pretzels"),
    )
    val secondBeer = firstBeer.copy(
      id = "concurrent-peer-second",
      name = "Concurrent Peer Second",
    )
    val writer = IndexedDbBeersStorage(databaseName)
    val peer = IndexedDbBeersStorage(databaseName)
    peer.observeBeers().first()

    listOf(
      async { writer.insertAll(listOf(firstBeer)) },
      async { peer.insertAll(listOf(secondBeer)) },
    ).awaitAll()

    val expected = listOf(firstBeer, secondBeer).sortedBy { it.name }
    assertEquals(expected, writer.observeBeers().first { it == expected })
    assertEquals(expected, peer.observeBeers().first { it == expected })

    writer.close()
    peer.close()
  }

  @Test
  fun closeIsIdempotentAndRejectsLaterOperations() = runTest {
    val databaseName = "billionbeers-close-contract-${hashCode()}"
    val storage = IndexedDbBeersStorage(databaseName)
    storage.observeBeers().first()

    storage.close()
    storage.close()

    assertFailsWith<IllegalStateException> {
      storage.insertAll(emptyList())
    }
    assertFailsWith<IllegalStateException> {
      storage.count()
    }
  }
}
