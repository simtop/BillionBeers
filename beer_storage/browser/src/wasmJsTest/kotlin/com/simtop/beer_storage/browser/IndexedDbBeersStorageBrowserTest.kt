@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.simtop.beer_storage.browser

import com.simtop.beer_storage.api.FilterPresetCapacityReachedException
import com.simtop.beer_storage.api.MAX_STORED_FILTER_PRESETS
import com.simtop.beer_storage.api.StoredBeer
import com.simtop.beer_storage.api.StoredFilterPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class IndexedDbBeersStorageBrowserTest {
  @Test
  fun getBeerByIdUsesTheStoredPrimaryKey() = runTest {
    val storage = IndexedDbBeersStorage("billionbeers-beer-by-id-${hashCode()}")
    try {
      storage.awaitReady()
      val beers = listOf(storedBeer("1"), storedBeer("42"), storedBeer("99"))
      storage.insertAll(beers)

      assertEquals(beers[1], storage.getBeerById("42"))
      assertEquals(null, storage.getBeerById("missing"))
    } finally {
      storage.close()
    }
  }

  @Test
  fun cacheBeersForDetailPreservesFlagsMembershipPositionsAndBookmarkAcrossReopen() = runTest {
    val databaseName = "billionbeers-detail-cache-${hashCode()}"
    var storage = IndexedDbBeersStorage(databaseName)
    try {
      storage.awaitReady()
      val first = storedBeer("catalog-first").copy(name = "First", availability = false, isFavorite = true)
      val second = storedBeer("catalog-second").copy(name = "Second")
      storage.insertPage(listOf(first, second), "catalog:en", nextKey = 3, totalCount = 50, pageKey = 2)
      val bookmark = storage.getPagingState("catalog:en")

      storage.cacheBeersForDetail(
        listOf(
          first.copy(name = "Updated", availability = true, isFavorite = false),
          storedBeer("query-only"),
        )
      )

      assertEquals(first.copy(name = "Updated"), storage.getBeerById("catalog-first"))
      assertEquals(listOf("catalog-first", "catalog-second"), storage.observeCatalogBeers().first().map(StoredBeer::id))
      assertEquals(bookmark, storage.getPagingState("catalog:en"))
      assertEquals("query-only", storage.getBeerById("query-only")?.id)

      storage.close()
      storage = IndexedDbBeersStorage(databaseName)
      storage.awaitReady()
      assertEquals("query-only", storage.getBeerById("query-only")?.id)
      assertEquals(listOf("catalog-first", "catalog-second"), storage.observeCatalogBeers().first().map(StoredBeer::id))
      assertEquals(bookmark, storage.getPagingState("catalog:en"))
    } finally {
      storage.deleteAll()
      storage.close()
    }
  }

  @Test
  fun capacityRejectionPreservesDurablePresetsAndAllowsUpdateAndRetry() = runTest {
    val databaseName = "billionbeers-presets-capacity-${hashCode()}"
    var writer = IndexedDbBeersStorage(databaseName)
    val peer = IndexedDbBeersStorage(databaseName)
    val presets = (0 until MAX_STORED_FILTER_PRESETS).map { index ->
      StoredFilterPreset("preset-$index", "Preset $index", "beer $index", null, null, index.toLong())
    }
    val added = StoredFilterPreset("added", "Lagers", "lager", null, "brewery-1", 11L)
    try {
      presets.forEach { writer.saveFilterPreset(it) }
      val before = presets.sortedByDescending { it.updatedAt }
      assertEquals(before, peer.observeSavedFilterPresets().first { it == before })

      assertFailsWith<FilterPresetCapacityReachedException> { writer.saveFilterPreset(added) }
      assertEquals(before, writer.observeSavedFilterPresets().first())
      assertEquals(before, peer.observeSavedFilterPresets().first())
      assertReopenedPresets(databaseName, before)

      val updated = presets.first().copy(name = "Updated", updatedAt = 20L)
      writer.saveFilterPreset(updated)
      writer.deleteFilterPreset(presets.last().id)
      writer.saveFilterPreset(added)
      val expected = (presets.drop(1).dropLast(1) + updated + added).sortedByDescending { it.updatedAt }
      assertEquals(expected, writer.observeSavedFilterPresets().first())
      assertEquals(expected, peer.observeSavedFilterPresets().first { it == expected })
      writer.close()
      writer = IndexedDbBeersStorage(databaseName)
      assertEquals(expected, writer.observeSavedFilterPresets().first())
    } finally {
      clearPresetsAndClose(writer, peer)
    }
  }

  @Test
  fun concurrentLogicalPresetSavesFromSeparateInstancesCoalesceAtomically() = runTest {
    val databaseName = "billionbeers-presets-concurrent-${hashCode()}"
    val firstWriter = IndexedDbBeersStorage(databaseName)
    val secondWriter = IndexedDbBeersStorage(databaseName)
    val first = StoredFilterPreset("candidate-1", "IPAs", "ipa", "style-1", null, 2L)
    val second = first.copy(id = "candidate-2", updatedAt = 3L)
    try {
      firstWriter.awaitReady()
      secondWriter.awaitReady()
      listOf(
        async { firstWriter.saveFilterPreset(first) },
        async { secondWriter.saveFilterPreset(second) },
      ).awaitAll()
      val sameNameDifferentQuery = first.copy(id = "candidate-3", styleId = "style-2")
      firstWriter.saveFilterPreset(sameNameDifferentQuery)

      val reopened = IndexedDbBeersStorage(databaseName)
      try {
        reopened.awaitReady()
        val saved = reopened.observeSavedFilterPresets().first()
        assertEquals(2, saved.size)
        val coalesced = saved.single { it.styleId == first.styleId }
        assertEquals("IPAs", coalesced.name)
        assertEquals("ipa", coalesced.search)
        assertEquals(null, coalesced.breweryId)
        assertTrue(coalesced.id in setOf(first.id, second.id))
        assertTrue(coalesced.updatedAt in setOf(first.updatedAt, second.updatedAt))
        assertEquals(sameNameDifferentQuery, saved.single { it.styleId == "style-2" })
      } finally {
        reopened.close()
      }
    } finally {
      clearPresetsAndClose(firstWriter, secondWriter)
    }
  }

  @Test
  fun logicalDuplicateAtCapacityKeepsExistingIdAndDoesNotConsumeAnotherSlot() = runTest {
    val databaseName = "billionbeers-presets-duplicate-capacity-${hashCode()}"
    val writer = IndexedDbBeersStorage(databaseName)
    val peer = IndexedDbBeersStorage(databaseName)
    val presets = (0 until MAX_STORED_FILTER_PRESETS).map { index ->
      StoredFilterPreset("preset-$index", "Preset $index", "beer $index", null, null, index.toLong())
    }
    val duplicate = presets.first().copy(id = "new-candidate", updatedAt = 20L)
    try {
      presets.forEach { writer.saveFilterPreset(it) }
      writer.saveFilterPreset(duplicate)

      val saved = writer.observeSavedFilterPresets().first()
      assertEquals(MAX_STORED_FILTER_PRESETS, saved.size)
      assertTrue(saved.none { it.id == duplicate.id })
      assertEquals(duplicate.copy(id = presets.first().id), saved.single { it.id == presets.first().id })
    } finally {
      clearPresetsAndClose(writer, peer)
    }
  }

  @Test
  fun abortedPresetSavePreservesDurableRowsAndAllowsRetry() = runTest {
    val added = StoredFilterPreset("added", "Lagers", "lager", null, "brewery-1", 2L)
    assertPresetAbortRecovery("save", { listOf(added, it) }) { it.saveFilterPreset(added) }
  }

  @Test
  fun abortedPresetRenamePreservesDurableRowsAndAllowsRetry() = runTest {
    assertPresetAbortRecovery("rename", { listOf(it.copy(name = "Renamed", updatedAt = 3L)) }) {
      it.renameFilterPreset("initial", "Renamed", 3L)
    }
  }

  @Test
  fun abortedPresetDeletePreservesDurableRowsAndAllowsRetry() = runTest {
    assertPresetAbortRecovery("delete", { emptyList() }) { it.deleteFilterPreset("initial") }
  }

  private suspend fun assertPresetAbortRecovery(
    operation: String,
    expectedAfter: (StoredFilterPreset) -> List<StoredFilterPreset>,
    mutate: suspend (IndexedDbBeersStorage) -> Unit,
  ) {
    val databaseName = "billionbeers-presets-abort-$operation-${hashCode()}"
    var writer = IndexedDbBeersStorage(databaseName)
    val peer = IndexedDbBeersStorage(databaseName)
    val initial = StoredFilterPreset("initial", "IPAs", "ipa", "style-1", null, 1L)
    try {
      writer.saveFilterPreset(initial)
      assertEquals(listOf(initial), peer.observeSavedFilterPresets().first { it == listOf(initial) })
      installStorageProbe()
      try {
        // Abort a real transaction after its mutation request succeeds but before commit.
        abortNextPresetMutation()
        assertFailsWith<Throwable> { mutate(writer) }
        assertEquals(1, storageProbePresetAbortCount())
        assertEquals(listOf(initial), writer.observeSavedFilterPresets().first())
        assertEquals(listOf(initial), peer.observeSavedFilterPresets().first())
      } finally {
        finishStorageProbe()
      }
      assertReopenedPresets(databaseName, listOf(initial))

      mutate(writer)
      val expected = expectedAfter(initial)
      assertEquals(expected, writer.observeSavedFilterPresets().first())
      assertEquals(expected, peer.observeSavedFilterPresets().first { it == expected })
      writer.close()
      writer = IndexedDbBeersStorage(databaseName)
      assertEquals(expected, writer.observeSavedFilterPresets().first())
    } finally {
      clearPresetsAndClose(writer, peer)
    }
  }

  private fun storedBeer(id: String) =
    StoredBeer(
      id = id,
      name = "Beer $id",
      tagline = "",
      description = "",
      imageUrl = "",
      abv = 0.0,
      ibu = 0.0,
      foodPairing = emptyList(),
    )

  private suspend fun clearPresetsAndClose(writer: IndexedDbBeersStorage, peer: IndexedDbBeersStorage) {
    peer.close()
    try {
      writer.observeSavedFilterPresets().first().forEach { writer.deleteFilterPreset(it.id) }
    } finally {
      writer.close()
    }
  }

  private suspend fun assertReopenedPresets(databaseName: String, expected: List<StoredFilterPreset>) {
    val reopened = IndexedDbBeersStorage(databaseName)
    try {
      assertEquals(expected, reopened.observeSavedFilterPresets().first())
    } finally {
      reopened.close()
    }
  }

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
    first.insertPage(listOf(beer.copy(name = "Updated Lager", availability = true, isFavorite = false)), surface = "catalog:en", nextKey = 2, totalCount = 1)
    first.close()

    val expected = beer.copy(name = "Updated Lager")
    val reopened = IndexedDbBeersStorage(databaseName)
    assertEquals(listOf(expected), reopened.observeBeers().first())
    assertEquals(expected, reopened.observeFavoriteBeers().first().single())
    assertEquals(2, reopened.getPagingState("catalog:en")?.nextKey)
    assertEquals(1, reopened.getPagingState("catalog:en")?.totalCount)
    assertTrue(reopened.count() == 1)
    reopened.deleteAll()
    reopened.close()
  }

  @Test
  fun filterPresetsSurviveReopenAndPeerInvalidation() = runTest {
    val databaseName = "billionbeers-presets-${hashCode()}"
    val writer = IndexedDbBeersStorage(databaseName)
    val peer = IndexedDbBeersStorage(databaseName)
    val preset = StoredFilterPreset("preset-1", "IPAs", "ipa", "style-1", null, 1L)

    writer.saveFilterPreset(preset)
    assertEquals(listOf(preset), peer.observeSavedFilterPresets().first { it == listOf(preset) })
    writer.renameFilterPreset(preset.id, "Updated IPAs", 2L)
    val renamed = preset.copy(name = "Updated IPAs", updatedAt = 2L)
    assertEquals(listOf(renamed), peer.observeSavedFilterPresets().first { it == listOf(renamed) })

    writer.close()
    peer.close()
    val reopened = IndexedDbBeersStorage(databaseName)
    assertEquals(listOf(renamed), reopened.observeSavedFilterPresets().first())
    reopened.deleteFilterPreset(preset.id)
    assertTrue(reopened.observeSavedFilterPresets().first().isEmpty())
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
      surface = "catalog:en",
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
    assertEquals(5, storage.getPagingState("catalog:en")?.nextKey)
    assertEquals(10, storage.getPagingState("catalog:en")?.totalCount)

    storage.insertPage(
      listOf(existingLast, newLast),
      surface = "catalog:en",
      nextKey = 3,
      totalCount = null,
    )
    assertEquals(5, storage.getPagingState("catalog:en")?.nextKey)
    assertEquals(10, storage.getPagingState("catalog:en")?.totalCount)

    storage.close()
    val reopened = IndexedDbBeersStorage(databaseName)
    assertEquals(2, reopened.count())
    assertEquals(
      listOf(existingLast.copy(availability = existing.availability, isFavorite = existing.isFavorite)),
      reopened.observeFavoriteBeers().first(),
    )
    assertEquals(5, reopened.getPagingState("catalog:en")?.nextKey)
    assertEquals(10, reopened.getPagingState("catalog:en")?.totalCount)
    reopened.deleteAll()
    reopened.close()
  }

  @Test
  fun catalogPreservesPageOrderAcrossAlphabeticalNamesAndReopen() = runTest {
    val databaseName = "billionbeers-catalog-order-${hashCode()}"
    fun beer(id: String, name: String) =
      StoredBeer(
        id = id,
        name = name,
        tagline = "",
        description = "",
        imageUrl = "",
        abv = 4.5,
        ibu = 18.0,
        foodPairing = emptyList(),
      )

    val firstPage = listOf(beer("first-z", "Zulu"), beer("first-a", "Alpha"))
    val secondPage = beer("second-b", "Bravo")
    val storage = IndexedDbBeersStorage(databaseName)
    storage.insertPage(
      firstPage,
      surface = "catalog:en",
      nextKey = 2,
      totalCount = 3,
      pageKey = 1,
    )
    storage.insertPage(
      listOf(secondPage),
      surface = "catalog:en",
      nextKey = null,
      totalCount = 3,
      pageKey = 2,
    )

    val expectedCatalog = listOf("first-z", "first-a", "second-b")
    assertEquals(expectedCatalog, storage.observeCatalogBeers().first().map { it.id })
    assertEquals(
      listOf("Alpha", "Bravo", "Zulu"),
      storage.observeBeers().first().map { it.name },
    )
    storage.close()

    val reopened = IndexedDbBeersStorage(databaseName)
    assertEquals(expectedCatalog, reopened.observeCatalogBeers().first().map { it.id })
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

    storage.insertPage(listOf(beer), surface = "catalog:en", nextKey = 2, totalCount = 1)

    assertEquals(listOf(beer), storage.observeBeers().first())
    assertEquals(listOf(beer), storage.observeFavoriteBeers().first())
    assertEquals(2, storage.getPagingState("catalog:en")?.nextKey)
    assertEquals(1, storage.count())
    assertEquals(1, storage.countCatalogEntries())
    assertEquals(listOf(beer), storage.observeCatalogBeers().first())
    storage.deleteAll()
    storage.close()
  }

  @Test
  fun uncachedFavoriteDoesNotEnterCatalogUntilFetched() = runTest {
    val storage = IndexedDbBeersStorage("billionbeers-favorite-catalog-${hashCode()}")
    val favorite = StoredBeer(
      id = "favorite-only",
      name = "Favorite Only",
      tagline = "",
      description = "",
      imageUrl = "",
      abv = 4.0,
      ibu = 20.0,
      foodPairing = emptyList(),
      isFavorite = true,
    )

    storage.upsertFavorite(favorite)

    assertEquals(listOf(favorite), storage.observeBeers().first())
    assertEquals(listOf(favorite), storage.observeFavoriteBeers().first())
    assertEquals(emptyList(), storage.observeCatalogBeers().first())
    assertEquals(0, storage.countCatalogEntries())

    storage.insertPage(listOf(favorite.copy(isFavorite = false)), "catalog:en", 2, 1)

    assertEquals(listOf(favorite), storage.observeCatalogBeers().first())
    assertEquals(1, storage.countCatalogEntries())
    storage.deleteAll()
    storage.close()
  }

  @Test
  fun pageInsertInvalidatesBookmarksForOtherCatalogLanguages() = runTest {
    val databaseName = "billionbeers-page-language-${hashCode()}"
    val storage = IndexedDbBeersStorage(databaseName)
    val beer = StoredBeer(
      id = "language-1",
      name = "French Lager",
      tagline = "",
      description = "",
      imageUrl = "",
      abv = 5.0,
      ibu = 20.0,
      foodPairing = emptyList(),
    )
    storage.insertPage(emptyList(), surface = "catalog:en", nextKey = 4, totalCount = 206)

    storage.insertPage(listOf(beer), surface = "catalog:fr", nextKey = 2, totalCount = 206)

    assertEquals(null, storage.getPagingState("catalog:en"))
    assertEquals(2, storage.getPagingState("catalog:fr")?.nextKey)
    assertEquals(1, storage.countPagingStates())
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
  fun blockedOpenCannotSettleAfterLateSuccess() = runTest {
    val databaseName = "billionbeers-blocked-open-${hashCode()}"
    installBlockedOpenProbe()
    val storage = IndexedDbBeersStorage(databaseName)
    try {
      val ready = async { assertFailsWith<Throwable> { storage.awaitReady() } }
      yield()
      triggerBlockedOpenProbe()
      yield()
      triggerLateBlockedOpenSuccess()
      ready.await()
      assertEquals(1, blockedOpenProbeCloseCount())
    } finally {
      storage.close()
      finishBlockedOpenProbe()
    }
  }

  @Test
  fun synchronousTransactionSetupFailureClosesDatabase() = runTest {
    val databaseName = "billionbeers-transaction-failure-${hashCode()}"
    installStorageProbe()
    val storage = runCatching {
      failNextStorageTransactionSetup()
      IndexedDbBeersStorage(databaseName)
    }.getOrThrow()
    try {
      assertFailsWith<Throwable> { storage.awaitReady() }
      assertTrue(storageProbeCloseCount() >= 1)
    } finally {
      storage.close()
      finishStorageProbe()
    }
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
