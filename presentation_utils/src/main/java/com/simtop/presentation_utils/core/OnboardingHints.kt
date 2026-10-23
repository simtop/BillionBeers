package com.simtop.presentation_utils.core

import androidx.compose.runtime.staticCompositionLocalOf

/** Stable identity for one introduction. Keep its value when copy or app versions change. */
@JvmInline value class IntroductionId(val value: String)

object OnboardingIntroductions {
  val welcome = IntroductionId("welcome")
  val favorites = IntroductionId("favorites")
}

/** The Android host owns completion persistence. Other hosts default to no hints. */
fun interface OnboardingHints {
  /** Claims an introduction once, without waiting for disk persistence. Call on the main thread. */
  fun claim(id: IntroductionId): Boolean
}

val LocalOnboardingHints = staticCompositionLocalOf<OnboardingHints?> { null }
