package com.simtop.beer_data.repositories

import com.simtop.beer_network.network.BeersServiceHttpException
import com.simtop.beer_network.network.BeersServiceNetworkException
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.core.core.NoOpLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FetchBeersErrorsTest {

  private val logger = NoOpLogger()

  @Test
  fun `maps known HTTP statuses`() {
    assertEquals(FetchBeersError.NotFound, httpException(404).toFetchBeersError(logger))
    assertEquals(FetchBeersError.Forbidden, httpException(403).toFetchBeersError(logger))
    assertEquals(FetchBeersError.RateLimited, httpException(429).toFetchBeersError(logger))
  }

  @Test
  fun `maps transport failures to Network`() {
    assertEquals(FetchBeersError.Network, BeersServiceNetworkException().toFetchBeersError(logger))
  }

  @Test
  fun `preserves unknown causes`() {
    val http = httpException(400)
    val other = IllegalStateException("unexpected")

    assertSame(http, (http.toFetchBeersError(logger) as FetchBeersError.Unknown).cause)
    assertSame(other, (other.toFetchBeersError(logger) as FetchBeersError.Unknown).cause)
  }

  private fun httpException(code: Int) = BeersServiceHttpException(code)
}
