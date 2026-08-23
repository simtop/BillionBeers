package com.simtop.billionbeers

import com.google.android.play.core.splitcompat.SplitCompatApplication
import com.simtop.billionbeers.debug.enableStrictMode
import com.simtop.billionbeers.di.AppGraph
import com.simtop.billionbeers.di.BaseAppGraph
import com.simtop.core.di.GraphProvider
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

class BillionBeersApplication : SplitCompatApplication(), GraphProvider {
  lateinit var appGraph: BaseAppGraph

  override val metroViewModelFactory: MetroViewModelFactory
    get() = appGraph.metroViewModelFactory

  // Deliberately does *not* pre-warm the networking branch of the graph on a background thread.
  // That was built, measured and reverted: a Perfetto trace of a cold start on a Pixel 8 shows no
  // SSL, Conscrypt or socket work on the main thread at all - the only OkHttp work anywhere is
  // ~3ms of JIT on the JIT thread pool - so there was nothing to move. See docs/adr/0012.
  override fun onCreate() {
    super.onCreate()
    enableStrictMode()
    if (!::appGraph.isInitialized) {
      rebuildAppGraph()
    }
  }

  /** Recreates app-scoped dependencies after a debug-only API environment change. */
  internal fun rebuildAppGraph() {
    appGraph = createGraphFactory<AppGraph.Factory>().create(this)
  }
}
