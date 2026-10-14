package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.errors.MutateFilterPresetError
import com.simtop.beerdomain.domain.errors.SaveFilterPresetError
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.domain.repositories.SavedFilterPresetsRepository
import com.simtop.core.core.Either
import com.simtop.core.core.EpochTimeProvider
import com.simtop.core.core.SystemEpochTimeProvider
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

fun interface SavedFilterPresetIdProvider {
  fun newId(): String
}

object RandomSavedFilterPresetIdProvider : SavedFilterPresetIdProvider {
  @OptIn(ExperimentalUuidApi::class)
  override fun newId(): String = Uuid.random().toString()
}

class SaveFilterPresetUseCase(
  private val repository: SavedFilterPresetsRepository,
  private val epochTimeProvider: EpochTimeProvider = SystemEpochTimeProvider(),
  private val idProvider: SavedFilterPresetIdProvider =
    RandomSavedFilterPresetIdProvider,
) {
  suspend operator fun invoke(
    name: String,
    query: BeersQuery,
  ): Either<SaveFilterPresetError, Unit> {
    val normalizedName = normalizeFilterPresetName(name)
      ?: return Either.Left(SaveFilterPresetError.InvalidName)
    val existingId =
      try {
        repository.observeSavedFilterPresets().first().firstOrNull {
          it.name == normalizedName && it.query == query
        }?.id
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        return Either.Left(SaveFilterPresetError.Unknown(e))
      }
    return repository.saveFilterPreset(
      SavedFilterPreset(
        id = existingId ?: idProvider.newId(),
        name = normalizedName,
        query = query,
        updatedAt = epochTimeProvider.epochMillis(),
      )
    )
  }
}

class RenameFilterPresetUseCase(
  private val repository: SavedFilterPresetsRepository,
  private val epochTimeProvider: EpochTimeProvider = SystemEpochTimeProvider(),
) {
  suspend operator fun invoke(
    id: String,
    name: String,
  ): Either<MutateFilterPresetError, Unit> {
    val normalizedName = normalizeFilterPresetName(name)
      ?: return Either.Left(MutateFilterPresetError.InvalidName)
    return repository.renameFilterPreset(
      id = id,
      name = normalizedName,
      updatedAt = epochTimeProvider.epochMillis(),
    )
  }
}

private fun normalizeFilterPresetName(name: String): String? =
  name.trim().takeIf { it.isNotEmpty() && it.length <= SavedFilterPreset.MAX_NAME_LENGTH }
