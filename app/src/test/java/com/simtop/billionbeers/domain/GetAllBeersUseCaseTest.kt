package com.simtop.billionbeers.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase
import com.simtop.billionbeers.testing_utils.MainCoroutineScopeRule
import com.simtop.billionbeers.testing_utils.fakeBeerListModel
import com.simtop.billionbeers.testing_utils.fakeException
import com.simtop.billionbeers.testing_utils.runBlocking
import com.simtop.core.core.mapLeft
import com.simtop.core.core.mapRight
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

        coEvery { beersRepository.getBeersFromSingleSource(capture(captureSlot)) } returns fakeBeerListModel

        val getAllBeersUseCase =
            GetAllBeersUseCase(beersRepository)

        // Act

        val response = getAllBeersUseCase.execute(getAllBeersUseCase.Params(anyInt()))

        // Assert

        expect {
            that(captureSlot) {
                get { captured }.isEqualTo(0)
            }
            response.mapRight {
                that(it) {
                    get { this }.isEqualTo(fakeBeerListModel)
                }
            }
        }
    }

    @Test
    fun `when repository fails we get error response`() = coroutineScope.runBlocking {
        // Arrange

        val captureSlot = slot<Int>()

        coEvery { beersRepository.getBeersFromSingleSource(capture(captureSlot)) } throws fakeException

        val getAllBeersUseCase =
            GetAllBeersUseCase(beersRepository)

        // Act

        val response = getAllBeersUseCase.execute(getAllBeersUseCase.Params(anyInt()))

        //Assert

        expect {
            that(captureSlot) {
                get { captured }.isEqualTo(0)
            }
            response.mapLeft {
                that(it) {
                    get { this }.isEqualTo(fakeException)
                }
            }
        }
    }
}