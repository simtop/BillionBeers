package com.simtop.billionbeers.di

import android.app.Application
import com.simtop.billionbeers.presentation.beerdetail.BeerDetailFragment
import com.simtop.billionbeers.presentation.beerslist.BeersListFragment
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AppModule::class,
        BeersApiModule::class,
        BeersRepositoryModule::class,
        BeersDatabaseModule::class,
        ViewModelsModule::class
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
    fun inject(beersDetailFragment: BeerDetailFragment)
}