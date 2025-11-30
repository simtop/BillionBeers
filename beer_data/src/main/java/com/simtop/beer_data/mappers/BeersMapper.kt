package com.simtop.beer_data.mappers

import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beer_database.utils.Converters

object BeersMapper {

    fun fromBeersApiResponseItemToBeer(response: BeersApiResponseItem?): Beer {
        val translation = response?.translations?.find { it.language.code == "en" }
        val imageUrl = response?.imageId?.let { "https://brewbuddy.dev/images/$it" }

        return Beer(
            id = response?.id ?: "",
            name = response?.name ?: "",
            tagline = translation?.slogan ?: "",
            description = translation?.description ?: "",
            imageUrl = imageUrl ?: "",
            abv = response?.abv ?: 0.0,
            ibu = response?.ibu ?: 0.0,
            foodPairing = response?.foodPairing ?: emptyList()
        )
    }

    fun fromBeerToBeerDbModel(beer : Beer) =
        BeerDbModel(
            beer.id,
            beer.name,
            beer.tagline,
            beer.description,
            beer.imageUrl,
            beer.abv,
            beer.ibu,
            Converters.listToJson(beer.foodPairing),
            beer.availability
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
            beerDbModel.availability
        )
}