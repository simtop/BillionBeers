package com.simtop.billionbeers.composefixture

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import billionbeers.compose_multiplatform_fixture.generated.resources.Res
import billionbeers.compose_multiplatform_fixture.generated.resources.compose_fixture_input_label
import billionbeers.compose_multiplatform_fixture.generated.resources.compose_fixture_submit
import billionbeers.compose_multiplatform_fixture.generated.resources.compose_fixture_title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.configureWebResources
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class ComposeFixtureWasmRenderTest {
  @Test
  fun browserRendersFocusesAndDeliversBufferedEvent() = runComposeUiTest {
    val viewModel = ComposeFixtureViewModel()
    var receivedEvents = 0
    configureWebResources { resourcePathMapping { path -> "/base/kotlin/$path" } }

    val title = getString(Res.string.compose_fixture_title)
    val inputLabel = getString(Res.string.compose_fixture_input_label)
    val submitLabel = getString(Res.string.compose_fixture_submit)
    assertTrue(title.isNotBlank())
    assertTrue(inputLabel.isNotBlank())
    assertTrue(submitLabel.isNotBlank())

    setContent {
      ComposeFixtureScreen(
        viewModel = viewModel,
        onEvent = { receivedEvents++ },
      )
    }

    waitForIdle()
    delay(250)
    waitForIdle()
    onNodeWithText(title).assertIsDisplayed()
    onNodeWithText(inputLabel).assertIsDisplayed()
    onNodeWithTag("compose-fixture-input").assertIsFocused().performTextInput("browser")
    onNodeWithText(submitLabel).performClick()
    waitForIdle()

    assertEquals("browser", viewModel.state.value.text)
    assertEquals(1, receivedEvents)
    viewModel.dispose()
  }
}
