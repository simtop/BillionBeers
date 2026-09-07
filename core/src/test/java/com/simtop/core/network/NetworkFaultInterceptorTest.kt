package com.simtop.core.network

import com.simtop.core.core.NetworkFaultController
import com.simtop.core.core.NetworkFaultMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class NetworkFaultInterceptorTest {

  private val request = Request.Builder().url("https://brewbuddy.dev/beers").build()
  private val controller = TestNetworkFaultController()
  private val chain = mockk<Interceptor.Chain>()
  private val proceeded =
    Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(200)
      .message("OK")
      .body("{}".toResponseBody(null))
      .build()

  init {
    every { chain.request() } returns request
    every { chain.proceed(request) } returns proceeded
  }

  @Test
  fun `none mode delegates to the network`() {
    val result = NetworkFaultInterceptor(controller).intercept(chain)

    assertSame(proceeded, result)
    verify(exactly = 1) { chain.proceed(request) }
  }

  @Test
  fun `forced HTTP modes return synthetic errors without touching the network`() {
    val interceptor = NetworkFaultInterceptor(controller)

    controller.setMode(NetworkFaultMode.FORCE_404)
    val notFound = interceptor.intercept(chain)
    controller.setMode(NetworkFaultMode.FORCE_500)
    val serverError = interceptor.intercept(chain)

    assertEquals(404, notFound.code)
    assertEquals("Not Found", notFound.message)
    assertEquals(500, serverError.code)
    assertEquals("Internal Server Error", serverError.message)
    verify(exactly = 0) { chain.proceed(request) }
  }

  private class TestNetworkFaultController : NetworkFaultController {
    private val state = MutableStateFlow(NetworkFaultMode.NONE)
    override val mode = state

    override fun setMode(mode: NetworkFaultMode) {
      state.value = mode
    }
  }
}
