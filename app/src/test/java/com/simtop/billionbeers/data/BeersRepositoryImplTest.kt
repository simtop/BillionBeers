package com.simtop.billionbeers.data

import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_network.remotesources.BeersRemoteSource
import com.simtop.beer_data.repositories.BeersRepositoryImpl
import com.simtop.billionbeers.testing_utils.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.any
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Rule
import org.junit.Test
import strikt.api.expect
import strikt.assertions.isEqualTo

@ExperimentalCoroutinesApi
internal class BeersRepositoryImplTest {

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()

    private val beersRemoteSource: BeersRemoteSource = mockk()

    private val beersLocalSource: BeersLocalSource = mockk()

    @Test
    fun `when remote source succeeds we get a success response`() = coroutineScope.runBlocking {
        // Arrange

        val captureSlot = slot<Int>()

        val getBeers = BeersRepositoryImpl(
            beersRemoteSource,
            beersLocalSource
        )

        coEvery { beersRemoteSource.getListOfBeers(capture(captureSlot)) } returns fakeBeerApiResponse

        // Act

        val result = getBeers.getListOfBeerFromApi(any())

        // Assert

        coVerify(exactly = 1) { beersRemoteSource.getListOfBeers(any()) }

        result shouldBeEqualTo fakeBeerListModel

        expect {
            that(captureSlot) {
                get { captured }.isEqualTo(0)
            }
            that(result) {
                get { this }.isEqualTo(fakeBeerListModel)
            }
        }
    }

    @Test(expected = Exception::class)
    fun `when remote source fails we throw an exception`() = coroutineScope.runBlocking {
        // Arrange
        val getBeers = BeersRepositoryImpl(
            beersRemoteSource,
            beersLocalSource
        )

        coEvery { beersRemoteSource.getListOfBeers(any()) } throws fakeException

        // Act

        getBeers.getListOfBeerFromApi(any())

        // Assert
    }
}