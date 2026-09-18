package com.simtop.core.core

import platform.Foundation.NSDate

internal actual fun currentEpochMillis(): Long =
  ((NSDate().timeIntervalSinceReferenceDate + 978_307_200.0) * 1_000.0).toLong()
