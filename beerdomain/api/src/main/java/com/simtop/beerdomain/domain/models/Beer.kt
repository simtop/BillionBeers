package com.simtop.beerdomain.domain.models

import kotlinx.serialization.Serializable

// @Keep
@Serializable
data class Beer(
  val id: String,
  val name: String,
  val tagline: String,
  val description: String,
  val imageUrl: String,
  val abv: Double,
  val ibu: Double,
  val foodPairing: List<String>,
  val availability: Boolean = true,
  // Detail-screen fields from the embedded list-response objects (Paging 2.0 Phase 4). All
  // defaulted: a Beer serialized before they existed (nav key / SavedStateHandle across an app
  // update) must still decode.
  val styleName: String = "",
  val breweryName: String = "",
  val srm: Int? = null,
  val releasedYear: Int? = null,
  val minServingTemperature: Int? = null,
  val maxServingTemperature: Int? = null,
  val fermentationMethod: String = "",
  val ingredients: List<String> = emptyList(),
  val recommendedGlasses: List<String> = emptyList(),
) {
  companion object {
    val empty = Beer("1", "", "", "", "", 0.0, 0.0, emptyList(), true)
  }
}
