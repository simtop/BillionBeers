package com.simtop.billionbeers.presentation

import androidx.lifecycle.ViewModel
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey

@ContributesIntoMap(AppScope::class)
@ViewModelKey(AppNavigationViewModel::class)
@Inject
class AppNavigationViewModel(private val beersRepository: BeersRepository) : ViewModel() {

  // Resolves a deep-linked beer id against the local cache - deep links only carry an id,
  // never the full Beer payload the BeerDetail nav key needs.
  suspend fun resolveBeer(beerId: String): Beer? = beersRepository.getBeerById(beerId)
}
