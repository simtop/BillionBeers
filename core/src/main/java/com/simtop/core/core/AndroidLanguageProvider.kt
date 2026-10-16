package com.simtop.core.core

import java.util.Locale

class AndroidLanguageProvider : LanguageProvider {
  override fun currentLanguageCode(): String = Locale.getDefault().language
}
