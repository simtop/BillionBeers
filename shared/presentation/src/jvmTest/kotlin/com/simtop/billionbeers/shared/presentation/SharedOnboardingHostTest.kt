package com.simtop.billionbeers.shared.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@OptIn(ExperimentalTestApi::class)
class SharedOnboardingHostTest {
  @Test
  fun delayedReadGatesWelcomeThenCompletionSurvivesANewController() =
    runSkikoComposeUiTest(size = Size(390f, 844f)) {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
      val read = CompletableDeferred<Set<String>>()
      val store = TestStore(read)
      var controller by mutableStateOf(OnboardingController(store, scope))
      val strings = OnboardingStrings.forLanguage("en")
      try {
        setContent {
          BillionBeersTheme(darkTheme = false) {
            SharedOnboardingHost(controller, welcome = { SharedWelcomeScreen(strings, it) }) {
              Text("Catalog")
            }
          }
        }
        onNodeWithText(strings.headline).assertDoesNotExist()
        onNodeWithText("Catalog").assertDoesNotExist()
        runOnIdle { read.complete(emptySet()) }
        onNodeWithText(strings.headline).assertIsDisplayed()
        val file = File("build/qa/onboarding-phone.png").apply { parentFile.mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        onNodeWithText(strings.next).performClick()
        onNodeWithText(strings.collectionHeadline).assertIsDisplayed()
        onNodeWithText(strings.explore).performClick()
        onNodeWithText("Catalog").assertIsDisplayed()
        runOnIdle { controller = OnboardingController(store, scope) }
        onNodeWithText("Catalog").assertIsDisplayed()
        onNodeWithText(strings.headline).assertDoesNotExist()
        assertEquals(setOf("welcome"), store.saved)
      } finally {
        scope.cancel()
      }
    }

  @Test
  fun bypassDuringLoadingShowsContentWithoutClaimingOrCompletingAnIntroduction() =
    runSkikoComposeUiTest(size = Size(1200f, 900f)) {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
      val read = CompletableDeferred<Set<String>>()
      val store = TestStore(read)
      val controller = OnboardingController(store, scope)
      var bypass by mutableStateOf(true)
      val strings = OnboardingStrings.forLanguage("fr")
      try {
        setContent {
          BillionBeersTheme(darkTheme = false) {
            SharedOnboardingHost(
              controller,
              bypassWelcome = bypass,
              welcome = { SharedWelcomeScreen(strings, it) },
            ) {
              Text(
                if (LocalOnboardingHints.current == null) "Link without hints"
                else "Unexpected hints"
              )
            }
          }
        }
        onNodeWithText("Link without hints").assertIsDisplayed()
        runOnIdle { read.complete(emptySet()) }
        onNodeWithText("Link without hints").assertIsDisplayed()
        assertFalse(
          checkNotNull(controller.state.value).isComplete(OnboardingIntroductions.welcome)
        )
        runOnIdle { bypass = false }
        onNodeWithText(strings.headline).assertIsDisplayed()
        val file = File("build/qa/onboarding-wide-fr.png").apply { parentFile.mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        assertEquals(emptySet(), store.saved)
      } finally {
        scope.cancel()
      }
    }

  private class TestStore(private val initialRead: CompletableDeferred<Set<String>>) :
    OnboardingStore {
    var saved: Set<String> = emptySet()
    private var loaded = false

    override suspend fun read(): Set<String> =
      if (loaded) saved
      else
        initialRead.await().also {
          saved = it
          loaded = true
        }

    override suspend fun write(completedIds: Set<String>) {
      saved = completedIds
    }
  }
}
