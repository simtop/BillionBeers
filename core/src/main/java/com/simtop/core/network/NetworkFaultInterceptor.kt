package com.simtop.core.network

import com.simtop.core.core.NetworkFaultController
import com.simtop.core.core.NetworkFaultMode
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Debug-only fault injection: lets the debug drawer force 404/500 responses or extra latency
 * without touching any call site, so error-state UI is testable on demand.
 */
class NetworkFaultInterceptor(private val controller: NetworkFaultController) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    return when (controller.mode.value) {
      NetworkFaultMode.NONE -> chain.proceed(request)
      NetworkFaultMode.EXTRA_LATENCY -> {
        Thread.sleep(EXTRA_LATENCY_MS)
        chain.proceed(request)
      }
      NetworkFaultMode.FORCE_404 -> fakeErrorResponse(request, code = 404, message = "Not Found")
      NetworkFaultMode.FORCE_500 ->
        fakeErrorResponse(request, code = 500, message = "Internal Server Error")
    }
  }

  private fun fakeErrorResponse(request: Request, code: Int, message: String): Response =
    Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(code)
      .message(message)
      .body("".toResponseBody(null))
      .build()

  private companion object {
    const val EXTRA_LATENCY_MS = 3000L
  }
}
