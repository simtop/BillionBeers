package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.repositories.BeersRepository
import io.mockk.verify
import io.mockk.mockk
import org.junit.Test

class GetAllBeersUseCaseTest {

    private val beersRepository = mockk<BeersRepository>(relaxed = true)
    private val useCase = GetAllBeersUseCase(beersRepository)

    @Test
    fun `execute should call getBeersFromSingleSource on repository`() {
        val quantity = 10
        useCase.execute(GetAllBeersUseCase.Params(quantity))
        verify(exactly = 1) { beersRepository.getBeersFromSingleSource(quantity) }
    }
}
