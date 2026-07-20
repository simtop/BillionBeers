package com.simtop.beer_data.mappers

import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.BreweryApiResponseItem
import com.simtop.beer_network.models.EmbeddedImage
import com.simtop.beer_network.models.EmbeddedTypology
import com.simtop.beer_network.models.Language
import com.simtop.beer_network.models.NamedEntity
import com.simtop.beer_network.models.NamedTranslation
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
      image = EmbeddedImage("https://brewbuddy.dev/images/42"),
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
  fun `fromBeersApiResponseItemToBeer defaults imageUrl when the embedded image is missing`() {
    val beer = mapper.fromBeersApiResponseItemToBeer(fullResponse().copy(image = null))

    expectThat(beer.imageUrl).isEqualTo("")
  }

  @Test
  fun `fromBeersApiResponseItemToBeer defaults imageUrl when the embedded image has no url`() {
    val beer =
      mapper.fromBeersApiResponseItemToBeer(fullResponse().copy(image = EmbeddedImage(url = null)))

    expectThat(beer.imageUrl).isEqualTo("")
  }

  @Test
  fun `fromBeersApiResponseItemToBeer seeds availability from the server available field`() {
    val beer = mapper.fromBeersApiResponseItemToBeer(fullResponse().copy(available = false))

    expectThat(beer.availability).isEqualTo(false)
  }

  @Test
  fun `fromBeersApiResponseItemToBeer defaults availability to true when available is absent`() {
    val beer = mapper.fromBeersApiResponseItemToBeer(fullResponse().copy(available = null))

    expectThat(beer.availability).isEqualTo(true)
  }

  @Test
  fun `fromBeersApiResponseItemToBeer maps the embedded detail fields`() {
    val response =
      fullResponse()
        .copy(
          srm = 9,
          releasedYear = 1980,
          minServingTemperature = 1,
          maxServingTemperature = 9,
          typology = EmbeddedTypology(name = "Stout"),
          brewery = BreweryApiResponseItem(id = "b1", name = "ChuckleCraft Brewery"),
          fermentationMethod = namedEntity("en" to "Lager", "it" to "Lager IT"),
          ingredients = listOf(namedEntity("en" to "Dark malt")),
          recommendedGlasses = listOf(namedEntity("en" to "Chalice")),
        )

    val beer = mapper.fromBeersApiResponseItemToBeer(response)

    expectThat(beer.styleName).isEqualTo("Stout")
    expectThat(beer.breweryName).isEqualTo("ChuckleCraft Brewery")
    expectThat(beer.srm).isEqualTo(9)
    expectThat(beer.releasedYear).isEqualTo(1980)
    expectThat(beer.minServingTemperature).isEqualTo(1)
    expectThat(beer.maxServingTemperature).isEqualTo(9)
    expectThat(beer.fermentationMethod).isEqualTo("Lager")
    expectThat(beer.ingredients).isEqualTo(listOf("Dark malt"))
    expectThat(beer.recommendedGlasses).isEqualTo(listOf("Chalice"))
  }

  @Test
  fun `named-entity fields fall back to en then to whatever translation exists`() {
    languageCode = "fr"
    val fallsBackToEn =
      fullResponse().copy(fermentationMethod = namedEntity("en" to "Lager", "it" to "Lager IT"))
    val fallsBackToFirst = fullResponse().copy(fermentationMethod = namedEntity("it" to "Lager IT"))

    expectThat(mapper.fromBeersApiResponseItemToBeer(fallsBackToEn).fermentationMethod)
      .isEqualTo("Lager")
    expectThat(mapper.fromBeersApiResponseItemToBeer(fallsBackToFirst).fermentationMethod)
      .isEqualTo("Lager IT")
  }

  @Test
  fun `detail fields default to empty when the embedded objects are absent`() {
    val beer = mapper.fromBeersApiResponseItemToBeer(fullResponse())

    expectThat(beer.styleName).isEqualTo("")
    expectThat(beer.breweryName).isEqualTo("")
    expectThat(beer.srm).isEqualTo(null)
    expectThat(beer.fermentationMethod).isEqualTo("")
    expectThat(beer.ingredients).isEqualTo(emptyList())
    expectThat(beer.recommendedGlasses).isEqualTo(emptyList())
  }

  private fun namedEntity(vararg nameByLanguage: Pair<String, String>) =
    NamedEntity(
      translations = nameByLanguage.map { (lang, name) -> NamedTranslation(name, Language(lang)) }
    )

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
  fun `fromBeerToBeerDbModel and back round-trips the detail fields`() {
    val beer =
      Beer(
        id = "1",
        name = "Buzz",
        tagline = "",
        description = "",
        imageUrl = "",
        abv = 4.5,
        ibu = 60.0,
        foodPairing = emptyList(),
        styleName = "Stout",
        breweryName = "ChuckleCraft Brewery",
        srm = 9,
        releasedYear = 1980,
        minServingTemperature = 1,
        maxServingTemperature = 9,
        fermentationMethod = "Lager",
        ingredients = listOf("Dark malt", "Roasted barley"),
        recommendedGlasses = listOf("Chalice"),
      )

    val roundTripped = mapper.fromBeerDbModelToBeer(mapper.fromBeerToBeerDbModel(beer))

    expectThat(roundTripped).isEqualTo(beer)
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
