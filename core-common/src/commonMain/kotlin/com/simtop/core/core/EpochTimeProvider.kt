package com.simtop.core.core

fun interface EpochTimeProvider {
  fun epochMillis(): Long
}

class SystemEpochTimeProvider : EpochTimeProvider {
  override fun epochMillis(): Long = currentEpochMillis()
}

internal expect fun currentEpochMillis(): Long
