package com.simtop.beer_data.repositories

import com.simtop.beer_network.network.BeersServiceHttpException
import com.simtop.beer_network.network.BeersServiceNetworkException
import com.simtop.beerdomain.domain.errors.FetchBeersError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FetchBeersErrorsTest {

  @Test
  fun `maps known HTTP statuses`() {
    assertEquals(FetchBeersError.NotFound, httpException(404).toFetchBeersError())
    assertEquals(FetchBeersError.Forbidden, httpException(403).toFetchBeersError())
    assertEquals(FetchBeersError.RateLimited, httpException(429).toFetchBeersError())
  }

  @Test
  fun `maps transport failures to Network`() {
    assertEquals(FetchBeersError.Network, BeersServiceNetworkException().toFetchBeersError())
  }

  @Test
  fun `preserves unknown causes`() {
    val http = httpException(500)
    val other = IllegalStateException("unexpected")

    assertSame(http, (http.toFetchBeersError() as FetchBeersError.Unknown).cause)
    assertSame(other, (other.toFetchBeersError() as FetchBeersError.Unknown).cause)
  }

  private fun httpException(code: Int) = BeersServiceHttpException(code)
}
