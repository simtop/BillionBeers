package com.simtop.billionbeers.data

import com.simtop.billionbeers.FAKE_JSON
import com.simtop.billionbeers.TestMockWebService
import com.simtop.billionbeers.fakeBeerApiResponse
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import java.net.HttpURLConnection

class BeersRemoteSourceTest : TestMockWebService() {

    @Test
    fun `when service succeeds we get a success response`() {
        // Arrange

        val expectedResult = fakeBeerApiResponse
        mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK)

        // Act

        val response = runBlocking {
            apiService.getListOfBeers(1)
        }

        // Assert

        response.toString() shouldBeEqualTo expectedResult.toString()
    }

    @Test(expected = Exception::class)
    fun `when service fails succeeds we throw an exception`() {
        // Arrange

        mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_UNAVAILABLE)

        // Act

        runBlocking {
            apiService.getListOfBeers(1)
        }

        // Assert
    }
}