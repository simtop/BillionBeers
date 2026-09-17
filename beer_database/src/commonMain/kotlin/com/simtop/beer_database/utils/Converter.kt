package com.simtop.beer_database.utils

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

// Not registered as Room @TypeConverters - the Room adapter calls these explicitly at the
// storage/DB boundary, keeping the List<String> <-> String conversion out of the portable contract.
object Converters {

  @TypeConverter fun listToJson(value: List<String>?) = Json.encodeToString(value ?: emptyList())

  @TypeConverter fun jsonToList(value: String): List<String> = Json.decodeFromString(value)
}
