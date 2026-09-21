package com.simtop.feature.beerbrowse.presentation

import androidx.lifecycle.ViewModel
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.shared.beerbrowse.BrowseViewModel as SharedBrowseViewModel
import com.simtop.feature.beerbrowse.presentation.di.FeatureBrowseScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey

@ContributesIntoMap(FeatureBrowseScope::class, binding = binding<ViewModel>())
@ViewModelKey(BrowseViewModel::class)
@Inject
class BrowseViewModel(beersRepository: BeersRepository) : SharedBrowseViewModel(beersRepository)
