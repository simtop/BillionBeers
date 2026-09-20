package com.simtop.core.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CommonUiStateTest {

  @Test
  fun `known error keeps its platform neutral key`() {
    val state = CommonUiState.Error(errorKey = CommonUiErrorKey.RateLimited)

    assertEquals(CommonUiErrorKey.RateLimited, state.errorKey)
    assertEquals(null, state.message)
  }

  @Test
  fun `literal message remains available for runtime failures`() {
    val state = CommonUiState.Error(message = "Request failed")

    assertEquals("Request failed", state.message)
    assertEquals(null, state.errorKey)
  }
}
