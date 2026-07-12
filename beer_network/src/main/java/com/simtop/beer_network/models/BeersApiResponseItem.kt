package com.simtop.beer_network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BeersApiResponseItem(
  val id: String? = null,
  val name: String? = null,
  val abv: Double? = null,
  val ibu: Double? = null,
  val image: EmbeddedImage? = null,
  // Only ever seeds a row's initial availability on first insert; the local edit stays
  // authoritative.
  val available: Boolean? = null,
  val translations: List<Translation>? = null,
  @SerialName("food_pairing") val foodPairing: List<String>? = null,
)

@Serializable data class EmbeddedImage(val url: String? = null)

@Serializable
data class Translation(val language: Language, val slogan: String?, val description: String?)

@Serializable data class Language(val code: String)
