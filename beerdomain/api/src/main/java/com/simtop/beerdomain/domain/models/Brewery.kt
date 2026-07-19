package com.simtop.beerdomain.domain.models

/**
 * A brewery as the browse list renders it. [countryCode] is the ISO code ("KP"); localized country
 * names exist server-side but a list row doesn't need them.
 */
data class Brewery(
  val id: String,
  val name: String,
  val countryCode: String,
  val foundedYear: Int?,
  val imageUrl: String,
)
