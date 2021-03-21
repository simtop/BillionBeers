package com.simtop.beerdomain.data.models


import com.google.gson.annotations.SerializedName

data class BeersApiResponseItem(
    val id: Int?,
    val name: String?,
    val tagline: String?,
    val description: String?,
    @SerializedName("image_url")
    val imageUrl: String?,
    val abv: Double?,
    val ibu: Double?,
    @SerializedName("food_pairing")
    val foodPairing: List<String>?
)