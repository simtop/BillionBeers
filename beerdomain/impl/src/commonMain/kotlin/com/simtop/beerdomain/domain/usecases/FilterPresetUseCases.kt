package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.errors.MutateFilterPresetError
import com.simtop.beerdomain.domain.errors.SaveFilterPresetError
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.Either
import com.simtop.core.core.EpochTimeProvider
import com.simtop.core.core.SystemEpochTimeProvider

fun interface SavedFilterPresetIdProvider {
  fun idFor(name: String, query: BeersQuery): String
}

/** Preserves the existing same-name/same-query upsert identity until product semantics change. */
object DeterministicSavedFilterPresetIdProvider : SavedFilterPresetIdProvider {
  override fun idFor(name: String, query: BeersQuery): String =
    "preset-${name.hashCode()}-${query.hashCode()}"
}

class SaveFilterPresetUseCase(
  private val repository: BeersRepository,
  private val epochTimeProvider: EpochTimeProvider = SystemEpochTimeProvider(),
  private val idProvider: SavedFilterPresetIdProvider =
    DeterministicSavedFilterPresetIdProvider,
) {
  suspend operator fun invoke(
    name: String,
    query: BeersQuery,
  ): Either<SaveFilterPresetError, Unit> {
    val normalizedName = normalizeFilterPresetName(name)
      ?: return Either.Left(SaveFilterPresetError.InvalidName)
    return repository.saveFilterPreset(
      SavedFilterPreset(
        id = idProvider.idFor(normalizedName, query),
        name = normalizedName,
        query = query,
        updatedAt = epochTimeProvider.epochMillis(),
      )
    )
  }
}

class RenameFilterPresetUseCase(
  private val repository: BeersRepository,
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
