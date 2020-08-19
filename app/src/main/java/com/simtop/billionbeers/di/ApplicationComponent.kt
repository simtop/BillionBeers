package com.simtop.billionbeers.di

import com.simtop.billionbeers.FirstFragment
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AppModule::class,
        BeersApiModule::class,
        BeersRepositoryModule::class,
        BeersDatabaseModule::class
    ]
)
interface ApplicationComponent {
    @Component.Factory
    interface Factory {
        fun create(): ApplicationComponent
    }

    fun inject(firstFragment: FirstFragment)
}