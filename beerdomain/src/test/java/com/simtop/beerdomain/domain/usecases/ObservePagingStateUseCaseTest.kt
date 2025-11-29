package com.simtop.beerdomain.domain.usecases

import app.cash.turbine.test
import com.simtop.beerdomain.test.fakes.FakeBeersRepository
import com.simtop.core.core.PagingState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObservePagingStateUseCaseTest {

    private val fakeRepository = FakeBeersRepository()
    private val useCase = ObservePagingStateUseCase(fakeRepository)

    @Test
    fun `execute should return paging state flow`() = runTest {
        // Arrange
        fakeRepository.setPagingState(PagingState.Loading)

        // Act & Assert
        useCase.execute().test {
            assertEquals(PagingState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
