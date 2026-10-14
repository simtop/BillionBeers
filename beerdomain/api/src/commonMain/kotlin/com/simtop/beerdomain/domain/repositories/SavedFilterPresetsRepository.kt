package com.simtop.beerdomain.domain.repositories

import com.simtop.beerdomain.domain.errors.MutateFilterPresetError
import com.simtop.beerdomain.domain.errors.SaveFilterPresetError
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.core.core.Either
import kotlinx.coroutines.flow.Flow

/** Persistence contract for saved filter presets, independent of the beer catalog repository. */
interface SavedFilterPresetsRepository {
  fun observeSavedFilterPresets(): Flow<List<SavedFilterPreset>>

  suspend fun saveFilterPreset(preset: SavedFilterPreset): Either<SaveFilterPresetError, Unit>

  suspend fun renameFilterPreset(
    id: String,
    name: String,
    updatedAt: Long,
  ): Either<MutateFilterPresetError, Unit>

  suspend fun deleteFilterPreset(id: String): Either<MutateFilterPresetError, Unit>
}
