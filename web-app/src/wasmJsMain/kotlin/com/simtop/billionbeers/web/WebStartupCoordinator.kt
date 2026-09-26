package com.simtop.billionbeers.web

import kotlinx.coroutines.CancellationException

internal sealed interface WebStartupResult<out Runtime, out Session> {
  data class Failed(val message: String) : WebStartupResult<Nothing, Nothing>

  data class Ready<Runtime, Session>(val runtime: Runtime, val session: Session) :
    WebStartupResult<Runtime, Session>
}

internal class WebStartupCoordinator<Runtime, Session>(
  private val openRuntime: suspend () -> Runtime,
  private val createSession: (Runtime) -> Session,
  private val closeRuntime: (Runtime) -> Unit,
) {
  suspend fun start(): WebStartupResult<Runtime, Session> {
    val runtime =
      try {
        openRuntime()
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        return WebStartupResult.Failed(error.message ?: "Unable to start the Web app")
      }

    return try {
      WebStartupResult.Ready(runtime, createSession(runtime))
    } catch (error: CancellationException) {
      closeRuntime(runtime)
      throw error
    } catch (error: Throwable) {
      closeRuntime(runtime)
      WebStartupResult.Failed(error.message ?: "Unable to start the Web app")
    }
  }
}
