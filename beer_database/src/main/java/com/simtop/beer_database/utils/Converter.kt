package com.simtop.beer_database.utils
 
import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
 
// Decided not to use the converter in the DB
object Converters {
 
  @TypeConverter fun listToJson(value: List<String>?) = Json.encodeToString(value ?: emptyList())
 
  @TypeConverter
  fun jsonToList(value: String): List<String> = Json.decodeFromString(value)
}
