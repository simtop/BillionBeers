package com.simtop.billionbeers.di

import com.simtop.billionbeers.MainActivityE2ETest
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AppModule::class,
        TestModule::class,
        BeersApiModule::class,
        BeersRepositoryModule::class,
        ViewModelsModule::class
    ]
)
interface TestApplicationComponent : ApplicationComponent {

    @Component.Factory
    interface Factory{

        fun create(@BindsInstance app: TestBaseApplication): TestApplicationComponent
    }

    fun inject(localDataSourceTest: LocalDataSourceTest)
    fun inject(mainActivityE2ETest: MainActivityE2ETest)
}