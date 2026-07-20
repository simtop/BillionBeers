package com.simtop.beer_data.mappers

import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_database.utils.Converters
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.BreweryApiResponseItem
import com.simtop.beer_network.models.NamedEntity
import com.simtop.beer_network.models.TypologyApiResponseItem
import com.simtop.beer_network.network.BeersService
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.Logger
import dev.zacsweers.metro.Inject

class BeersMapper
@Inject
constructor(private val languageProvider: LanguageProvider, private val logger: Logger) {

  fun fromBeersApiResponseItemToBeer(response: BeersApiResponseItem?): Beer {
    val languageCode = languageProvider.currentLanguageCode()
    val translation =
      response?.translations?.find { it.language.code == languageCode }
        ?: response?.translations?.find { it.language.code == BeersService.DEFAULT_LANGUAGE_CODE }
    if (response?.id == null) logMissingField("id", response)
    if (response?.name == null) logMissingField("name", response)
    if (translation == null) logMissingField("translations (matching language)", response)

    return Beer(
      id = response?.id ?: "",
      name = response?.name ?: "",
      tagline = translation?.slogan ?: "",
      description = translation?.description ?: "",
      imageUrl = response?.image?.url ?: "",
      abv = response?.abv ?: 0.0,
      ibu = response?.ibu ?: 0.0,
      foodPairing = response?.foodPairing ?: emptyList(),
      // Seeds the row's initial availability on first insert only (the keyed upsert never rewrites
      // availability for an existing id, so a user's later edit survives refresh).
      availability = response?.available ?: true,
      styleName = response?.typology?.name ?: "",
      breweryName = response?.brewery?.name ?: "",
      srm = response?.srm,
      releasedYear = response?.releasedYear,
      minServingTemperature = response?.minServingTemperature,
      maxServingTemperature = response?.maxServingTemperature,
      fermentationMethod = response?.fermentationMethod?.localizedName(languageCode) ?: "",
      ingredients = response?.ingredients.localizedNames(languageCode),
      recommendedGlasses = response?.recommendedGlasses.localizedNames(languageCode),
    )
  }

  /**
   * The display name in the requested language, falling back to the default language and then to
   * whatever translation exists - the same tolerance the slogan/description lookup applies.
   */
  private fun NamedEntity.localizedName(languageCode: String): String {
    val translations = translations.orEmpty()
    val match =
      translations.find { it.language?.code == languageCode }
        ?: translations.find { it.language?.code == BeersService.DEFAULT_LANGUAGE_CODE }
        ?: translations.firstOrNull()
    return match?.name ?: ""
  }

  private fun List<NamedEntity>?.localizedNames(languageCode: String): List<String> =
    orEmpty().map { it.localizedName(languageCode) }.filter { it.isNotEmpty() }

  private fun logMissingField(field: String, response: BeersApiResponseItem?) {
    logger.warn(TAG, "missing $field for beer id=${response?.id}, defaulting")
  }

  fun fromTypologyToBeerStyle(response: TypologyApiResponseItem): BeerStyle {
    if (response.id == null || response.name == null) {
      logger.warn(TAG, "missing id or name for typology id=${response.id}, defaulting")
    }
    return BeerStyle(id = response.id ?: "", name = response.name ?: "")
  }

  fun fromBreweryApiResponseItemToBrewery(response: BreweryApiResponseItem): Brewery {
    if (response.id == null || response.name == null) {
      logger.warn(TAG, "missing id or name for brewery id=${response.id}, defaulting")
    }
    return Brewery(
      id = response.id ?: "",
      name = response.name ?: "",
      countryCode = response.country?.code ?: "",
      foundedYear = response.foundedYear,
      imageUrl = response.image?.url ?: "",
    )
  }

  fun fromBeerToBeerDbModel(beer: Beer) =
    BeerDbModel(
      id = beer.id,
      name = beer.name,
      tagline = beer.tagline,
      description = beer.description,
      imageUrl = beer.imageUrl,
      abv = beer.abv,
      ibu = beer.ibu,
      foodPairing = Converters.listToJson(beer.foodPairing),
      availability = beer.availability,
      styleName = beer.styleName,
      breweryName = beer.breweryName,
      srm = beer.srm,
      releasedYear = beer.releasedYear,
      minServingTemperature = beer.minServingTemperature,
      maxServingTemperature = beer.maxServingTemperature,
      fermentationMethod = beer.fermentationMethod,
      ingredients = Converters.listToJson(beer.ingredients),
      recommendedGlasses = Converters.listToJson(beer.recommendedGlasses),
    )

  fun fromBeerDbModelToBeer(beerDbModel: BeerDbModel) =
    Beer(
      id = beerDbModel.id,
      name = beerDbModel.name,
      tagline = beerDbModel.tagline,
      description = beerDbModel.description,
      imageUrl = beerDbModel.imageUrl,
      abv = beerDbModel.abv,
      ibu = beerDbModel.ibu,
      foodPairing = Converters.jsonToList(beerDbModel.foodPairing),
      availability = beerDbModel.availability,
      styleName = beerDbModel.styleName,
      breweryName = beerDbModel.breweryName,
      srm = beerDbModel.srm,
      releasedYear = beerDbModel.releasedYear,
      minServingTemperature = beerDbModel.minServingTemperature,
      maxServingTemperature = beerDbModel.maxServingTemperature,
      fermentationMethod = beerDbModel.fermentationMethod,
      ingredients = Converters.jsonToList(beerDbModel.ingredients),
      recommendedGlasses = Converters.jsonToList(beerDbModel.recommendedGlasses),
    )

  private companion object {
    const val TAG = "BeersMapper"
  }
}
