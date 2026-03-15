package com.simtop.billionbeers

import com.google.android.play.core.splitcompat.SplitCompatApplication
import com.simtop.billionbeers.di.AppGraph
import dev.zacsweers.metro.createGraphFactory
import com.simtop.core.di.GraphProvider
import com.simtop.core.di.ViewModelFactory

class BillionBeersApplication : SplitCompatApplication(), GraphProvider {
    lateinit var appGraph: AppGraph

    override val viewModelFactory: ViewModelFactory
        get() = appGraph.viewModelFactory

    override fun onCreate() {
        super.onCreate()
        if (!::appGraph.isInitialized) {
            appGraph = createGraphFactory<AppGraph.Factory>().create(this)
        }
    }
}
