package com.simtop.beer_database.utils

import platform.Foundation.NSDate

internal actual fun currentTimeMillis(): Long =
  ((NSDate().timeIntervalSinceReferenceDate + 978_307_200.0) * 1_000.0).toLong()
