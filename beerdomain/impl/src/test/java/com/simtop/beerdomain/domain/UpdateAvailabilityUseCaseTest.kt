package com.simtop.beerdomain.domain

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.Either
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateAvailabilityUseCaseTest {

  private val fakeRepository = FakeBeersRepository()
  private val useCase = UpdateAvailabilityUseCase(fakeRepository)

  @Test
  fun `execute should update availability in repository`() = runTest {
    // Arrange
    val beer = Beer.empty.copy(id = "1", name = "Test Beer", availability = true)
    fakeRepository.setBeers(listOf(beer))
    val updatedBeer = beer.copy(availability = false)

    // Act
    val result = useCase.execute(useCase.Params(updatedBeer))

    // Assert
    assertTrue(result is Either.Right)
    val storedBeer = fakeRepository.getBeers().first()
    assertEquals(false, storedBeer.availability)
  }

  @Test
  fun `execute should return error when repository fails`() = runTest {
    // Arrange
    val exception = Exception("Repo error")
    fakeRepository.setExceptionToThrow(exception)
    val beer = Beer.empty.copy(id = "1")

    // Act
    val result = useCase.execute(useCase.Params(beer))

    // Assert
    assertTrue(result is Either.Left)
    assertEquals(exception, (result as Either.Left).value)
  }
}
