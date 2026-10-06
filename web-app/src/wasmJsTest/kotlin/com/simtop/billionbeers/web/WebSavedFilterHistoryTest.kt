package com.simtop.billionbeers.web

import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class WebSavedFilterHistoryTest {
  private val preset = SavedFilterPreset("preset", "IPA", BeersQuery(search = "ipa"), 1L)

  @Test
  fun tokensRestoreExactAppliedSnapshotsWithoutPuttingQueriesInTokens() {
    val history = WebSavedFilterHistory("page-1")
    val first = history.remember(preset)
    val renamed = preset.copy(name = "Lager", query = BeersQuery(styleId = "lager"), updatedAt = 2L)
    val second = history.remember(renamed)
    assertNotEquals(first, second)
    assertEquals("page-1:1", first)
    assertEquals(preset, history.resolve(first))
    assertEquals(renamed, history.resolve(second))
    assertNull(history.resolve("unknown"))
  }

  @Test
  fun reloadDoesNotRestoreAnOldTokenOrCollideWithNewEntries() {
    val previous = WebSavedFilterHistory("page-1")
    val oldToken = previous.remember(preset)
    val reloaded = WebSavedFilterHistory("page-2")
    assertNull(reloaded.resolve(oldToken))
    assertNotEquals(oldToken, reloaded.remember(preset))
    assertNull(reloaded.resolve(oldToken))
    previous.clear()
    assertNull(previous.resolve(oldToken))
  }
}
