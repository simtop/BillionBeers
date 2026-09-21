package com.simtop.feature.favorites

import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.shared.favorites.FavoritesViewModel as SharedFavoritesViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey

@ContributesIntoMap(AppScope::class)
@ViewModelKey(FavoritesViewModel::class)
@Inject
class FavoritesViewModel(beersRepository: BeersRepository) :
  SharedFavoritesViewModel(beersRepository)
