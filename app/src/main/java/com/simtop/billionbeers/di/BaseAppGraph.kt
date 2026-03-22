package com.simtop.billionbeers.di

import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.di.ViewModelFactory

interface BaseAppGraph : DynamicDependencies {
    override val useCase: UpdateAvailabilityUseCase
    override val coroutineDispatcher: CoroutineDispatcherProvider
    val splitInstallManager: SplitInstallManager
    val viewModelFactory: ViewModelFactory
}
