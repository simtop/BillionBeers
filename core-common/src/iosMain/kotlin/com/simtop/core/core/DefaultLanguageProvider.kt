package com.simtop.core.core

class DefaultLanguageProvider(private val languageCode: String = "en") : LanguageProvider {
  override fun currentLanguageCode(): String = languageCode
}
