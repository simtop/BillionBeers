package com.simtop.navigation

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.navigation.contract.PortableRoute
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class RoutesTest {

  private val beer = Beer.empty.copy(id = "42", name = "Punk IPA")

  @Test
  fun `android keys map to portable route values`() {
    expectThat(BeersList.toPortableRoute()).isEqualTo(PortableRoute.BeersList)
    expectThat(Favorites.toPortableRoute()).isEqualTo(PortableRoute.Favorites)
    expectThat(BeersSearch.toPortableRoute()).isEqualTo(PortableRoute.BeersSearch)
    expectThat(BeerBrowse.toPortableRoute()).isEqualTo(PortableRoute.BeerBrowse)
    expectThat(BeerDetail(beer).toPortableRoute()).isEqualTo(PortableRoute.BeerDetail(beer))
  }

  @Test
  fun `portable detail route maps back to an android key`() {
    expectThat(PortableRoute.BeerDetail(beer).toNavKey()).isEqualTo(BeerDetail(beer))
  }

  @Test
  fun `unknown android keys do not enter the portable route contract`() {
    expectThat(UnknownKey.toPortableRoute()).isNull()
  }

  private data object UnknownKey : androidx.navigation3.runtime.NavKey
}
