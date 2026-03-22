package com.simtop.billionbeers

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.beer_data.di.BeersRepositoryModule
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.di.BaseAppGraph
import com.simtop.billionbeers.di.SplitInstallModule
import com.simtop.core.di.AppScope
import com.simtop.core.di.ApplicationContext
import com.simtop.core.di.ViewModelFactory
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

@DependencyGraph(
    scope = AppScope::class,
    excludes = [
        BeersRepositoryModule::class,
        SplitInstallModule::class
    ]
)
interface TestAppGraph : BaseAppGraph {
    override val splitInstallManager: SplitInstallManager
    override val viewModelFactory: ViewModelFactory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides @ApplicationContext context: Context,
            @Provides beersRepository: BeersRepository,
            @Provides splitInstallManager: SplitInstallManager
        ): TestAppGraph
    }
}
