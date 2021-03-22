package com.simtop.billionbeers.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_network.remotesources.BeersRemoteSource
import com.simtop.beer_data.repositories.BeersRepositoryImpl
import com.simtop.billionbeers.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.any
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
internal class BeersRepositoryImplTest {

    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()

    private val beersRemoteSource: com.simtop.beer_network.remotesources.BeersRemoteSource = mockk()

    private val beersLocalSource: com.simtop.beer_database.localsources.BeersLocalSource = mockk()

    @Test
    fun `when remote source succeeds we get a success response`() = coroutineScope.runBlocking {
        // Arrange

        val getBeers = com.simtop.beer_data.repositories.BeersRepositoryImpl(
            beersRemoteSource,
            beersLocalSource
        )

        coEvery { beersRemoteSource.getListOfBeers(any()) } returns fakeBeerApiResponse

        // Act

        val result = getBeers.getListOfBeerFromApi(any())

        // Assert

        coVerify(exactly = 1) { beersRemoteSource.getListOfBeers(any()) }

        result shouldBeEqualTo fakeBeerListModel
    }

    @Test(expected = Exception::class)
    fun `when remote source fails we throw an exception`() = coroutineScope.runBlocking {
        // Arrange
        val getBeers = com.simtop.beer_data.repositories.BeersRepositoryImpl(
            beersRemoteSource,
            beersLocalSource
        )

        coEvery { beersRemoteSource.getListOfBeers(any()) } throws fakeException

        // Act

        getBeers.getListOfBeerFromApi(any())

        // Assert
    }
}