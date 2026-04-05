package com.simtop.billionbeers

import android.content.Context
import androidx.lifecycle.ViewModel
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.beer_data.di.BeersRepositoryModule
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.di.BaseAppGraph
import com.simtop.billionbeers.di.SplitInstallModule
import com.simtop.core.di.AppScope
import com.simtop.core.di.ApplicationContext
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

@BindingContainer
@ContributesTo(AppScope::class)
interface TestViewModelMapsModule {
    @Multibinds(allowEmpty = true)
    fun viewModels(): Map<KClass<out ViewModel>, ViewModel>

    @Multibinds(allowEmpty = true)
    fun assistedViewModels(): Map<KClass<out ViewModel>, ViewModelAssistedFactory>

    @Multibinds(allowEmpty = true)
    fun manualAssistedViewModels(): Map<KClass<out ManualViewModelAssistedFactory>, ManualViewModelAssistedFactory>
}

@DependencyGraph(
    scope = AppScope::class,
    excludes = [
        BeersRepositoryModule::class,
        SplitInstallModule::class,
    ]
)
interface TestAppGraph : BaseAppGraph {
    override val splitInstallManager: SplitInstallManager
    override val metroViewModelFactory: TestViewModelFactory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides @ApplicationContext context: Context,
            @Provides beersRepository: BeersRepository,
            @Provides splitInstallManager: SplitInstallManager,
        ): TestAppGraph
    }
}
