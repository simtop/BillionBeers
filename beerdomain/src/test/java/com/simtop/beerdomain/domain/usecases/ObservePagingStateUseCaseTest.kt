package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.repositories.BeersRepository
import io.mockk.verify
import io.mockk.mockk
import org.junit.Test

class ObservePagingStateUseCaseTest {

    private val beersRepository = mockk<BeersRepository>(relaxed = true)
    private val useCase = ObservePagingStateUseCase(beersRepository)

    @Test
    fun `execute should call observePagingState on repository`() {
        useCase.execute()
        verify(exactly = 1) { beersRepository.observePagingState() }
    }
}
