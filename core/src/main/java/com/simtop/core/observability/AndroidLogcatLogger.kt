package com.simtop.core.observability

import android.util.Log
import com.simtop.core.core.Diagnostic
import com.simtop.core.core.DiagnosticLevel
import com.simtop.core.core.LogTag
import com.simtop.core.core.Logger

/**
 * Only reached through DI in the running app. The structured diagnostic is rendered from its closed
 * vocabulary; no caller-controlled message or throwable reaches Logcat.
 */
class AndroidLogcatLogger : Logger {
  override fun log(tag: LogTag, diagnostic: Diagnostic) {
    val message = "${diagnostic.area}:${diagnostic.code}:${diagnostic.level}"
    when (diagnostic.level) {
      DiagnosticLevel.INFO -> Log.i(tag.value, message)
      DiagnosticLevel.WARNING -> Log.w(tag.value, message)
      DiagnosticLevel.ERROR -> Log.e(tag.value, message)
    }
  }
}
