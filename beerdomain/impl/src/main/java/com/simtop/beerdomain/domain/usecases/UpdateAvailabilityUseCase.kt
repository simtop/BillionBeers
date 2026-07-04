package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.Either
import dev.zacsweers.metro.Inject

class UpdateAvailabilityUseCase @Inject constructor(private val beersRepository: BeersRepository) {
  suspend operator fun invoke(beer: Beer): Either<UpdateAvailabilityError, Unit> {
    return beersRepository.updateAvailability(beer)
  }
}
