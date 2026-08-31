package com.simtop.core.core

enum class LogPriority {
  DEBUG,
  INFO,
  WARN,
  ERROR,
}

fun interface Logger {
  fun log(priority: LogPriority, tag: String, message: String, throwable: Throwable?)

  fun debug(tag: String, message: String) = log(LogPriority.DEBUG, tag, message, null)

  fun info(tag: String, message: String) = log(LogPriority.INFO, tag, message, null)

  fun warn(tag: String, message: String, throwable: Throwable? = null) =
    log(LogPriority.WARN, tag, message, throwable)

  fun error(tag: String, message: String, throwable: Throwable? = null) =
    log(LogPriority.ERROR, tag, message, throwable)
}

class NoOpLogger : Logger {
  override fun log(priority: LogPriority, tag: String, message: String, throwable: Throwable?) =
    Unit
}
