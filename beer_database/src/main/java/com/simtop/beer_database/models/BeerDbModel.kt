package com.simtop.beer_database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "beers")
data class BeerDbModel(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "tagline")
    val tagline: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "image_url")
    val imageUrl: String,
    @ColumnInfo(name = "abv")
    val abv: Double,
    @ColumnInfo(name = "ibu")
    val ibu: Double,
    @ColumnInfo(name = "food_pairing")
    val foodPairing : String,
    @ColumnInfo(name = "availability")
    val availability : Boolean = true
)
