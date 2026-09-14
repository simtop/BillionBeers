package com.simtop.core.core

import java.util.Locale

class DefaultLanguageProvider : LanguageProvider {
  override fun currentLanguageCode(): String = Locale.getDefault().language
}
