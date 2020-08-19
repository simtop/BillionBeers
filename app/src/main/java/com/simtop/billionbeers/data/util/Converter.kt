package com.simtop.billionbeers.data.util

import androidx.room.TypeConverter
import com.google.gson.Gson

object Converters {

    @TypeConverter
    fun listToJson(value: List<String>?) = Gson().toJson(value)

    @TypeConverter
    fun jsonToList(value: String) = Gson().fromJson(value, Array<String>::class.java).toList()
}
