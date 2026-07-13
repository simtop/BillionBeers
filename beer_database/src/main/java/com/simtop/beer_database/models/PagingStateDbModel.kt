package com.simtop.beer_database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-surface paging bookmark, written in the same transaction as the page's rows so position never
 * diverges from data. [surface] scopes one paged list (e.g. `"catalog:en"`); [nextKey] is the first
 * page not yet cached (kept monotonic non-decreasing so a refresh over a warm cache never rewinds
 * it); [totalCount] is the server's `X-Total-Count`. A row is only ever *read* here in Phase 1 -
 * acting on a surface mismatch (language switch) is deferred.
 */
@Entity(tableName = "paging_state")
data class PagingStateDbModel(
  @PrimaryKey @ColumnInfo(name = "surface") val surface: String,
  @ColumnInfo(name = "next_key") val nextKey: Int?,
  @ColumnInfo(name = "total_count") val totalCount: Int?,
  @ColumnInfo(name = "refreshed_at") val refreshedAt: Long,
)
