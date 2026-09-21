package com.simtop.feature.favorites

import androidx.lifecycle.ViewModel
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.shared.favorites.FavoritesViewModel as SharedFavoritesViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey(FavoritesViewModel::class)
@Inject
class FavoritesViewModel(beersRepository: BeersRepository) :
  SharedFavoritesViewModel(beersRepository)
