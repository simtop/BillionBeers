package com.simtop.billionbeers.shared.beerdetail

const val MAX_BEER_METRIC_FRACTION_DIGITS = 3

fun formatBeerMetric(value: Double): String =
  if (value == value.toInt().toDouble()) value.toInt().toString() else value.toString()

fun formatBeerMetric(value: Double, decimalSeparator: Char): String =
  formatBeerMetric(value).replace('.', decimalSeparator)
