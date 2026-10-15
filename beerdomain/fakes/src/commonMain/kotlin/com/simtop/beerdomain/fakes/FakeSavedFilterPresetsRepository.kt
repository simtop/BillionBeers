package com.simtop.beerdomain.fakes

import com.simtop.beerdomain.domain.errors.MutateFilterPresetError
import com.simtop.beerdomain.domain.errors.SaveFilterPresetError
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.domain.repositories.SavedFilterPresetsRepository
import com.simtop.core.core.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSavedFilterPresetsRepository : SavedFilterPresetsRepository {
  private val presetsFlow = MutableStateFlow<List<SavedFilterPreset>>(emptyList())
  private var observationFlow: Flow<List<SavedFilterPreset>>? = null

  fun setObservationFlow(flow: Flow<List<SavedFilterPreset>>?) {
    observationFlow = flow
  }

  fun getPresets(): List<SavedFilterPreset> = presetsFlow.value

  val saveRequests = mutableListOf<SavedFilterPreset>()
  var saveError: SaveFilterPresetError? = null
  var saveException: Exception? = null

  override fun observeSavedFilterPresets(): Flow<List<SavedFilterPreset>> =
    observationFlow ?: presetsFlow

  override suspend fun saveFilterPreset(
    preset: SavedFilterPreset
  ): Either<SaveFilterPresetError, Unit> {
    saveRequests += preset
    saveException?.let { throw it }
    saveError?.let { return Either.Left(it) }
    val current = presetsFlow.value.toMutableList()
    val index = current.indexOfFirst {
      it.id == preset.id || (it.name == preset.name && it.query == preset.query)
    }
    if (index < 0 && current.size >= SavedFilterPreset.MAX_COUNT) {
      return Either.Left(SaveFilterPresetError.CapacityReached)
    }
    if (index < 0) current += preset else current[index] = preset
    presetsFlow.value =
      current.sortedWith(
        compareByDescending<SavedFilterPreset> { it.updatedAt }.thenBy { it.name }.thenBy { it.id }
      )
    return Either.Right(Unit)
  }

  val renameRequests = mutableListOf<Triple<String, String, Long>>()
  var mutateError: MutateFilterPresetError? = null
  var mutateException: Exception? = null

  override suspend fun renameFilterPreset(
    id: String,
    name: String,
    updatedAt: Long,
  ): Either<MutateFilterPresetError, Unit> {
    renameRequests += Triple(id, name, updatedAt)
    mutateException?.let { throw it }
    mutateError?.let { return Either.Left(it) }
    presetsFlow.value =
      presetsFlow.value.map { if (it.id == id) it.copy(name = name, updatedAt = updatedAt) else it }
    return Either.Right(Unit)
  }

  override suspend fun deleteFilterPreset(id: String): Either<MutateFilterPresetError, Unit> {
    mutateException?.let { throw it }
    mutateError?.let { return Either.Left(it) }
    presetsFlow.value = presetsFlow.value.filterNot { it.id == id }
    return Either.Right(Unit)
  }
}
