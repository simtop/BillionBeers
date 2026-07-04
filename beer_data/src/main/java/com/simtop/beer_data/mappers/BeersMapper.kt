package com.simtop.beer_data.mappers

import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_database.utils.Converters
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.network.BeersService
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.core.core.LanguageProvider
import dev.zacsweers.metro.Inject

class BeersMapper @Inject constructor(private val languageProvider: LanguageProvider) {

  fun fromBeersApiResponseItemToBeer(response: BeersApiResponseItem?): Beer {
    val languageCode = languageProvider.currentLanguageCode()
    val translation =
      response?.translations?.find { it.language.code == languageCode }
        ?: response?.translations?.find { it.language.code == BeersService.DEFAULT_LANGUAGE_CODE }
    val imageUrl = response?.imageId?.let { "https://brewbuddy.dev/images/$it" }

    if (response?.id == null) logMissingField("id", response)
    if (response?.name == null) logMissingField("name", response)
    if (translation == null) logMissingField("translations (matching language)", response)

    return Beer(
      id = response?.id ?: "",
      name = response?.name ?: "",
      tagline = translation?.slogan ?: "",
      description = translation?.description ?: "",
      imageUrl = imageUrl ?: "",
      abv = response?.abv ?: 0.0,
      ibu = response?.ibu ?: 0.0,
      foodPairing = response?.foodPairing ?: emptyList(),
    )
  }

  // TODO: route through the observability seam (Logger facade) once it lands - the codebase has
  // no logging today (docs/MASTER_PLAN.md Phase 3). System.err is a dependency-free stopgap so
  // malformed backend data isn't silently swallowed in the meantime, without dragging
  // android.util.Log's static-mock requirement into every indirect caller's unit tests.
  private fun logMissingField(field: String, response: BeersApiResponseItem?) {
    System.err.println("$TAG: missing $field for beer id=${response?.id}, defaulting")
  }

  fun fromBeerToBeerDbModel(beer: Beer) =
    BeerDbModel(
      beer.id,
      beer.name,
      beer.tagline,
      beer.description,
      beer.imageUrl,
      beer.abv,
      beer.ibu,
      Converters.listToJson(beer.foodPairing),
      beer.availability,
    )

  fun fromBeerDbModelToBeer(beerDbModel: BeerDbModel) =
    Beer(
      beerDbModel.id,
      beerDbModel.name,
      beerDbModel.tagline,
      beerDbModel.description,
      beerDbModel.imageUrl,
      beerDbModel.abv,
      beerDbModel.ibu,
      Converters.jsonToList(beerDbModel.foodPairing),
      beerDbModel.availability,
    )

  private companion object {
    const val TAG = "BeersMapper"
  }
}
