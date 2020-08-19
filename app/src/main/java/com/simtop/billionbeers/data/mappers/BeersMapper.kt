package com.simtop.billionbeers.data.mappers

import com.simtop.billionbeers.data.models.BeersApiResponseItem
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
}