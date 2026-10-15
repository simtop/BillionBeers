package com.simtop.billionbeers.shared.beerdetail

fun formatBeerMetric(value: Double): String =
  if (value == value.toInt().toDouble()) value.toInt().toString() else value.toString()

fun formatBeerMetric(value: Double, decimalSeparator: Char): String =
  formatBeerMetric(value).replace('.', decimalSeparator)
