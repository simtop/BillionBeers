package com.simtop.beer_data.repositories

import com.simtop.beerdomain.domain.errors.FetchBeersError
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response

class FetchBeersErrorsTest {

  @Test
  fun `maps known HTTP statuses`() {
    assertEquals(FetchBeersError.NotFound, httpException(404).toFetchBeersError())
    assertEquals(FetchBeersError.Forbidden, httpException(403).toFetchBeersError())
    assertEquals(FetchBeersError.RateLimited, httpException(429).toFetchBeersError())
  }

  @Test
  fun `maps IO failures to Network`() {
    assertEquals(FetchBeersError.Network, IOException("offline").toFetchBeersError())
  }

  @Test
  fun `preserves unknown causes`() {
    val http = httpException(500)
    val other = IllegalStateException("unexpected")

    assertSame(http, (http.toFetchBeersError() as FetchBeersError.Unknown).cause)
    assertSame(other, (other.toFetchBeersError() as FetchBeersError.Unknown).cause)
  }

  private fun httpException(code: Int) =
    HttpException(Response.error<Unit>(code, "".toResponseBody(null)))
}
