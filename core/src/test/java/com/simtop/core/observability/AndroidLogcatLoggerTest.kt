package com.simtop.core.observability

import android.util.Log
import com.simtop.core.core.Diagnostic
import com.simtop.core.core.DiagnosticArea
import com.simtop.core.core.DiagnosticCode
import com.simtop.core.core.DiagnosticLevel
import com.simtop.core.core.LogTag
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AndroidLogcatLoggerTest {

  @BeforeEach
  fun setUp() {
    mockkStatic(Log::class)
    every { Log.i(any(), any()) } returns 0
    every { Log.w(any(), any<String>()) } returns 0
    every { Log.e(any(), any()) } returns 0
  }

  @AfterEach
  fun tearDown() {
    unmockkStatic(Log::class)
  }

  @Test
  fun `diagnostic level determines Logcat priority and only closed fields are rendered`() {
    val logger = AndroidLogcatLogger()
    DiagnosticLevel.entries.forEach { level ->
      logger.log(
        LogTag.BEERS_REMOTE_SOURCE,
        Diagnostic(DiagnosticArea.NETWORK, DiagnosticCode.UNKNOWN, level),
      )
    }

    verify(exactly = 1) { Log.i("BeersRemoteSource", "NETWORK:UNKNOWN:INFO") }
    verify(exactly = 1) { Log.w("BeersRemoteSource", "NETWORK:UNKNOWN:WARNING") }
    verify(exactly = 1) { Log.e("BeersRemoteSource", "NETWORK:UNKNOWN:ERROR") }
  }
}
