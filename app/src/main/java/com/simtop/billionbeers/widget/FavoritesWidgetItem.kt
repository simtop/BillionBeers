package com.simtop.billionbeers.widget

import com.simtop.beerdomain.domain.models.Beer

data class FavoritesWidgetItem(
  val id: String,
  val name: String,
  val imageUrl: String,
  val availability: Boolean,
)

fun List<Beer>.toFavoritesWidgetItems(maxItems: Int = 3): List<FavoritesWidgetItem> =
  asSequence()
    .take(maxItems)
    .map {
      FavoritesWidgetItem(
        id = it.id,
        name = it.name,
        imageUrl = it.imageUrl,
        availability = it.availability,
      )
    }
    .toList()
