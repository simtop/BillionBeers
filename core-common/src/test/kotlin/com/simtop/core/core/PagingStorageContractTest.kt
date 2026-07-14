package com.simtop.core.core

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The behaviour every [PagingStorage] must satisfy, regardless of where it stores pages. Concrete
 * implementations subclass this and run the whole suite, so a new paged surface (search, favorites)
 * gets the invariants for free and can't silently break them - the PR-#57 class of bug lived
 * exactly in a storage that looked fine in isolation.
 *
 * Two families exist by design and can't share one assertion for a refresh: a network-only storage
 * *replaces* its contents on [PagingStorage.storeFirstPage] (the fetched page is the whole truth),
 * while a DB-backed SSOT *merges* (upsert, never delete, so a locally-owned column survives). That
 * single difference is declared via [storeFirstPageReplaces]; everything else is shared.
 */
@ExperimentalCoroutinesApi
abstract class PagingStorageContractTest {

  /** A fresh, empty storage for each test. */
  abstract fun createStorage(): PagingStorage<Int, String>

  /** True if [PagingStorage.storeFirstPage] replaces existing contents; false if it merges them. */
  abstract val storeFirstPageReplaces: Boolean

  private fun page(vararg items: String) = PageResult<Int, String>(items.toList(), nextKey = null)

  @Test
  fun `storeFirstPage surfaces its items in data`() = runTest {
    val storage = createStorage()

    storage.storeFirstPage(page("a", "b"))

    storage.data.test { assertEquals(listOf("a", "b"), awaitItem()) }
  }

  @Test
  fun `append adds a subsequent page after the first, in order`() = runTest {
    val storage = createStorage()
    storage.storeFirstPage(page("a", "b"))

    storage.append(page("c", "d"))

    storage.data.test { assertEquals(listOf("a", "b", "c", "d"), awaitItem()) }
  }

  @Test
  fun `append never drops earlier pages`() = runTest {
    val storage = createStorage()
    storage.storeFirstPage(page("a"))
    storage.append(page("b"))

    storage.append(page("c"))

    storage.data.test { assertEquals(listOf("a", "b", "c"), awaitItem()) }
  }

  @Test
  fun `storeFirstPage over existing data replaces or merges as declared`() = runTest {
    val storage = createStorage()
    storage.storeFirstPage(page("a", "b"))

    // A refresh delivering a disjoint page - replace keeps only the new one, merge keeps both.
    storage.storeFirstPage(page("c"))

    val expected = if (storeFirstPageReplaces) listOf("c") else listOf("a", "b", "c")
    storage.data.test { assertEquals(expected, awaitItem()) }
  }
}

/** Network-only storage: [PagingStorage.storeFirstPage] replaces. */
@ExperimentalCoroutinesApi
class InMemoryPagingStorageContractTest : PagingStorageContractTest() {
  override fun createStorage() = InMemoryPagingStorage<Int, String>()

  override val storeFirstPageReplaces = true
}

/**
 * A minimal upsert-by-value storage standing in for a DB-backed SSOT: every write merges instead of
 * replacing, mirroring the beers cache's "refresh never deletes" policy. The real Room storage's
 * merge is covered end-to-end by BeersPagerFactoryImplTest (composition) and the instrumented DAO
 * test; this proves the *contract itself* holds for the merging family.
 */
@ExperimentalCoroutinesApi
class MergingPagingStorageContractTest : PagingStorageContractTest() {
  override fun createStorage() =
    object : PagingStorage<Int, String> {
      private val items = MutableStateFlow<List<String>>(emptyList())
      override val data = items

      private fun upsert(page: List<String>) = items.update { current ->
        current + page.filterNot(current::contains)
      }

      override suspend fun storeFirstPage(page: PageResult<Int, String>) = upsert(page.items)

      override suspend fun append(page: PageResult<Int, String>) = upsert(page.items)
    }

  override val storeFirstPageReplaces = false
}
