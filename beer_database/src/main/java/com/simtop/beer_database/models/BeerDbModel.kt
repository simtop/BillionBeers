package com.simtop.beer_database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "beers")
data class BeerDbModel(
  @PrimaryKey @ColumnInfo(name = "id") val id: String,
  @ColumnInfo(name = "name") val name: String,
  @ColumnInfo(name = "tagline") val tagline: String,
  @ColumnInfo(name = "description") val description: String,
  @ColumnInfo(name = "image_url") val imageUrl: String,
  @ColumnInfo(name = "abv") val abv: Double,
  @ColumnInfo(name = "ibu") val ibu: Double,
  @ColumnInfo(name = "food_pairing") val foodPairing: String,
  @ColumnInfo(name = "availability") val availability: Boolean = true,
  // Detail fields (v3). Kotlin defaults keep old call sites compiling; the SQL defaultValue
  // mirrors the v2->v3 ALTER TABLE statements, so a migrated row and a fresh row agree.
  @ColumnInfo(name = "style_name", defaultValue = "") val styleName: String = "",
  @ColumnInfo(name = "brewery_name", defaultValue = "") val breweryName: String = "",
  @ColumnInfo(name = "srm") val srm: Int? = null,
  @ColumnInfo(name = "released_year") val releasedYear: Int? = null,
  @ColumnInfo(name = "min_serving_temperature") val minServingTemperature: Int? = null,
  @ColumnInfo(name = "max_serving_temperature") val maxServingTemperature: Int? = null,
  @ColumnInfo(name = "fermentation_method", defaultValue = "") val fermentationMethod: String = "",
  // JSON-encoded string lists, like food_pairing.
  @ColumnInfo(name = "ingredients", defaultValue = "[]") val ingredients: String = "[]",
  @ColumnInfo(name = "recommended_glasses", defaultValue = "[]")
  val recommendedGlasses: String = "[]",
)
