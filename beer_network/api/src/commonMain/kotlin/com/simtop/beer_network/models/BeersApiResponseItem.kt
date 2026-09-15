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
  val srm: Int? = null,
  @SerialName("released_year") val releasedYear: Int? = null,
  @SerialName("minimum_serving_temperature") val minServingTemperature: Int? = null,
  @SerialName("maximum_serving_temperature") val maxServingTemperature: Int? = null,
  // `name` is language-neutral for typologies; only descriptions are translated (unrendered).
  val typology: EmbeddedTypology? = null,
  val brewery: BreweryApiResponseItem? = null,
  @SerialName("fermentation_method") val fermentationMethod: NamedEntity? = null,
  val ingredients: List<NamedEntity>? = null,
  @SerialName("recommended_glasses") val recommendedGlasses: List<NamedEntity>? = null,
)

@Serializable data class EmbeddedImage(val url: String? = null)

@Serializable
data class Translation(val language: Language, val slogan: String?, val description: String?)

@Serializable data class Language(val code: String)

@Serializable data class EmbeddedTypology(val name: String? = null)

/**
 * An embedded entity whose display name lives in per-language [translations] (an ingredient, a
 * recommended glass, a fermentation method) - unlike a beer's [Translation], which carries
 * slogan/description instead of a name.
 */
@Serializable data class NamedEntity(val translations: List<NamedTranslation>? = null)

@Serializable data class NamedTranslation(val name: String? = null, val language: Language? = null)
