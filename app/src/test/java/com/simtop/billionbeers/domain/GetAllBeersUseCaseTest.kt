package com.simtop.billionbeers.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.billionbeers.MainCoroutineScopeRule
import com.simtop.beerdomain.core.mapLeft
import com.simtop.beerdomain.core.mapRight
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase
import com.simtop.billionbeers.fakeBeerListModel
import com.simtop.billionbeers.fakeException
import com.simtop.billionbeers.runBlocking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.any
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
internal class GetAllBeersUseCaseTest {

    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()

    private val beersRepository: com.simtop.beerdomain.domain.repositories.BeersRepository = mockk()

    @Test
    fun `when repository succeeds we get success response`() = coroutineScope.runBlocking {
        // Arrange

        coEvery { beersRepository.getBeersFromSingleSource(any()) } returns fakeBeerListModel

        val getAllBeersUseCase =
            com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase(beersRepository)

        // Act

        val response = getAllBeersUseCase.execute(getAllBeersUseCase.Params(any()))

        // Assert

        coVerify(exactly = 1) { beersRepository.getBeersFromSingleSource(any()) }

        response.mapRight {
            it shouldBeEqualTo fakeBeerListModel
        }
    }

    @Test
    fun `when repository fails we get error response`() = coroutineScope.runBlocking {
        // Arrange

        coEvery { beersRepository.getBeersFromSingleSource(any()) } throws fakeException

        val getAllBeersUseCase =
            com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase(beersRepository)

        // Act

        val response = getAllBeersUseCase.execute(getAllBeersUseCase.Params(any()))

        //Assert

        coVerify(exactly = 1) { beersRepository.getBeersFromSingleSource(any()) }

        response.mapLeft {
            it shouldBeEqualTo fakeException
        }
    }
}