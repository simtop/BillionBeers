package com.simtop.feature.beerslist

import androidx.lifecycle.ViewModel
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.shared.beerslist.BeersListViewModel as SharedBeersListViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey(BeersListViewModel::class)
@Inject
class BeersListViewModel(
  beersRepository: BeersRepository,
  beersPagerFactory: BeersPagerFactory,
) : SharedBeersListViewModel(beersRepository, beersPagerFactory)

typealias BeersListEvent = com.simtop.billionbeers.shared.beerslist.BeersListEvent
