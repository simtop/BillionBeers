package com.simtop.billionbeers.t71

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class T71ViewModelTest {
  @Test
  fun submitUpdatesStateAndEmitsOneEvent() = runTest {
    val viewModel = T71ViewModel()
    val event = launch {
      assertEquals(T71Event.Submitted, viewModel.events.first())
    }

    viewModel.updateText("hello")
    viewModel.submit()
    event.join()

    assertEquals(T71UiState(text = "hello", submissionCount = 1), viewModel.state.value)
    viewModel.dispose()
  }

  @Test
  fun clearingViewModelClosesItsEventStream() = runTest {
    val viewModel = T71ViewModel()
    viewModel.dispose()

    assertEquals(null, viewModel.events.firstOrNull())
  }
}
