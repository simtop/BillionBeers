package com.simtop.billionbeers.iosshared

internal fun iosLanguage(languageCode: String): String =
  languageCode.substringBefore('-').lowercase().let { if (it == "fr") "fr" else "en" }
