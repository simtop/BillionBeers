package com.simtop.billionbeers.debug

import android.os.StrictMode

/**
 * Turns "you can't tell from a call site whether a callee blocks" into a stack trace on the first
 * debug run.
 *
 * This is the reason the ViewModels here call a bare `viewModelScope.launch { }` instead of
 * `launch(dispatchers.io)`. Wrapping every call in `io` looks defensive, but it is the opposite: if
 * a repository ever starts doing real blocking work, running it on `io` means no exception, no
 * report, just a quietly slower app. StrictMode keeps the failure loud and local, so main-safety
 * can stay the callee's responsibility - which is the only way it composes.
 *
 * Two of the three cases are already enforced by the platform without this: Room throws on a
 * main-thread query unless `allowMainThreadQueries()` is set (it is not, outside one instrumented
 * test), and Android throws `NetworkOnMainThreadException` for blocking sockets. StrictMode covers
 * the rest - disk reads and writes, and slow calls the app reports itself.
 *
 * `penaltyLog`, not `penaltyDeath`: a violation should interrupt whoever caused it, not make the
 * debug build unusable for everyone else on an unrelated screen. Tighten it if violations ever stop
 * being rare.
 *
 * Debug-only by construction - this file lives in `src/debug` and has a no-op twin in
 * `src/release`, the same shape as [DebugDrawerHost].
 */
fun enableStrictMode() {
  StrictMode.setThreadPolicy(
    StrictMode.ThreadPolicy.Builder()
      .detectDiskReads()
      .detectDiskWrites()
      .detectNetwork()
      .detectCustomSlowCalls()
      .penaltyLog()
      .build()
  )

  StrictMode.setVmPolicy(
    StrictMode.VmPolicy.Builder()
      .detectLeakedSqlLiteObjects()
      .detectLeakedClosableObjects()
      .detectActivityLeaks()
      .penaltyLog()
      .build()
  )
}
