package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.repositories.BeersRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@ExperimentalCoroutinesApi
class LoadNextPageUseCaseTest {

    private val beersRepository = mockk<BeersRepository>(relaxed = true)
    private val useCase = LoadNextPageUseCase(beersRepository)

    @Test
    fun `execute should call loadNextPage on repository`() = runTest {
        useCase.execute()
        coVerify(exactly = 1) { beersRepository.loadNextPage() }
    }
}
