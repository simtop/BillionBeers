package com.simtop.beer_network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One `/breweries` row; the embedded `country` and `image` objects replace by-id lookups. */
@Serializable
data class BreweryApiResponseItem(
  val id: String? = null,
  val name: String? = null,
  @SerialName("founded_year") val foundedYear: Int? = null,
  val country: EmbeddedCountry? = null,
  val image: EmbeddedImage? = null,
)

@Serializable data class EmbeddedCountry(val code: String? = null)
