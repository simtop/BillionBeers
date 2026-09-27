package com.simtop.beerdomain.domain.models

/** A named, local-only snapshot of the supported beer-list query. */
data class SavedFilterPreset(
  val id: String,
  val name: String,
  val query: BeersQuery,
  val updatedAt: Long,
) {
  init {
    require(id.isNotBlank()) { "Saved filter preset id must not be blank" }
    require(name.isNotBlank()) { "Saved filter preset name must not be blank" }
    require(name.length <= MAX_NAME_LENGTH) {
      "Saved filter preset name must be at most $MAX_NAME_LENGTH characters"
    }
    require(updatedAt >= 0L) { "Saved filter preset timestamp must not be negative" }
  }

  companion object {
    const val MAX_COUNT = 10
    const val MAX_NAME_LENGTH = 64
  }
}
