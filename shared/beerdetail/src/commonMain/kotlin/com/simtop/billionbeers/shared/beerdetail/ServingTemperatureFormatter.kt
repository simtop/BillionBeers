package com.simtop.billionbeers.shared.beerdetail

fun formatServingTemperatureRange(
  minTemperature: Int,
  maxTemperature: Int,
  unit: String = "°C",
): String =
  if (minTemperature == maxTemperature) {
    "$minTemperature$unit"
  } else {
    "$minTemperature–$maxTemperature$unit"
  }
