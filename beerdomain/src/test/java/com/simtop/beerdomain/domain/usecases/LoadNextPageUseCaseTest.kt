package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.test.fakes.FakeBeersRepository
import com.simtop.core.core.PagingState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LoadNextPageUseCaseTest {

    private val fakeRepository = FakeBeersRepository()
    private val useCase = LoadNextPageUseCase(fakeRepository)

    @Test
    fun `execute should trigger loadNextPage on repository`() = runTest {
        // Act
        useCase.execute()

        // Assert
        // Fake implementation sets state to Success after loading
        assertEquals(PagingState.Success, fakeRepository.getPagingState())
    }
}
