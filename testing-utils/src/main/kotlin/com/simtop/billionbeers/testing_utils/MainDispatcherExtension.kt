package com.simtop.billionbeers.testing_utils

import com.simtop.core.core.CoroutineDispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Installs a [TestDispatcher] as `Dispatchers.Main` for the duration of a test.
 *
 * **This is the piece that gives a test control over a ViewModel's coroutines.** `viewModelScope`
 * is `SupervisorJob() + Dispatchers.Main.immediate`, and no amount of dependency injection can
 * reach it - `Dispatchers.setMain` is the only override. A ViewModel that calls a bare
 * `viewModelScope.launch { }` is therefore fully controllable from a test, which is why the
 * ViewModels here no longer take a [CoroutineDispatcherProvider] just to hand one to `launch`.
 *
 * Pass the same [testDispatcher] to `runTest` so both share one scheduler:
 * ```
 * @JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()
 *
 * @Test fun example() = runTest(mainDispatcher.testDispatcher) { ... }
 * ```
 *
 * Two schedulers is the classic failure here: `runTest` advances its own virtual clock while work
 * dispatched elsewhere never runs, and the test hangs or silently asserts on a stale value.
 *
 * Defaults to [StandardTestDispatcher] deliberately. `UnconfinedTestDispatcher` runs coroutines
 * eagerly at the launch point, which hides ordering and race bugs that real dispatch would expose;
 * pass one explicitly if a test genuinely needs eager execution.
 *
 * Replaces `MainCoroutineScopeRule`, which was a JUnit 4 `TestWatcher` and therefore unusable by
 * the JUnit 5 tests in this project - it sat unreferenced in this module while every ViewModel test
 * hand-rolled the same setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(val testDispatcher: TestDispatcher = StandardTestDispatcher()) :
  BeforeEachCallback, AfterEachCallback {

  /**
   * A real fake rather than a mock, for the few places that still inject a dispatcher provider - a
   * flow doing CPU work in its operators, which belongs on `default` rather than on whatever
   * collects it.
   *
   * A `mockk` here has to stub each property, so adding a `default` call to production code makes
   * an unrelated test fail on an unstubbed member instead of on its assertion. This cannot.
   */
  val dispatcherProvider: CoroutineDispatcherProvider =
    object : CoroutineDispatcherProvider {
      override val main: CoroutineDispatcher = testDispatcher
      override val default: CoroutineDispatcher = testDispatcher
      override val io: CoroutineDispatcher = testDispatcher
      override val unconfined: CoroutineDispatcher = testDispatcher
    }

  override fun beforeEach(context: ExtensionContext) {
    Dispatchers.setMain(testDispatcher)
  }

  override fun afterEach(context: ExtensionContext) {
    Dispatchers.resetMain()
  }
}
