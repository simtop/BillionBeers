package com.simtop.beer_data.mappers

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.BreweryApiResponseItem
import com.simtop.beer_network.models.NamedEntity
import com.simtop.beer_network.models.TypologyApiResponseItem
import com.simtop.beer_network.network.BeersService
import com.simtop.beer_storage.api.StoredBeer
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.core.core.Diagnostic
import com.simtop.core.core.DiagnosticArea
import com.simtop.core.core.DiagnosticCode
import com.simtop.core.core.DiagnosticLevel
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.LogTag
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
    if (response?.id == null) logger.warn(LogTag.BEERS_MAPPER, MISSING_BEER_ID)
    if (response?.name == null) logger.warn(LogTag.BEERS_MAPPER, MISSING_BEER_NAME)
    if (translation == null) logger.warn(LogTag.BEERS_MAPPER, MISSING_BEER_TRANSLATION)

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
      isFavorite = false,
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

  fun fromTypologyToBeerStyle(response: TypologyApiResponseItem): BeerStyle {
    if (response.id == null || response.name == null) {
      logger.warn(LogTag.BEERS_MAPPER, MISSING_STYLE_ID_OR_NAME)
    }
    return BeerStyle(id = response.id ?: "", name = response.name ?: "")
  }

  fun fromBreweryApiResponseItemToBrewery(response: BreweryApiResponseItem): Brewery {
    if (response.id == null || response.name == null) {
      logger.warn(LogTag.BEERS_MAPPER, MISSING_BREWERY_ID_OR_NAME)
    }
    return Brewery(
      id = response.id ?: "",
      name = response.name ?: "",
      countryCode = response.country?.code ?: "",
      foundedYear = response.foundedYear,
      imageUrl = response.image?.url ?: "",
    )
  }

  fun fromBeerToStoredBeer(beer: Beer) =
    StoredBeer(
      id = beer.id,
      name = beer.name,
      tagline = beer.tagline,
      description = beer.description,
      imageUrl = beer.imageUrl,
      abv = beer.abv,
      ibu = beer.ibu,
      foodPairing = beer.foodPairing,
      availability = beer.availability,
      isFavorite = beer.isFavorite,
      styleName = beer.styleName,
      breweryName = beer.breweryName,
      srm = beer.srm,
      releasedYear = beer.releasedYear,
      minServingTemperature = beer.minServingTemperature,
      maxServingTemperature = beer.maxServingTemperature,
      fermentationMethod = beer.fermentationMethod,
      ingredients = beer.ingredients,
      recommendedGlasses = beer.recommendedGlasses,
    )

  fun fromStoredBeerToBeer(storedBeer: StoredBeer) =
    Beer(
      id = storedBeer.id,
      name = storedBeer.name,
      tagline = storedBeer.tagline,
      description = storedBeer.description,
      imageUrl = storedBeer.imageUrl,
      abv = storedBeer.abv,
      ibu = storedBeer.ibu,
      foodPairing = storedBeer.foodPairing,
      availability = storedBeer.availability,
      isFavorite = storedBeer.isFavorite,
      styleName = storedBeer.styleName,
      breweryName = storedBeer.breweryName,
      srm = storedBeer.srm,
      releasedYear = storedBeer.releasedYear,
      minServingTemperature = storedBeer.minServingTemperature,
      maxServingTemperature = storedBeer.maxServingTemperature,
      fermentationMethod = storedBeer.fermentationMethod,
      ingredients = storedBeer.ingredients,
      recommendedGlasses = storedBeer.recommendedGlasses,
    )

  private companion object {
    val MISSING_BEER_ID =
      Diagnostic(
        DiagnosticArea.DATA_MAPPING,
        DiagnosticCode.MISSING_BEER_ID,
        DiagnosticLevel.WARNING,
      )
    val MISSING_BEER_NAME =
      Diagnostic(
        DiagnosticArea.DATA_MAPPING,
        DiagnosticCode.MISSING_BEER_NAME,
        DiagnosticLevel.WARNING,
      )
    val MISSING_BEER_TRANSLATION =
      Diagnostic(
        DiagnosticArea.DATA_MAPPING,
        DiagnosticCode.MISSING_BEER_TRANSLATION,
        DiagnosticLevel.WARNING,
      )
    val MISSING_STYLE_ID_OR_NAME =
      Diagnostic(
        DiagnosticArea.DATA_MAPPING,
        DiagnosticCode.MISSING_STYLE_ID_OR_NAME,
        DiagnosticLevel.WARNING,
      )
    val MISSING_BREWERY_ID_OR_NAME =
      Diagnostic(
        DiagnosticArea.DATA_MAPPING,
        DiagnosticCode.MISSING_BREWERY_ID_OR_NAME,
        DiagnosticLevel.WARNING,
      )
  }
}
