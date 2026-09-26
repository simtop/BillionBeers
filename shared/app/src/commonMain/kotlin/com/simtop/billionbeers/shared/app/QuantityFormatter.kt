package com.simtop.billionbeers.shared.app

fun formatCount(count: Int, singular: String, plural: String): String =
  "$count ${if (count == 1) singular else plural}"
