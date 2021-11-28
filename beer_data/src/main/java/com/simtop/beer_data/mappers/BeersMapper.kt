package com.simtop.beer_data.mappers

import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beer_database.utils.Converters

object BeersMapper {

    fun fromBeersApiResponseItemToBeer(response: BeersApiResponseItem?): Beer =
        Beer(
            response?.id ?: 0,
            response?.name ?: "",
            response?.tagline ?: "",
            response?.description ?: "",
            response?.imageUrl ?: "",
            response?.abv ?: 0.0,
            response?.ibu ?: 0.0,
            response?.foodPairing ?: emptyList()
        )

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