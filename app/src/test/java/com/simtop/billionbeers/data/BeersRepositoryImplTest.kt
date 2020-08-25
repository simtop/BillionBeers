package com.simtop.billionbeers.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.billionbeers.MainCoroutineScopeRule
import com.simtop.billionbeers.data.database.BeersDao
import com.simtop.billionbeers.data.localsource.BeersLocalSource
import com.simtop.billionbeers.data.remotesources.BeersRemoteSource
import com.simtop.billionbeers.data.repository.BeersRepositoryImpl
import com.simtop.billionbeers.fakeBeerApiResponse
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
internal class BeersRepositoryImplTest {

    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()

    private val beersRemoteSource: BeersRemoteSource = mockk()

    private val beersLocalSource : BeersLocalSource = mockk()

    @Test
    fun `should get data from repository`() {
        coroutineScope.runBlockingTest {

            val getBeers = BeersRepositoryImpl(beersRemoteSource, beersLocalSource)

            coEvery { beersRemoteSource.getListOfBeers(any()) } returns fakeBeerApiResponse

            val result = getBeers.getListOfBeerFromApi(any())

            coVerify(exactly = 1) { beersRemoteSource.getListOfBeers(any())  }

            result shouldBeEqualTo fakeBeerListModel

        }
    }
}