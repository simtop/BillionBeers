package com.simtop.beer_database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filter_presets")
data class SavedFilterPresetDbModel(
  @PrimaryKey @ColumnInfo(name = "id") val id: String,
  @ColumnInfo(name = "name") val name: String,
  @ColumnInfo(name = "search") val search: String?,
  @ColumnInfo(name = "style_id") val styleId: String?,
  @ColumnInfo(name = "brewery_id") val breweryId: String?,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
