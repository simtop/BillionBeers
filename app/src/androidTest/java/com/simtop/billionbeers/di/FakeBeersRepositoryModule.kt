package com.simtop.billionbeers.di

import com.simtop.beer_data.di.BeersRepositoryModule
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class, replaces = [BeersRepositoryModule::class])
object FakeBeersRepositoryModule {
    val fakeBeersRepository = FakeBeersRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun provideBeersRepository(): BeersRepository = fakeBeersRepository
}