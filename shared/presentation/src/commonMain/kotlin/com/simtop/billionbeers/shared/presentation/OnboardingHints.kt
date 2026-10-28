package com.simtop.billionbeers.shared.presentation

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.jvm.JvmInline

/** Stable identity for one introduction. Keep its value when copy or app versions change. */
@JvmInline value class IntroductionId(val value: String)

object OnboardingIntroductions {
  val welcome = IntroductionId("welcome")
  val favorites = IntroductionId("favorites")
}

/** Each platform host owns completion persistence. */
fun interface OnboardingHints {
  /** Claims an introduction once, without waiting for disk persistence. Call on the main thread. */
  fun claim(id: IntroductionId): Boolean
}

val LocalOnboardingHints = staticCompositionLocalOf<OnboardingHints?> { null }
