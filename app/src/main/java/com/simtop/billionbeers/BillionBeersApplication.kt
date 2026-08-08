package com.simtop.billionbeers

import com.google.android.play.core.splitcompat.SplitCompatApplication
import com.simtop.billionbeers.debug.enableStrictMode
import com.simtop.billionbeers.di.AppGraph
import com.simtop.billionbeers.di.BaseAppGraph
import com.simtop.core.di.GraphProvider
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class BillionBeersApplication : SplitCompatApplication(), GraphProvider {
  lateinit var appGraph: BaseAppGraph

  override val metroViewModelFactory: MetroViewModelFactory
    get() = appGraph.metroViewModelFactory

  override fun onCreate() {
    super.onCreate()
    enableStrictMode()
    if (!::appGraph.isInitialized) {
      appGraph = createGraphFactory<AppGraph.Factory>().create(this)
    }
    warmNetworkingGraph()
  }

  /**
   * Builds the networking branch of the graph off the main thread, before the first composition
   * asks for it.
   *
   * Resolving [BaseAppGraph.beersRepository] pulls the remote source, Retrofit and finally the
   * OkHttp client, and building that client creates an `SSLContext` - which StrictMode reported as
   * a main-thread `CustomViolation: newSSLContext` on every launch. The work sits in the dependency
   * graph rather than in any coroutine, so it happened during `MainActivity.onCreate`'s
   * `setContent`, on the critical path to the first frame. (Its sibling, a main-thread disk read
   * for the cache directory, was fixed separately in `NetworkingModule`.)
   *
   * **This is a race.** Metro memoises providers behind a double-checked lock, so if the first
   * composition reaches the repository first, the main thread blocks on the same work.
   *
   * Measured on a Pixel 8, 10 cold starts per side of the minified benchmark variant: median 209.5
   * ms -> 206.2 ms, mean 213.9 ms -> 206.4 ms. A small real win, 3-7 ms, against a same-code
   * run-to-run swing of 2.5 ms. The debug-build StrictMode violation it removes is far larger
   * (270-294 ms on main, every launch, now zero) - but that is a debug number and does not
   * transfer: the shipped variant is minified and AOT-compiled from a baseline profile, so the same
   * work costs a fraction of it. Treat StrictMode durations as "this is on the wrong thread", never
   * as "this costs the user that much".
   *
   * An emulator cannot answer this at all: there the same comparison swung 160 ms between two runs
   * of identical code, which is 30x the effect being measured.
   */
  private fun warmNetworkingGraph() {
    CoroutineScope(appGraph.coroutineDispatcher.io).launch {
      // A warm-up must never be what crashes the app. If resolution fails here, swallow it and let
      // the real call site fail in its own context, where the error can be handled and reported.
      runCatching { appGraph.beersRepository }
    }
  }
}
