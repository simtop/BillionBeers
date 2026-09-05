package com.simtop.core.observability

import android.util.Log
import com.simtop.core.core.Diagnostic
import com.simtop.core.core.LogPriority
import com.simtop.core.core.LogTag
import com.simtop.core.core.Logger

/**
 * Only reached through DI in the running app. The structured diagnostic is rendered from its closed
 * vocabulary; no caller-controlled message or throwable reaches Logcat.
 */
class AndroidLogcatLogger : Logger {
  override fun log(priority: LogPriority, tag: LogTag, diagnostic: Diagnostic) {
    val message = "${diagnostic.area}:${diagnostic.code}:${diagnostic.level}"
    when (priority) {
      LogPriority.DEBUG -> Log.d(tag.value, message)
      LogPriority.INFO -> Log.i(tag.value, message)
      LogPriority.WARN -> Log.w(tag.value, message)
      LogPriority.ERROR -> Log.e(tag.value, message)
    }
  }
}
