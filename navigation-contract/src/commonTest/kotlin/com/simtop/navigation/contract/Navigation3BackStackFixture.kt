package com.simtop.navigation.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Test-only back-stack model used to probe the common Navigation 3 contract without changing a
 * production host. Entry identity is deliberately separate from route equality: two visits to the
 * same destination are still two distinct entries.
 */
class Navigation3BackStackFixture(initialRoute: PortableRoute) {
  private var nextEntryId = INITIAL_ENTRY_ID + 1

  private val mutableEntries =
    mutableListOf(
      Entry(id = INITIAL_ENTRY_ID, route = initialRoute),
    )

  val entries: List<Entry>
    get() = mutableEntries.toList()

  fun push(route: PortableRoute): Entry {
    val entry = Entry(id = nextEntryId++, route = route)
    mutableEntries += entry
    return entry
  }

  fun pop(): Boolean {
    if (mutableEntries.size == 1) return false
    mutableEntries.removeLast()
    return true
  }

  fun replaceRoot(route: PortableRoute) {
    mutableEntries.clear()
    mutableEntries += Entry(id = nextEntryId++, route = route)
  }

  fun encode(): String = json.encodeToString(SavedState(entries))

  companion object {
    private const val INITIAL_ENTRY_ID = 1L

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(payload: String): Navigation3BackStackFixture {
      val state = json.decodeFromString<SavedState>(payload)
      require(state.entries.isNotEmpty()) { "A back stack must contain a root entry" }

      return Navigation3BackStackFixture(state.entries).also { fixture ->
        fixture.mutableEntries.clear()
        fixture.mutableEntries += state.entries
        fixture.nextEntryId = state.entries.maxOf { it.id } + 1
      }
    }
  }

  @Serializable
  data class Entry(val id: Long, val route: PortableRoute)

  @Serializable
  private data class SavedState(val entries: List<Entry>)

  private constructor(restoredEntries: List<Entry>) : this(restoredEntries.first().route) {
    mutableEntries.clear()
    mutableEntries += restoredEntries
  }
}
