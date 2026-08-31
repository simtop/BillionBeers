package com.simtop.core.core

fun interface AnalyticsTracker {
  fun logEvent(name: String, params: Map<String, String>)
}

fun AnalyticsTracker.logEvent(name: String) = logEvent(name, emptyMap())

class NoOpAnalyticsTracker : AnalyticsTracker {
  override fun logEvent(name: String, params: Map<String, String>) = Unit
}
