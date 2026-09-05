package com.simtop.core.core

interface CrashReporter {
  fun record(diagnostic: Diagnostic)

  fun log(diagnostic: Diagnostic)

  fun setContext(context: CrashContext)
}

class NoOpCrashReporter : CrashReporter {
  override fun record(diagnostic: Diagnostic) = Unit

  override fun log(diagnostic: Diagnostic) = Unit

  override fun setContext(context: CrashContext) = Unit
}
