package com.simtop.billionbeers.shared.presentation

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.core.core.CommonUiErrorKey
import com.simtop.core.core.CommonUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class FetchBeersErrorMappingTest {

  @Test
  fun `maps every known fetch error to a semantic error key`() {
    val cases =
      listOf(
        FetchBeersError.Network to CommonUiErrorKey.NoInternet,
        FetchBeersError.NotFound to CommonUiErrorKey.NoBeersFound,
        FetchBeersError.Forbidden to CommonUiErrorKey.AccessDenied,
        FetchBeersError.RateLimited to CommonUiErrorKey.RateLimited,
        FetchBeersError.Unknown(IllegalStateException("private failure")) to
          CommonUiErrorKey.FailedToLoadBeers,
      )

    cases.forEach { (error, expectedKey) ->
      assertEquals(
        CommonUiState.Error(errorKey = expectedKey),
        error.toCommonUiErrorState(),
      )
    }
  }

  @Test
  fun `does not expose unknown failure message as product copy`() {
    val state =
      FetchBeersError.Unknown(IllegalStateException("private failure")).toCommonUiErrorState()

    assertEquals(null, state.message)
    assertEquals(CommonUiErrorKey.FailedToLoadBeers, state.errorKey)
  }
}
