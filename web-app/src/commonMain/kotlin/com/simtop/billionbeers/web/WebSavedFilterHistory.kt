package com.simtop.billionbeers.web

import com.simtop.beerdomain.domain.models.SavedFilterPreset

/** Query snapshots live only for this page session; browser state carries an opaque token. */
internal class WebSavedFilterHistory(private val sessionId: String) {
  private var nextId = 0L
  private val snapshots = mutableMapOf<String, SavedFilterPreset>()

  fun remember(preset: SavedFilterPreset): String {
    val token = "$sessionId:${++nextId}"
    snapshots[token] = preset
    return token
  }

  fun resolve(token: String): SavedFilterPreset? = snapshots[token]

  fun clear() = snapshots.clear()
}
