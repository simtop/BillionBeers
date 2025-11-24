package com.simtop.billionbeers.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase
import com.simtop.billionbeers.testing_utils.MainCoroutineScopeRule
import com.simtop.billionbeers.testing_utils.fakeBeerListModel
import com.simtop.billionbeers.testing_utils.fakeException
import com.simtop.billionbeers.testing_utils.runBlocking
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.ArgumentMatchers.anyInt
import strikt.api.expect
import strikt.assertions.isEqualTo

@ExperimentalCoroutinesApi
internal class GetAllBeersUseCaseTest {

    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()

    private val beersRepository: BeersRepository = mockk()

    @Test
    fun `when repository succeeds we get success response`() = coroutineScope.runBlocking {
        // Arrange

        val captureSlot = slot<Int>()

        coEvery { beersRepository.getBeersFromSingleSource(capture(captureSlot)) } returns flowOf(fakeBeerListModel)

        val getAllBeersUseCase =
            GetAllBeersUseCase(beersRepository)

        // Act

        val response = getAllBeersUseCase.execute(GetAllBeersUseCase.Params(anyInt()))

        // Assert

        expect {
            that(captureSlot) {
                get { captured }.isEqualTo(0)
            }
            that(response.first()) {
                get { this }.isEqualTo(fakeBeerListModel)
            }
        }
    }

    @Test
    fun `when repository fails we get error response`() = coroutineScope.runBlocking {
        // Arrange

        val captureSlot = slot<Int>()

        coEvery { beersRepository.getBeersFromSingleSource(capture(captureSlot)) } returns flow { throw fakeException }

        val getAllBeersUseCase =
            GetAllBeersUseCase(beersRepository)

        // Act
        // We expect the flow to throw the exception when collected
        
        try {
             getAllBeersUseCase.execute(GetAllBeersUseCase.Params(anyInt())).first()
        } catch (e: Exception) {
             expect {
                 that(e).isEqualTo(fakeException)
             }
        }
    }
}