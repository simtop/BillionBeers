package com.simtop.core.core

import java.util.Locale

fun interface LanguageProvider {
  fun currentLanguageCode(): String
}

class DefaultLanguageProvider : LanguageProvider {
  override fun currentLanguageCode(): String = Locale.getDefault().language
}
