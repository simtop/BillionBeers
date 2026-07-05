package com.simtop.beer_data.mappers

import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.Language
import com.simtop.beer_network.models.Translation
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.NoOpLogger
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class BeersMapperTest {

  private var languageCode = "en"
  private val mapper = BeersMapper(LanguageProvider { languageCode }, NoOpLogger())

  private fun fullResponse() =
    BeersApiResponseItem(
      id = "1",
      name = "Buzz",
      abv = 4.5,
      ibu = 60.0,
      imageId = "42",
      translations = listOf(Translation(Language("en"), "A Real Bitter Experience.", "Tasty.")),
      foodPairing = listOf("Steak"),
    )

  @Test
  fun `fromBeersApiResponseItemToBeer maps a fully populated response`() {
    val beer = mapper.fromBeersApiResponseItemToBeer(fullResponse())

    expectThat(beer)
      .isEqualTo(
        Beer(
          id = "1",
          name = "Buzz",
          tagline = "A Real Bitter Experience.",
          description = "Tasty.",
          imageUrl = "https://brewbuddy.dev/images/42",
          abv = 4.5,
          ibu = 60.0,
          foodPairing = listOf("Steak"),
        )
      )
  }

  @Test
  fun `fromBeersApiResponseItemToBeer defaults everything when response is null`() {
    val beer = mapper.fromBeersApiResponseItemToBeer(null)

    expectThat(beer)
      .isEqualTo(
        Beer(
          id = "",
          name = "",
          tagline = "",
          description = "",
          imageUrl = "",
          abv = 0.0,
          ibu = 0.0,
          foodPairing = emptyList(),
        )
      )
  }

  @Test
  fun `fromBeersApiResponseItemToBeer defaults id when missing`() {
    val beer = mapper.fromBeersApiResponseItemToBeer(fullResponse().copy(id = null))

    expectThat(beer.id).isEqualTo("")
  }

  @Test
  fun `fromBeersApiResponseItemToBeer defaults tagline and description when no matching translation`() {
    val response =
      fullResponse()
        .copy(translations = listOf(Translation(Language("de"), "Ein Bier.", "Lecker.")))

    val beer = mapper.fromBeersApiResponseItemToBeer(response)

    expectThat(beer.tagline).isEqualTo("")
    expectThat(beer.description).isEqualTo("")
  }

  @Test
  fun `fromBeersApiResponseItemToBeer picks the translation matching the current language`() {
    languageCode = "fr"
    val response =
      fullResponse()
        .copy(
          translations =
            listOf(
              Translation(Language("en"), "A Real Bitter Experience.", "Tasty."),
              Translation(Language("fr"), "Une bière.", "Savoureux."),
            )
        )

    val beer = mapper.fromBeersApiResponseItemToBeer(response)

    expectThat(beer.tagline).isEqualTo("Une bière.")
    expectThat(beer.description).isEqualTo("Savoureux.")
  }

  @Test
  fun `fromBeersApiResponseItemToBeer falls back to en when current language has no translation`() {
    languageCode = "fr"

    val beer = mapper.fromBeersApiResponseItemToBeer(fullResponse())

    expectThat(beer.tagline).isEqualTo("A Real Bitter Experience.")
    expectThat(beer.description).isEqualTo("Tasty.")
  }

  @Test
  fun `fromBeersApiResponseItemToBeer defaults tagline and description when translations is null`() {
    val beer = mapper.fromBeersApiResponseItemToBeer(fullResponse().copy(translations = null))

    expectThat(beer.tagline).isEqualTo("")
    expectThat(beer.description).isEqualTo("")
  }

  @Test
  fun `fromBeersApiResponseItemToBeer defaults imageUrl when imageId is missing`() {
    val beer = mapper.fromBeersApiResponseItemToBeer(fullResponse().copy(imageId = null))

    expectThat(beer.imageUrl).isEqualTo("")
  }

  @Test
  fun `fromBeerToBeerDbModel converts a Beer into a BeerDbModel`() {
    val beer =
      Beer(
        id = "1",
        name = "Buzz",
        tagline = "A Real Bitter Experience.",
        description = "Tasty.",
        imageUrl = "https://brewbuddy.dev/images/42",
        abv = 4.5,
        ibu = 60.0,
        foodPairing = listOf("Steak"),
        availability = false,
      )

    val dbModel = mapper.fromBeerToBeerDbModel(beer)

    expectThat(dbModel)
      .isEqualTo(
        BeerDbModel(
          id = "1",
          name = "Buzz",
          tagline = "A Real Bitter Experience.",
          description = "Tasty.",
          imageUrl = "https://brewbuddy.dev/images/42",
          abv = 4.5,
          ibu = 60.0,
          foodPairing = "[\"Steak\"]",
          availability = false,
        )
      )
  }

  @Test
  fun `fromBeerDbModelToBeer converts a BeerDbModel into a Beer`() {
    val dbModel =
      BeerDbModel(
        id = "1",
        name = "Buzz",
        tagline = "A Real Bitter Experience.",
        description = "Tasty.",
        imageUrl = "https://brewbuddy.dev/images/42",
        abv = 4.5,
        ibu = 60.0,
        foodPairing = "[\"Steak\"]",
        availability = false,
      )

    val beer = mapper.fromBeerDbModelToBeer(dbModel)

    expectThat(beer)
      .isEqualTo(
        Beer(
          id = "1",
          name = "Buzz",
          tagline = "A Real Bitter Experience.",
          description = "Tasty.",
          imageUrl = "https://brewbuddy.dev/images/42",
          abv = 4.5,
          ibu = 60.0,
          foodPairing = listOf("Steak"),
          availability = false,
        )
      )
  }
}
