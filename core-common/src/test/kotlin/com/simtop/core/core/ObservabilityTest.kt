package com.simtop.core.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ObservabilityTest {

  @Test
  fun `typed analytics event reaches the in-memory recorder`() {
    val tracker = RecordingAnalyticsTracker()
    val event = AnalyticsEvent.SearchSubmitted(resultCount = 12)

    tracker.logEvent(event)

    assertEquals(listOf(event), tracker.events)
  }

  @Test
  fun `analytics rejects negative result counts`() {
    assertThrows(IllegalArgumentException::class.java) {
      AnalyticsEvent.SearchSubmitted(resultCount = -1)
    }
  }

  @Test
  fun `catalog ids reject URLs queries fragments and free-form text`() {
    assertEquals("42", CatalogId.from("42")?.value)
    assertNull(CatalogId.from("https://brewbuddy.dev/beers/42"))
    assertNull(CatalogId.from("42?query=secret"))
    assertNull(CatalogId.from("search text"))
  }

  @Test
  fun `typed diagnostic and context reach the crash recorder`() {
    val reporter = RecordingCrashReporter()
    val diagnostic =
      Diagnostic(
        area = DiagnosticArea.NETWORK,
        code = DiagnosticCode.NETWORK_UNAVAILABLE,
        level = DiagnosticLevel.ERROR,
      )
    val context = CrashContext(operation = ObservabilityOperation.LOAD_BEERS)

    reporter.record(diagnostic)
    reporter.log(diagnostic)
    reporter.setContext(context)

    assertEquals(listOf(diagnostic), reporter.recorded)
    assertEquals(listOf(diagnostic), reporter.logged)
    assertEquals(listOf(context), reporter.contexts)
  }

  @Test
  fun `logger records only structured diagnostics`() {
    val logger = RecordingLogger()
    val diagnostic =
      Diagnostic(
        area = DiagnosticArea.DATA_MAPPING,
        code = DiagnosticCode.MISSING_BEER_ID,
        level = DiagnosticLevel.WARNING,
      )

    logger.warn(LogTag.BEERS_MAPPER, diagnostic)

    assertEquals(LogPriority.WARN, logger.priority)
    assertEquals(LogTag.BEERS_MAPPER, logger.tag)
    assertEquals(diagnostic, logger.diagnostic)
  }

  private class RecordingAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()

    override fun logEvent(event: AnalyticsEvent) {
      events += event
    }
  }

  private class RecordingCrashReporter : CrashReporter {
    val recorded = mutableListOf<Diagnostic>()
    val logged = mutableListOf<Diagnostic>()
    val contexts = mutableListOf<CrashContext>()

    override fun record(diagnostic: Diagnostic) {
      recorded += diagnostic
    }

    override fun log(diagnostic: Diagnostic) {
      logged += diagnostic
    }

    override fun setContext(context: CrashContext) {
      contexts += context
    }
  }

  private class RecordingLogger : Logger {
    var priority: LogPriority? = null
    var tag: LogTag? = null
    var diagnostic: Diagnostic? = null

    override fun log(priority: LogPriority, tag: LogTag, diagnostic: Diagnostic) {
      this.priority = priority
      this.tag = tag
      this.diagnostic = diagnostic
    }
  }
}
