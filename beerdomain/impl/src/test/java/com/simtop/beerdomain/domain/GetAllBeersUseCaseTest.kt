package com.simtop.beerdomain.domain

import app.cash.turbine.test
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.FakeBeersRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetAllBeersUseCaseTest {

  private val fakeRepository = FakeBeersRepository()
  private val useCase = GetAllBeersUseCase(fakeRepository)

  @Test
  fun `execute should return beers from repository`() = runTest {
    // Arrange
    val beer = Beer.empty.copy(id = "1", name = "Test Beer")
    fakeRepository.setBeers(listOf(beer))
    val quantity = 10

    // Act & Assert
    useCase.execute(GetAllBeersUseCase.Params(quantity)).test {
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("Test Beer", list[0].name)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
