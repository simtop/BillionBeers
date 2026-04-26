package com.simtop.billionbeers

import com.google.android.play.core.splitcompat.SplitCompatApplication
import com.simtop.billionbeers.di.AppGraph
import com.simtop.billionbeers.di.BaseAppGraph
import dev.zacsweers.metro.createGraphFactory
import com.simtop.core.di.GraphProvider
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

class BillionBeersApplication : SplitCompatApplication(), GraphProvider {
    lateinit var appGraph: BaseAppGraph

    override val metroViewModelFactory: MetroViewModelFactory
        get() = appGraph.metroViewModelFactory

    override fun onCreate() {
        super.onCreate()
        if (!::appGraph.isInitialized) {
            appGraph = createGraphFactory<AppGraph.Factory>().create(this)
        }
    }
}
