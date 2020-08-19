package com.simtop.billionbeers.data

import com.simtop.billionbeers.FAKE_JSON
import com.simtop.billionbeers.TestMockWebService
import com.simtop.billionbeers.fakeBeerApiResponse
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import java.lang.Exception
import java.net.HttpURLConnection

class BeersRemoteSourceTest : TestMockWebService() {

    @Test
    fun `when we make the api call it returns success when we get a success`() {
        val expectedResult = fakeBeerApiResponse
        mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK)

        val response = runBlocking {
            apiService.getListOfBeers(1)
        }

        response.toString() shouldBeEqualTo expectedResult.toString()
    }

    @Test(expected = Exception::class)
    fun `when we make the api call it returns failure when we get a failure`() {
        mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_UNAVAILABLE)

        runBlocking {
            apiService.getListOfBeers(1)
        }
    }
}