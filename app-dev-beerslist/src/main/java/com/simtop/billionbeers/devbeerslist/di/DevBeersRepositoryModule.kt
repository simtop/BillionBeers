package com.simtop.billionbeers.devbeerslist.di

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface DevBeersRepositoryModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideBeersRepository(): BeersRepository = FakeBeersRepository(initialBeers = sampleBeers)
}

private val sampleBeers =
  listOf(
    Beer(
      id = "1",
      name = "Buzz",
      tagline = "A Real Bitter Experience.",
      description =
        "A light, crisp and bitter IPA brewed with English and American hops. A small batch " +
          "brewed only once.",
      imageUrl = "",
      abv = 4.5,
      ibu = 60.0,
      foodPairing = listOf("Spicy chicken tikka masala", "Grilled chicken quesadilla"),
      availability = true,
    ),
    Beer(
      id = "2",
      name = "Trashy Blonde",
      tagline = "You Know You Shouldn't",
      description = "A titillating, neurotic, peroxide blonde beer.",
      imageUrl = "",
      abv = 4.1,
      ibu = 41.5,
      foodPairing = listOf("Fried chicken", "Nachos"),
      availability = false,
    ),
    Beer(
      id = "3",
      name = "Avery Brown Dredge",
      tagline = "Bloggers Imperial Pilsner.",
      description = "An Imperial Pilsner in collaboration with beer writers.",
      imageUrl = "",
      abv = 7.2,
      ibu = 59.0,
      foodPairing = listOf("Fish and chips", "Caesar salad"),
      availability = true,
    ),
  )
