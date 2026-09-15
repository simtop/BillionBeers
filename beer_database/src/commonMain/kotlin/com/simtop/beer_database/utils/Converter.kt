package com.simtop.beer_database.utils

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

// Not registered as a Room @TypeConverters - BeersMapper calls these directly at the
// domain/DB boundary instead, keeping the List<String> <-> String conversion visible there
// rather than implicit in Room.
object Converters {

  @TypeConverter fun listToJson(value: List<String>?) = Json.encodeToString(value ?: emptyList())

  @TypeConverter fun jsonToList(value: String): List<String> = Json.decodeFromString(value)
}
