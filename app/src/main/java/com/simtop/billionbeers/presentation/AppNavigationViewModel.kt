package com.simtop.billionbeers.presentation

import androidx.lifecycle.ViewModel
import com.simtop.beerdomain.domain.errors.SaveFilterPresetError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.Either
import com.simtop.core.core.SystemEpochTimeProvider
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

  suspend fun savePreset(name: String, query: BeersQuery): Either<SaveFilterPresetError, Unit> {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty() || normalizedName.length > SavedFilterPreset.MAX_NAME_LENGTH)
      return Either.Right(Unit)
    return beersRepository.saveFilterPreset(
      SavedFilterPreset(
        id = "preset-${normalizedName.hashCode()}-${query.hashCode()}",
        name = normalizedName,
        query = query,
        updatedAt = SystemEpochTimeProvider().epochMillis(),
      )
    )
  }
}
