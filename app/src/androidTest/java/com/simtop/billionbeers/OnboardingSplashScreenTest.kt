package com.simtop.billionbeers

import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.di.BaseAppGraph
import com.simtop.billionbeers.di.FakeBeersRepositoryModule
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.shared.presentation.OnboardingController
import com.simtop.billionbeers.shared.presentation.OnboardingStore
import com.simtop.testing_utils_android.withFailureScreenshot
import dev.zacsweers.metro.createGraphFactory
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingSplashScreenTest {
  private val compose = createEmptyComposeRule()
  @get:Rule val failureScreenshots = withFailureScreenshot(compose)

  @Test
  fun ordinaryLaunchWaitsForPreferencesBeforeDrawingWelcome() = withPendingRead { read, _ ->
    assertFirstDrawWaitsForRead(read, Result.success(emptySet())) {
      compose.onNodeWithText("Find your next favorite.").assertIsDisplayed()
    }
  }

  @Test
  fun readFailureReleasesSplashAndOpensCatalog() = withPendingRead { read, _ ->
    assertFirstDrawWaitsForRead(read, Result.failure(IOException("unavailable"))) {
      compose.onNodeWithText(STARTUP_BEER_NAME).assertIsDisplayed()
    }
  }

  @Test
  fun deepLinkDrawsWhilePreferencesAreStillLoading() = withPendingRead { read, controller ->
    withStartingActivity(read, favoritesIntent()) { _, drawn ->
      assertTrue(
        "Deep links must draw without waiting for onboarding storage",
        drawn.await(DRAW_TIMEOUT_SECONDS, TimeUnit.SECONDS),
      )
      assertNull("The preference read must still be suspended", controller.state.value)
      compose.onNodeWithTag("favorites_tab").assertIsSelected()
      compose.onNodeWithTag("introduction_hint").assertDoesNotExist()
    }
  }

  @Test
  fun incomingDeepLinkReleasesSplashWhilePreferencesAreStillLoading() =
    withPendingRead { read, controller ->
      withStartingActivity(read) { activity, drawn ->
        assertFalse(
          "Ordinary startup must still be waiting",
          drawn.await(OBSERVATION_MILLIS, TimeUnit.MILLISECONDS),
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
          val originalIntent = activity.intent
          InstrumentationRegistry.getInstrumentation()
            .callActivityOnNewIntent(activity, favoritesIntent())
          // Keep ActivityScenario's launch identity intact for lifecycle tracking and cleanup.
          activity.intent = originalIntent
        }
        assertTrue(
          "A delivered deep link must release the splash",
          drawn.await(DRAW_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        assertNull(controller.state.value)
        compose.onNodeWithTag("favorites_tab").assertIsSelected()
      }
    }

  private fun favoritesIntent() =
    Intent(
      Intent.ACTION_VIEW,
      Uri.parse("billionbeers://favorites"),
      InstrumentationRegistry.getInstrumentation().targetContext,
      MainActivity::class.java,
    )

  private fun assertFirstDrawWaitsForRead(
    read: CompletableDeferred<Set<String>>,
    result: Result<Set<String>>,
    assertContent: () -> Unit,
  ) {
    withStartingActivity(read) { _, drawn ->
      assertFalse(
        "Activity content drew before onboarding preferences resolved",
        drawn.await(OBSERVATION_MILLIS, TimeUnit.MILLISECONDS),
      )
      result.fold(read::complete, read::completeExceptionally)
      assertTrue(
        "Resolved preferences must release the first Activity draw",
        drawn.await(DRAW_TIMEOUT_SECONDS, TimeUnit.SECONDS),
      )
      assertContent()
    }
  }

  private fun withStartingActivity(
    read: CompletableDeferred<Set<String>>,
    intent: Intent =
      Intent(InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java),
    block: (MainActivity, CountDownLatch) -> Unit,
  ) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val resumed = CountDownLatch(1)
    val drawn = CountDownLatch(1)
    val activity = AtomicReference<MainActivity>()
    val content = AtomicReference<View>()
    val drawListener = ViewTreeObserver.OnDrawListener { drawn.countDown() }
    val monitor = ActivityLifecycleMonitorRegistry.getInstance()
    val lifecycle = ActivityLifecycleCallback { current, stage ->
      if (
        current is MainActivity && stage == Stage.RESUMED && activity.compareAndSet(null, current)
      ) {
        content.set(current.findViewById(android.R.id.content))
        content.get().viewTreeObserver.addOnDrawListener(drawListener)
        resumed.countDown()
      }
    }
    instrumentation.runOnMainSync { monitor.addLifecycleCallback(lifecycle) }
    // ActivityScenario's synchronous launch/access can wait for drawing/idle. The test must be
    // able to release storage independently while the splash deliberately prevents those draws.
    val launcher = Executors.newSingleThreadExecutor()
    val launch = launcher.submit<ActivityScenario<MainActivity>> { ActivityScenario.launch(intent) }
    try {
      assertTrue(
        "Activity must resume while the read is suspended",
        resumed.await(DRAW_TIMEOUT_SECONDS, TimeUnit.SECONDS),
      )
      instrumentation.runOnMainSync { content.get().postInvalidateOnAnimation() }
      block(checkNotNull(activity.get()), drawn)
    } finally {
      read.complete(emptySet())
      instrumentation.runOnMainSync {
        content.get()?.viewTreeObserver?.removeOnDrawListener(drawListener)
        monitor.removeLifecycleCallback(lifecycle)
      }
      try {
        launch.get(DRAW_TIMEOUT_SECONDS, TimeUnit.SECONDS).close()
      } finally {
        launcher.shutdownNow()
      }
    }
  }

  private fun withPendingRead(
    block: (CompletableDeferred<Set<String>>, OnboardingController) -> Unit
  ) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val app = instrumentation.targetContext.applicationContext as BillionBeersApplication
    val previousGraph = app.appGraph
    val repository = FakeBeersRepositoryModule.fakeBeersRepository
    val previousBeers = repository.getBeers()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val read = CompletableDeferred<Set<String>>()
    val controller =
      OnboardingController(
        object : OnboardingStore {
          override suspend fun read() = read.await()

          override suspend fun write(completedIds: Set<String>) = Unit
        },
        scope,
      )
    val graph = createGraphFactory<TestAppGraph.Factory>().create(context = app)
    repository.setBeers(listOf(Beer.empty.copy(id = "startup-beer", name = STARTUP_BEER_NAME)))
    app.activateAppGraph(
      object : BaseAppGraph by graph {
        override val onboardingController = controller
      }
    )
    try {
      block(read, controller)
    } finally {
      read.complete(emptySet())
      scope.cancel()
      repository.setBeers(previousBeers)
      app.activateAppGraph(previousGraph)
    }
  }

  private companion object {
    const val OBSERVATION_MILLIS = 300L
    const val DRAW_TIMEOUT_SECONDS = 5L
    const val STARTUP_BEER_NAME = "Startup test beer"
  }
}
