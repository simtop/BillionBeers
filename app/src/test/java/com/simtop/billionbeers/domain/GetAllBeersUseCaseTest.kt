package com.simtop.billionbeers.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.billionbeers.MainCoroutineScopeRule
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.domain.repository.BeersRepository
import com.simtop.billionbeers.domain.usecases.GetAllBeersUseCase
import com.simtop.billionbeers.fakeBeerListModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.any
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
internal class GetAllBeersUseCaseTest {

    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()

    private val beersRepository: BeersRepository = mockk()

    @Test
    fun `should get data from repository`() {
        coroutineScope.runBlockingTest {
            coEvery { beersRepository.getBeersFromSingleSource(any()) } returns fakeBeerListModel

            val getAllBeersUseCase = GetAllBeersUseCase(beersRepository)

            val result = getAllBeersUseCase.execute(getAllBeersUseCase.Params(any()))

            coVerify(exactly = 1) { beersRepository.getBeersFromSingleSource(any()) }

            if (result is Either.Right) {
                result.value shouldBeEqualTo fakeBeerListModel
            }
        }
    }
}