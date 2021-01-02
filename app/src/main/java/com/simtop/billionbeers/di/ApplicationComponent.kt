package com.simtop.billionbeers.di

import com.simtop.billionbeers.BillionBeersApplication
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
        ViewModelsModule::class,
        CoroutineDispatchersModule::class
    ]
)
interface ApplicationComponent {

    @Component.Factory
    interface Factory{

        fun create(@BindsInstance app: BillionBeersApplication): ApplicationComponent
    }

    fun inject(beersListFragment: BeersListFragment)
    fun inject(beersDetailFragment: BeerDetailFragment)
}