package com.simtop.core.core

fun interface AnalyticsTracker {
  fun logEvent(event: AnalyticsEvent)
}

class NoOpAnalyticsTracker : AnalyticsTracker {
  override fun logEvent(event: AnalyticsEvent) = Unit
}
