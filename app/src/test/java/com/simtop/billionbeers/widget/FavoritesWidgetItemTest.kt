package com.simtop.billionbeers.widget

import com.simtop.beerdomain.domain.models.Beer
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesWidgetItemTest {
  @Test
  fun mapsAtMostThreeFavoritesInOrder() {
    val beers = (1..4).map { beer(id = "$it", availability = it != 2) }

    assertEquals(
      listOf(
        FavoritesWidgetItem("1", "Beer 1", "https://example.com/1.png", true),
        FavoritesWidgetItem("2", "Beer 2", "https://example.com/2.png", false),
        FavoritesWidgetItem("3", "Beer 3", "https://example.com/3.png", true),
      ),
      beers.toFavoritesWidgetItems(),
    )
  }
}

internal fun beer(
  id: String,
  name: String = "Beer $id",
  availability: Boolean = true,
  isFavorite: Boolean = true,
): Beer =
  Beer(
    id = id,
    name = name,
    tagline = "",
    description = "",
    imageUrl = "https://example.com/$id.png",
    abv = 0.0,
    ibu = 0.0,
    foodPairing = emptyList(),
    availability = availability,
    isFavorite = isFavorite,
  )
