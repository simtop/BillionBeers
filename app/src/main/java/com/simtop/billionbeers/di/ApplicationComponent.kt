package com.simtop.billionbeers.di

import android.app.Application
import com.simtop.billionbeers.BeersListFragment
import dagger.BindsInstance
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

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(application: Application): Builder

        fun build(): ApplicationComponent
    }

    fun inject(beersListFragment: BeersListFragment)
}