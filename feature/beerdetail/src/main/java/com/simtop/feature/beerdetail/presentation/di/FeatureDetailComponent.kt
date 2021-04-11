package com.simtop.feature.beerdetail.presentation.di

import com.simtop.billionbeers.di.DynamicDependencies
import com.simtop.feature.beerdetail.presentation.BeerDetailFragment
import dagger.Component

@Component(
    dependencies = [DynamicDependencies::class]
)
interface FeatureDetailComponent {

    fun inject(fragment: BeerDetailFragment)

    @Component.Factory
    interface Factory {
        fun create(
            dependencies: DynamicDependencies,
        ): FeatureDetailComponent
    }
}