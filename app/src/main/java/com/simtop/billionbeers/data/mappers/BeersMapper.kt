package com.simtop.billionbeers.data.mappers

import com.simtop.billionbeers.data.models.BeerDbModel
import com.simtop.billionbeers.data.models.BeersApiResponseItem
import com.simtop.billionbeers.data.util.Converters
import com.simtop.billionbeers.domain.models.Beer

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