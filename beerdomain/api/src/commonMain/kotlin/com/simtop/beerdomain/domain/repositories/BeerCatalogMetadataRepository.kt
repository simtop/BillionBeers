package com.simtop.beerdomain.domain.repositories

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.core.core.Either

/** Reads the unpaged style and brewery catalogs used to build beer queries. */
interface BeerCatalogMetadataRepository {
  /** All beer styles, unpaged - a list this small never needs a pager. */
  suspend fun getBeerStyles(): Either<FetchBeersError, List<BeerStyle>>

  /** All breweries, unpaged - same reasoning as [getBeerStyles]. */
  suspend fun getBreweries(): Either<FetchBeersError, List<Brewery>>
}
