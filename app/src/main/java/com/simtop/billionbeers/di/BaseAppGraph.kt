package com.simtop.billionbeers.di

import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.FeatureFlagProvider
import com.simtop.core.core.NetworkFaultController
import com.simtop.core.core.ThemeController
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelMultibindings

interface BaseAppGraph : DynamicDependencies, MetroViewModelMultibindings {
  override val beersRepository: BeersRepository
  override val coroutineDispatcher: CoroutineDispatcherProvider
  val splitInstallManager: SplitInstallManager
  val metroViewModelFactory: MetroViewModelFactory
  val themeController: ThemeController
  val networkFaultController: NetworkFaultController
  val featureFlagProvider: FeatureFlagProvider
}
