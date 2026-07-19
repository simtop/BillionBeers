package com.simtop.core.core

import app.cash.turbine.test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@ExperimentalCoroutinesApi
class PagingMediatorTest {

  /** Storage that records every write so tests can assert on ordering and content. */
  private class RecordingStorage : PagingStorage<Int, String> {
    val events = mutableListOf<String>()
    val stored = MutableStateFlow<List<String>>(emptyList())
    var failWrites = false

    override val data: Flow<List<String>> = stored

    override suspend fun storeFirstPage(page: PageResult<Int, String>) {
      events += "storeFirstPage"
      if (failWrites) throw RuntimeException("write failed")
      stored.value = page.items
    }

    override suspend fun append(page: PageResult<Int, String>) {
      events += "append"
      if (failWrites) throw RuntimeException("write failed")
      stored.update { current -> current + page.items }
    }
  }

  /** Test harness: page N is `listOf("item N")` unless overridden or told to fail. */
  private class Harness(
    private val pages: (Int) -> List<String> = { page -> listOf("item $page") },
    private val failOn: MutableSet<Int> = mutableSetOf(),
  ) {
    val fetchedKeys = mutableListOf<Int>()
    val storage = RecordingStorage()

    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        fetchRemote = { key ->
          fetchedKeys += key
          storage.events += "fetch $key"
          if (key in failOn) throw RuntimeException("fetch $key failed")
          val items = pages(key)
          PageResult(items, nextKey = if (items.isEmpty()) null else key + 1)
        },
        classifyError = { it.message ?: "unknown" },
        storage = storage,
      )

    fun failOnKey(key: Int) = failOn.add(key)

