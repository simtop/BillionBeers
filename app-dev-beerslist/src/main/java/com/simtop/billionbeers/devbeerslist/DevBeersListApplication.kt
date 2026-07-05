package com.simtop.billionbeers.devbeerslist

import android.app.Application
import com.simtop.billionbeers.devbeerslist.di.DevAppGraph
import com.simtop.core.di.GraphProvider
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

class DevBeersListApplication : Application(), GraphProvider {
  lateinit var appGraph: DevAppGraph

  override val metroViewModelFactory: MetroViewModelFactory
    get() = appGraph.metroViewModelFactory

  override fun onCreate() {
    super.onCreate()
    appGraph = createGraphFactory<DevAppGraph.Factory>().create(this)
  }
}
