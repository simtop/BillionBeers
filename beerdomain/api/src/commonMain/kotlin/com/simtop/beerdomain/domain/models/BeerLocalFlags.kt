package com.simtop.beerdomain.domain.models

/** The locally edited fields a query surface overlays on its in-memory beer results. */
data class BeerLocalFlags(
  val id: String,
  val availability: Boolean,
  val isFavorite: Boolean,
)
