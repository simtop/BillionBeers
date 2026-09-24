package com.simtop.billionbeers.composefixture

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.delay

@OptIn(ExperimentalTestApi::class)
class ComposeFixtureWasmRenderTest {
  @Test
  fun browserRendersFocusesAndDeliversBufferedEvent() = runComposeUiTest {
    val viewModel = ComposeFixtureViewModel()
    var receivedEvents = 0

    setContent {
      ComposeFixtureScreen(
        viewModel = viewModel,
        onEvent = { receivedEvents++ },
        title = "Shared Compose fixture",
        inputLabel = "Focus input",
        submitLabel = "Submit event",
      )
    }

    waitForIdle()
    delay(250)
    waitForIdle()
    onNodeWithText("Shared Compose fixture").assertIsDisplayed()
    onNodeWithTag("compose-fixture-input").assertIsFocused().performTextInput("browser")
    onNodeWithText("Submit event").performClick()
    waitForIdle()

    assertEquals("browser", viewModel.state.value.text)
    assertEquals(1, receivedEvents)
    viewModel.dispose()
  }
}
