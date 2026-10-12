package com.simtop.billionbeers.shared.savedfilters

import com.simtop.beerdomain.domain.errors.MutateFilterPresetError
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.domain.usecases.RenameFilterPresetUseCase
import com.simtop.core.core.Either
import kotlinx.coroutines.flow.Flow

class SavedFiltersModel(private val repository: BeersRepository) {
  private val renameFilterPreset = RenameFilterPresetUseCase(repository)

  val presets: Flow<List<SavedFilterPreset>> = repository.observeSavedFilterPresets()

  suspend fun rename(id: String, name: String): Either<MutateFilterPresetError, Unit> =
    renameFilterPreset(id, name)

  suspend fun delete(id: String): Either<MutateFilterPresetError, Unit> =
    repository.deleteFilterPreset(id)
}
