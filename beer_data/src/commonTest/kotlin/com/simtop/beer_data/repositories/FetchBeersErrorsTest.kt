package com.simtop.beer_data.repositories

import com.simtop.beer_network.network.BeersServiceHttpException
import com.simtop.beer_network.network.BeersServiceNetworkException
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.core.core.Diagnostic
import com.simtop.core.core.DiagnosticArea
import com.simtop.core.core.DiagnosticCode
import com.simtop.core.core.DiagnosticLevel
import com.simtop.core.core.LogTag
import com.simtop.core.core.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

class FetchBeersErrorsTest {

  @Test
  fun `maps known HTTP statuses`() {
    assertEquals(FetchBeersError.NotFound, httpException(404).toFetchBeersError())
    assertEquals(FetchBeersError.Forbidden, httpException(403).toFetchBeersError())
    assertEquals(FetchBeersError.RateLimited, httpException(429).toFetchBeersError())
    listOf(500, 503, 599).forEach { code ->
      assertEquals(FetchBeersError.Server, httpException(code).toFetchBeersError())
    }
  }

  @Test
  fun `maps transport and serialization failures`() {
    assertEquals(FetchBeersError.Network, BeersServiceNetworkException().toFetchBeersError())
    assertEquals(
      FetchBeersError.Parse,
      SerializationException("invalid payload").toFetchBeersError(),
    )
  }

  @Test
  fun `preserves unknown causes and excludes non-server statuses`() {
    val failures = listOf(400, 499, 600).map(::httpException) + IllegalStateException("unexpected")
    failures.forEach { failure ->
      assertSame(failure, (failure.toFetchBeersError() as FetchBeersError.Unknown).cause)
    }
  }

  @Test
  fun `remote reporting uses the exact bounded code and severity once`() {
    val cases =
      listOf(
        BeersServiceNetworkException() to
          (DiagnosticCode.NETWORK_UNAVAILABLE to DiagnosticLevel.WARNING),
        httpException(404) to (DiagnosticCode.HTTP_NOT_FOUND to DiagnosticLevel.INFO),
        httpException(403) to (DiagnosticCode.HTTP_FORBIDDEN to DiagnosticLevel.WARNING),
        httpException(429) to (DiagnosticCode.RATE_LIMITED to DiagnosticLevel.WARNING),
        httpException(503) to (DiagnosticCode.SERVER_FAILURE to DiagnosticLevel.ERROR),
        httpException(400) to (DiagnosticCode.UNKNOWN to DiagnosticLevel.ERROR),
        IllegalStateException("private failure") to
          (DiagnosticCode.UNKNOWN to DiagnosticLevel.ERROR),
      )
    cases.forEach { (failure, metadata) ->
      assertReported(
        failure,
        FetchFailureOrigin.REMOTE,
        Diagnostic(DiagnosticArea.NETWORK, metadata.first, metadata.second),
      )
    }
    assertReported(
      SerializationException("private payload"),
      FetchFailureOrigin.REMOTE,
      Diagnostic(DiagnosticArea.DATA_MAPPING, DiagnosticCode.PARSE_FAILURE, DiagnosticLevel.ERROR),
    )
  }

  @Test
  fun `mapping and storage reporting retain their origin regardless of exception type`() {
    assertReported(
      IllegalStateException("mapping"),
      FetchFailureOrigin.MAPPING,
      Diagnostic(DiagnosticArea.DATA_MAPPING, DiagnosticCode.UNKNOWN, DiagnosticLevel.ERROR),
    )
    assertReported(
      SerializationException("mapping"),
      FetchFailureOrigin.MAPPING,
      Diagnostic(DiagnosticArea.DATA_MAPPING, DiagnosticCode.PARSE_FAILURE, DiagnosticLevel.ERROR),
    )
    listOf(IllegalStateException("storage"), SerializationException("storage")).forEach { failure ->
      assertReported(
        failure,
        FetchFailureOrigin.STORAGE,
        Diagnostic(
          DiagnosticArea.DATABASE,
          DiagnosticCode.LOCAL_STORAGE_FAILURE,
          DiagnosticLevel.ERROR,
        ),
      )
    }
  }

  @Test
  fun `successful operations are returned without reporting`() {
    val entries = mutableListOf<Diagnostic>()
    val logger = Logger { _, diagnostic -> entries += diagnostic }
    assertEquals(42, withFetchDiagnostics(logger, FetchFailureOrigin.REMOTE) { 42 })
    assertEquals(emptyList(), entries)
  }

  @Test
  fun `cancellation escapes without reporting from every origin`() {
    FetchFailureOrigin.entries.forEach { origin ->
      val entries = mutableListOf<Diagnostic>()
      val logger = Logger { _, diagnostic -> entries += diagnostic }
      val cancellation = CancellationException("cancel")
      assertSame(
        cancellation,
        assertFailsWith<CancellationException> {
          withFetchDiagnostics(logger, origin) { throw cancellation }
        },
      )
      assertEquals(emptyList(), entries)
    }
  }

  @Test
  fun `reporter failure does not replace the original operation failure`() {
    val failure = IllegalStateException("original")
    val logger = Logger { _, _ -> throw IllegalArgumentException("adapter") }
    assertSame(
      failure,
      assertFailsWith<IllegalStateException> {
        withFetchDiagnostics(logger, FetchFailureOrigin.REMOTE) { throw failure }
      },
    )
  }

  private fun assertReported(failure: Exception, origin: FetchFailureOrigin, expected: Diagnostic) {
    val entries = mutableListOf<Pair<LogTag, Diagnostic>>()
    val logger = Logger { tag, diagnostic -> entries += tag to diagnostic }
    assertSame(
      failure,
      assertFailsWith<Exception> {
        withFetchDiagnostics(logger, origin) { throw failure }
      },
    )
    assertEquals(listOf(origin.tag to expected), entries)
  }

  private fun httpException(code: Int) = BeersServiceHttpException(code)
}
