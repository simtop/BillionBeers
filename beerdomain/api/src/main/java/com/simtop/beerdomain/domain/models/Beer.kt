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
  var availability: Boolean = true
) {
  companion object {
    val empty = Beer("1", "", "", "", "", 0.0, 0.0, emptyList(), true)
  }
}
