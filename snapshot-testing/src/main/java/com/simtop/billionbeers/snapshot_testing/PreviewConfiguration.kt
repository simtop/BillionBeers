package com.simtop.billionbeers.snapshot_testing

import androidx.compose.runtime.Composable

data class PreviewConfiguration(
  val name: String,
  val theme: String,
  val fontScale: Float,
  val locale: String,
  val layoutDirection: String,
  val width: String,
  val previewName: String = "",
  val previewGroup: String = "",
  val widthDp: Int = -1,
  val heightDp: Int = -1,
  val uiMode: Int = 0,
  val device: String = "",
)

object AccessibilityMatrix {
  private const val LARGE_FONT_SCALE = 1.5f
  private const val EXTRA_LARGE_FONT_SCALE = 2f
  private val fontScales = listOf(1f, LARGE_FONT_SCALE, EXTRA_LARGE_FONT_SCALE)

  val configurations: List<PreviewConfiguration> =
    listOf("light", "dark").flatMap { theme ->
      fontScales.flatMap { fontScale ->
        listOf(
            "en" to "ltr",
            "fr" to "ltr",
            // Keep layout direction independent from locale so RTL goldens do not vary with
            // locale-specific numeral shaping across Paparazzi environments.
            "en" to "rtl",
          )
          .flatMap { (locale, layoutDirection) ->
            listOf("compact", "expanded").map { width ->
              PreviewConfiguration(
                name =
                  "${theme}_font${(fontScale * 100).toInt()}_${locale.replace('-', '_')}" +
                    "_${layoutDirection}_$width",
                theme = theme,
                fontScale = fontScale,
                locale = locale,
                layoutDirection = layoutDirection,
                width = width,
              )
            }
          }
      }
    }
}

data class Snapshot(
  val name: String,
  val content: @Composable () -> Unit,
  val configuration: PreviewConfiguration =
    PreviewConfiguration(
      name = "default",
      theme = "light",
      fontScale = 1f,
      locale = "en",
      layoutDirection = "ltr",
      width = "compact",
    ),
)
