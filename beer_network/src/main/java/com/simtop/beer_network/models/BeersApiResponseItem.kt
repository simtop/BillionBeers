package com.simtop.beer_network.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class BeersApiResponseItem(
  val id: String?,
  val name: String?,
  val abv: Double?,
  val ibu: Double?,
  @SerializedName("image_id") val imageId: String?,
  val translations: List<Translation>?,
  @SerializedName("food_pairing") val foodPairing: List<String>?
)

@Keep data class Translation(val language: Language, val slogan: String?, val description: String?)

@Keep data class Language(val code: String)