    fun succeedOnKey(key: Int) = failOn.remove(key)
  }

  @Test
  fun `first load emits Loading then Success and stores the page`() = runTest {
    val harness = Harness()

    harness.mediator.pagingState.test {
      assertEquals(PagingState.Idle, awaitItem())

      harness.mediator.loadFirstPage()

      assertEquals(PagingState.Loading, awaitItem())
      assertEquals(PagingState.Success(), awaitItem())
    }
    assertEquals(listOf("item 1"), harness.storage.stored.value)
  }

  @Test
  fun `loadNextPage emits LoadingNextPage then Success and appends`() = runTest {
    val harness = Harness()
    harness.mediator.loadFirstPage()

    harness.mediator.pagingState.test {
      assertEquals(PagingState.Success(), awaitItem())

      harness.mediator.loadNextPage()

      assertEquals(PagingState.LoadingNextPage, awaitItem())
      assertEquals(PagingState.Success(), awaitItem())
    }
    assertEquals(listOf("item 1", "item 2"), harness.storage.stored.value)
  }

  @Test
  fun `first page failure emits Error with isFirstPage true`() = runTest {
    val harness = Harness()
    harness.failOnKey(1)

    harness.mediator.loadFirstPage()

    assertEquals(
      PagingState.Error("fetch 1 failed", isFirstPage = true),
      harness.mediator.pagingState.value,
    )
  }

  @Test
  fun `next page failure emits Error with isFirstPage false`() = runTest {
    val harness = Harness()
    harness.mediator.loadFirstPage()
    harness.failOnKey(2)

    harness.mediator.loadNextPage()

    assertEquals(
      PagingState.Error("fetch 2 failed", isFirstPage = false),
      harness.mediator.pagingState.value,
    )
  }

  @Test
  fun `next page failure also emits a one-shot LoadMoreFailed event`() = runTest {
    val harness = Harness()
    harness.mediator.loadFirstPage()
    harness.failOnKey(2)

    harness.mediator.events.test {
      harness.mediator.loadNextPage()

      assertEquals(PagingEvent.LoadMoreFailed("fetch 2 failed"), awaitItem())
    }
  }

  @Test
  fun `first page failure emits no LoadMoreFailed event`() = runTest {
    val harness = Harness()
    harness.failOnKey(1)

    harness.mediator.events.test {
      harness.mediator.loadFirstPage()

      expectNoEvents()
    }
    assertEquals(
      PagingState.Error("fetch 1 failed", isFirstPage = true),
      harness.mediator.pagingState.value,
    )
  }

  @Test
  fun `key only advances on success so retry refetches the failed page`() = runTest {
    val harness = Harness()
    harness.mediator.loadFirstPage()
    harness.failOnKey(2)

    harness.mediator.loadNextPage() // page 2 fails
    harness.succeedOnKey(2)
    harness.mediator.loadNextPage() // retries page 2
    harness.mediator.loadNextPage() // then moves on to page 3

    assertEquals(listOf(1, 2, 2, 3), harness.fetchedKeys)
  }

  @Test
  fun `empty page emits EndOfPagination and further loadNextPage is a no-op`() = runTest {
    val harness = Harness(pages = { page -> if (page >= 2) emptyList() else listOf("item") })
    harness.mediator.loadFirstPage()

    harness.mediator.loadNextPage() // page 2 is empty
    assertEquals(PagingState.EndOfPagination(), harness.mediator.pagingState.value)

    harness.mediator.loadNextPage()
    harness.mediator.loadNextPage()
    assertEquals(listOf(1, 2), harness.fetchedKeys)
  }

  @Test
  fun `nextKey returning null emits EndOfPagination`() = runTest {
    val storage = RecordingStorage()
    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        // Short page with an explicit null nextKey: ends without a wasted empty-page fetch.
        fetchRemote = { PageResult(listOf("only item"), nextKey = null) },
        classifyError = { "unused" },
        storage = storage,
      )

    mediator.loadFirstPage()

    assertEquals(PagingState.EndOfPagination(), mediator.pagingState.value)
    assertEquals(listOf("only item"), storage.stored.value)
  }

  // A single-page surface never emits Success at all, so EndOfPagination is its only chance to
  // carry the server total - without it the UI can never render "N results" for short datasets.
  @Test
  fun `EndOfPagination carries the total when the first page is also the last`() = runTest {
    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        fetchRemote = { PageResult(listOf("only item"), nextKey = null, totalCount = 14) },
        classifyError = { "unused" },
      )

    mediator.loadFirstPage()

    assertEquals(PagingState.EndOfPagination(totalCount = 14), mediator.pagingState.value)
  }

  @Test
  fun `the empty-page probe carries the total when the header reports one`() = runTest {
    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        // A server that reports totals but whose nextKey math wasn't wired: page 2 comes back
        // empty with the header still present.
        fetchRemote = { page ->
          if (page == 1) PageResult(listOf("item"), nextKey = 2, totalCount = 1)
          else PageResult(emptyList(), nextKey = 3, totalCount = 1)
        },
        classifyError = { "unused" },
      )

    mediator.loadFirstPage()
    mediator.loadNextPage()

    assertEquals(PagingState.EndOfPagination(totalCount = 1), mediator.pagingState.value)
  }

  @Test
  fun `loadFirstPage after EndOfPagination resets and loads again`() = runTest {
    val harness = Harness(pages = { page -> if (page >= 2) emptyList() else listOf("item") })
    harness.mediator.loadFirstPage()
    harness.mediator.loadNextPage() // reaches EndOfPagination

    harness.mediator.loadFirstPage()

    assertEquals(PagingState.Success(), harness.mediator.pagingState.value)
    assertEquals(listOf(1, 2, 1), harness.fetchedKeys)
  }

  @Test
  fun `concurrent loadNextPage calls collapse to a single fetch`() = runTest {
    val gate = CompletableDeferred<Unit>()
    var fetchCount = 0
    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        fetchRemote = {
          fetchCount++
          gate.await()
          PageResult(listOf("item"), nextKey = 2)
        },
        classifyError = { "unused" },
      )

    launch { mediator.loadNextPage() }
    launch { mediator.loadNextPage() }
    launch { mediator.loadNextPage() }
    runCurrent() // first call is suspended in fetchRemote; the others bail out

    gate.complete(Unit)
    advanceUntilIdle()

    assertEquals(1, fetchCount)
    assertEquals(PagingState.Success(), mediator.pagingState.value)
  }

  @Test
  fun `CancellationException is rethrown not classified as Error`() = runTest {
    var classified = false
    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        fetchRemote = { throw CancellationException("cancelled") },
        classifyError = {
          classified = true
          "should never happen"
        },
      )

    val result = runCatching { mediator.loadNextPage() }

    assertTrue(result.exceptionOrNull() is CancellationException)
    assertTrue(!classified)
  }

  @Test
  fun `refresh hands the new first page to storeFirstPage only after a successful fetch`() =
    runTest {
      val harness = Harness()
      harness.mediator.loadFirstPage()
      harness.mediator.loadNextPage()
      assertEquals(listOf("item 1", "item 2"), harness.storage.stored.value)
      harness.storage.events.clear()

      harness.mediator.loadFirstPage() // refresh

      assertEquals(listOf("fetch 1", "storeFirstPage"), harness.storage.events)
      assertEquals(listOf("item 1"), harness.storage.stored.value)
    }

  @Test
  fun `failed refresh never touches storage`() = runTest {
    val harness = Harness()
    harness.mediator.loadFirstPage()
    harness.mediator.loadNextPage()
    harness.storage.events.clear()
    harness.failOnKey(1)

    harness.mediator.loadFirstPage() // refresh fails

    assertEquals(listOf("fetch 1"), harness.storage.events)
    assertEquals(listOf("item 1", "item 2"), harness.storage.stored.value)
    assertEquals(
      PagingState.Error("fetch 1 failed", isFirstPage = true),
      harness.mediator.pagingState.value,
    )
  }

  // Regression guard: a failed first page retried through loadNextPage must re-run as a *first*
  // page. Storing it via append would duplicate page 1 in a replacing storage and leave the pager
  // positioned as if page 1 were the next page in a merging one.
  @Test
  fun `after a failed first load loadNextPage retries it as a first page not an append`() =
    runTest {
      val harness = Harness()
      harness.failOnKey(1)
      harness.mediator.loadFirstPage() // fails
      harness.succeedOnKey(1)
      harness.storage.events.clear()

      harness.mediator.loadNextPage() // retry entry point after the failure

      assertEquals(listOf("fetch 1", "storeFirstPage"), harness.storage.events)
      assertEquals(listOf("item 1"), harness.storage.stored.value)

      harness.mediator.loadNextPage() // and pagination resumes normally afterwards
      assertEquals(listOf(1, 1, 2), harness.fetchedKeys)
    }

  @Test
  fun `after a failed refresh loadNextPage re-runs the refresh instead of appending page 1`() =
    runTest {
      val harness = Harness()
      harness.mediator.loadFirstPage()
      harness.mediator.loadNextPage() // pages 1 and 2 on screen
      harness.failOnKey(1)
      harness.mediator.loadFirstPage() // refresh fails
      harness.succeedOnKey(1)
      harness.storage.events.clear()

      harness.mediator.loadNextPage()

      assertEquals(listOf("fetch 1", "storeFirstPage"), harness.storage.events)
      // The replacing storage holds exactly the refreshed first page - "item 1" was not appended
      // after the stale list.
      assertEquals(listOf("item 1"), harness.storage.stored.value)
    }

  @Test
  fun `storage write failure emits Error and does not advance the key`() = runTest {
    val harness = Harness()
    harness.storage.failWrites = true

    harness.mediator.loadFirstPage()
    assertEquals(
      PagingState.Error("write failed", isFirstPage = true),
      harness.mediator.pagingState.value,
    )

    harness.storage.failWrites = false
    harness.mediator.loadNextPage() // retries page 1, not page 2

    assertEquals(listOf(1, 1), harness.fetchedKeys)
    assertEquals(listOf("item 1"), harness.storage.stored.value)
  }

  /** Non-replacing storage mirroring a DB upsert: every write merges instead of replacing. */
  private class UpsertingStorage : PagingStorage<Int, String> {
    val stored = MutableStateFlow<List<String>>(emptyList())

    override val data: Flow<List<String>> = stored

    override suspend fun storeFirstPage(page: PageResult<Int, String>) = upsert(page.items)

    override suspend fun append(page: PageResult<Int, String>) = upsert(page.items)

    private fun upsert(page: List<String>) {
      stored.update { current -> current + page.filterNot(current::contains) }
    }
  }

  @Test
  fun `nextKeyFromStorage seeds the first loadNextPage over a warm cache`() = runTest {
    val fetchedKeys = mutableListOf<Int>()
    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        fetchRemote = { key ->
          fetchedKeys += key
          PageResult(listOf("item $key"), nextKey = key + 1)
        },
        classifyError = { "unused" },
        nextKeyFromStorage = { 4 }, // e.g. three full pages already sit in storage
      )

    mediator.loadNextPage()
    mediator.loadNextPage()

    assertEquals(listOf(4, 5), fetchedKeys)
  }

  @Test
  fun `refresh always fetches initialKey even with nextKeyFromStorage`() = runTest {
    val fetchedKeys = mutableListOf<Int>()
    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        fetchRemote = { key ->
          fetchedKeys += key
          PageResult(listOf("item $key"), nextKey = key + 1)
        },
        classifyError = { "unused" },
        nextKeyFromStorage = { 2 }, // in-memory default storage: one page stored after first load
      )

    mediator.loadFirstPage()
    mediator.loadNextPage()

    assertEquals(listOf(1, 2), fetchedKeys)
  }

  @Test
  fun `refresh over a non-replacing storage resumes load-more after everything stored`() = runTest {
    val storage = UpsertingStorage()
    val fetchedKeys = mutableListOf<Int>()
    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        fetchRemote = { key ->
          fetchedKeys += key
          PageResult(listOf("item $key"), nextKey = key + 1)
        },
        classifyError = { "unused" },
        storage = storage,
        nextKeyFromStorage = { storage.stored.value.size + 1 }, // page size is 1 item
      )
    mediator.loadFirstPage()
    mediator.loadNextPage() // pages 1 and 2 stored

    mediator.loadFirstPage() // refresh: the upsert keeps both stored pages

    mediator.loadNextPage() // must fetch page 3, not re-walk page 2
    assertEquals(listOf(1, 2, 1, 3), fetchedKeys)
    assertEquals(listOf("item 1", "item 2", "item 3"), storage.stored.value)
  }

  // A dataset smaller than one page: the fetch's total-count math says the first page is also the
  // last (nextKey == null). The storage's row-count estimate rounds to "page 1 is next" and must
  // not override that - it used to, costing a redundant refetch of page 1 on the next scroll.
  @Test
  fun `a first page that is also the last ends pagination despite nextKeyFromStorage`() = runTest {
    val fetchedKeys = mutableListOf<Int>()
    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        fetchRemote = { key ->
          fetchedKeys += key
          PageResult(listOf("only item"), nextKey = null)
        },
        classifyError = { "unused" },
        nextKeyFromStorage = { 1 }, // stale estimate: fewer rows than a page rounds back to 1
      )

    mediator.loadFirstPage()
    assertEquals(PagingState.EndOfPagination(), mediator.pagingState.value)

    mediator.loadNextPage() // must be a no-op, not a refetch of page 1
    assertEquals(listOf(1), fetchedKeys)
  }

  @Test
  fun `nextKeyFromStorage failure emits Error and the next loadNextPage retries it`() = runTest {
    val fetchedKeys = mutableListOf<Int>()
    var failResume = true
    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        fetchRemote = { key ->
          fetchedKeys += key
          PageResult(listOf("item $key"), nextKey = key + 1)
        },
        classifyError = { it.message ?: "unknown" },
        nextKeyFromStorage = {
          if (failResume) throw RuntimeException("count failed")
          4
        },
      )

    mediator.loadNextPage()

    assertEquals(PagingState.Error("count failed", isFirstPage = false), mediator.pagingState.value)
    assertEquals(emptyList<Int>(), fetchedKeys)

    failResume = false
    mediator.loadNextPage()

    assertEquals(listOf(4), fetchedKeys)
  }

  @Test
  fun `default in-memory storage accumulates pages and refresh replaces them`() = runTest {
    var refreshed = false
    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        fetchRemote = { page ->
          val items = if (refreshed) listOf("new $page") else listOf("item $page")
          PageResult(items, nextKey = page + 1)
        },
        classifyError = { "unused" },
      )

    mediator.data.test {
      assertEquals(emptyList<String>(), awaitItem())

      mediator.loadFirstPage()
      assertEquals(listOf("item 1"), awaitItem())

      mediator.loadNextPage()
      assertEquals(listOf("item 1", "item 2"), awaitItem())

      refreshed = true
      mediator.loadFirstPage()
      assertEquals(listOf("new 1"), awaitItem())
    }
  }

  @Test
  fun `in-memory refresh against a now-empty backend clears stale items`() = runTest {
    var isFirstCall = true
    val mediator =
      PagingMediator<Int, String, String>(
        initialKey = 1,
        fetchRemote = { page ->
          if (isFirstCall) {
            isFirstCall = false
            PageResult(listOf("stale"), nextKey = page + 1)
          } else {
            PageResult(emptyList(), nextKey = null)
          }
        },
        classifyError = { "unused" },
      )
    mediator.loadFirstPage()

    mediator.loadFirstPage() // refresh: the stale item must not survive

    assertEquals(PagingState.EndOfPagination(), mediator.pagingState.value)
    mediator.data.test { assertEquals(emptyList<String>(), awaitItem()) }
  }
}
