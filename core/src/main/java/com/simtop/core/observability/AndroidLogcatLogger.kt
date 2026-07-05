package com.simtop.core.observability

import android.util.Log
import com.simtop.core.core.LogPriority
import com.simtop.core.core.Logger

/**
 * Only reached through DI in the running app - unit tests inject [com.simtop.core.core.NoOpLogger]
 * or a fake instead, so android.util.Log's unmocked-by-default JVM stub is never on the call path.
 */
class AndroidLogcatLogger : Logger {
  override fun log(priority: LogPriority, tag: String, message: String, throwable: Throwable?) {
    when (priority) {
      LogPriority.DEBUG -> Log.d(tag, message, throwable)
      LogPriority.INFO -> Log.i(tag, message, throwable)
      LogPriority.WARN -> Log.w(tag, message, throwable)
      LogPriority.ERROR -> Log.e(tag, message, throwable)
    }
  }
}
