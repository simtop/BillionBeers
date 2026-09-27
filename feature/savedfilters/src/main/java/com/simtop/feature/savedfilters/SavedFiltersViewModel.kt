package com.simtop.feature.savedfilters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.SystemEpochTimeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PRESETS_STOP_TIMEOUT_MILLIS = 5_000L

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey(SavedFiltersViewModel::class)
@Inject
class SavedFiltersViewModel(private val repository: BeersRepository) : ViewModel() {
  val presets: StateFlow<List<SavedFilterPreset>> =
    repository
      .observeSavedFilterPresets()
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(PRESETS_STOP_TIMEOUT_MILLIS),
        emptyList(),
      )

  fun rename(preset: SavedFilterPreset, name: String) {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty() || normalizedName.length > SavedFilterPreset.MAX_NAME_LENGTH)
      return
    viewModelScope.launch {
      repository.renameFilterPreset(
        preset.id,
        normalizedName,
        SystemEpochTimeProvider().epochMillis(),
      )
    }
  }

  fun delete(preset: SavedFilterPreset) {
    viewModelScope.launch { repository.deleteFilterPreset(preset.id) }
  }
}

class SavedFilterResultsViewModel
@AssistedInject
constructor(
  coroutineDispatcher: com.simtop.core.core.CoroutineDispatcherProvider,
  beersPagerFactory: com.simtop.beerdomain.domain.repositories.BeersPagerFactory,
  @Assisted query: com.simtop.beerdomain.domain.models.BeersQuery,
) :
  com.simtop.billionbeers.shared.beerbrowse.BrowseBeersViewModel(
    coroutineDispatcher,
    beersPagerFactory,
    query,
  ) {
  @AssistedFactory
  @ManualViewModelAssistedFactoryKey(Factory::class)
  @ContributesIntoMap(AppScope::class)
  interface Factory : ManualViewModelAssistedFactory {
    fun create(
      @Assisted query: com.simtop.beerdomain.domain.models.BeersQuery
    ): SavedFilterResultsViewModel
  }
}
