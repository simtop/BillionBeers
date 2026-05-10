package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.Either
import dev.zacsweers.metro.Inject

class UpdateAvailabilityUseCase @Inject constructor(private val beersRepository: BeersRepository) {
  @Suppress("TooGenericExceptionCaught")
  suspend operator fun invoke(beer: Beer): Either<Exception, Unit> {
    return try {
      beersRepository.updateAvailability(beer)
      Either.Right(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (exception: Exception) {
      Either.Left(exception)
    }
  }
}
