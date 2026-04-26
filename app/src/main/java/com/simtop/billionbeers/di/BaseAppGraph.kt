package com.simtop.billionbeers.di

import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.core.core.CoroutineDispatcherProvider
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

interface BaseAppGraph : DynamicDependencies {
    override val useCase: UpdateAvailabilityUseCase
    override val coroutineDispatcher: CoroutineDispatcherProvider
    val splitInstallManager: SplitInstallManager
    val metroViewModelFactory: MetroViewModelFactory
}
