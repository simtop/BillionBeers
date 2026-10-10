package com.simtop.feature.savedfilters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.MutateFilterPresetError
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.domain.usecases.RenameFilterPresetUseCase
import com.simtop.core.core.Either
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

private const val PRESETS_STOP_TIMEOUT_MILLIS = 5_000L

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey(SavedFiltersViewModel::class)
@Inject
class SavedFiltersViewModel(private val repository: BeersRepository) : ViewModel() {
  private val renameFilterPreset = RenameFilterPresetUseCase(repository)
  private val mutationErrors = Channel<Unit>(Channel.BUFFERED)
  val mutationFailed = mutationErrors.receiveAsFlow()
  val presets: StateFlow<List<SavedFilterPreset>> =
    repository
      .observeSavedFilterPresets()
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(PRESETS_STOP_TIMEOUT_MILLIS),
        emptyList(),
      )

  suspend fun rename(preset: SavedFilterPreset, name: String): Boolean =
    when (val result = renameFilterPreset(preset.id, name)) {
      is Either.Right -> true
      is Either.Left -> {
        if (result.value is MutateFilterPresetError.Unknown) mutationErrors.trySend(Unit)
        false
      }
    }

  suspend fun delete(preset: SavedFilterPreset): Boolean {
    val succeeded = repository.deleteFilterPreset(preset.id) is Either.Right
    if (!succeeded) mutationErrors.trySend(Unit)
    return succeeded
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
