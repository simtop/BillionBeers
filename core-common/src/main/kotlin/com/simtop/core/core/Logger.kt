package com.simtop.core.core

enum class LogPriority {
  DEBUG,
  INFO,
  WARN,
  ERROR,
}

fun interface Logger {
  fun log(priority: LogPriority, tag: LogTag, diagnostic: Diagnostic)

  fun debug(tag: LogTag, diagnostic: Diagnostic) = log(LogPriority.DEBUG, tag, diagnostic)

  fun info(tag: LogTag, diagnostic: Diagnostic) = log(LogPriority.INFO, tag, diagnostic)

  fun warn(tag: LogTag, diagnostic: Diagnostic) = log(LogPriority.WARN, tag, diagnostic)

  fun error(tag: LogTag, diagnostic: Diagnostic) = log(LogPriority.ERROR, tag, diagnostic)
}

enum class LogTag(val value: String) {
  BEERS_MAPPER("BeersMapper")
}

class NoOpLogger : Logger {
  override fun log(priority: LogPriority, tag: LogTag, diagnostic: Diagnostic) = Unit
}
