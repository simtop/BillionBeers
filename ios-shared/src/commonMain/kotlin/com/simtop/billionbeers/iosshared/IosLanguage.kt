package com.simtop.billionbeers.iosshared

internal fun iosLanguage(languageCode: String): String =
  languageCode.substringBefore('-').lowercase().let { language ->
    when (language) {
      "es", "fr" -> language
      else -> "en"
    }
  }
