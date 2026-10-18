package com.simtop.beer_database.models

import androidx.room.ColumnInfo

/** Lightweight Room projection for a query pager's local-flag overlay. */
data class BeerLocalFlagsDbModel(
  @ColumnInfo(name = "id") val id: String,
  @ColumnInfo(name = "availability") val availability: Boolean,
  @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
)
