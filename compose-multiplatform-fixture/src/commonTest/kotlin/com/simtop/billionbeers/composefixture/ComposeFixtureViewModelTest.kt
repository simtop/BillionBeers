package com.simtop.billionbeers.composefixture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class ComposeFixtureViewModelTest {
  @Test
  fun submitUpdatesStateAndEmitsOneEvent() = runTest {
    val viewModel = ComposeFixtureViewModel()
    val event = launch {
      assertEquals(ComposeFixtureEvent.Submitted, viewModel.events.first())
    }

    viewModel.updateText("hello")
    viewModel.submit()
    event.join()

    assertEquals(ComposeFixtureUiState(text = "hello", submissionCount = 1), viewModel.state.value)
    viewModel.dispose()
  }

  @Test
  fun clearingViewModelClosesItsEventStream() = runTest {
    val viewModel = ComposeFixtureViewModel()
    viewModel.dispose()

    assertEquals(null, viewModel.events.firstOrNull())
  }
}
