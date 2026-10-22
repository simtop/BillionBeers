package com.simtop.billionbeers.shared.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import com.simtop.billionbeers.shared.designsystem.controls.BrandText

/**
 * Labels for one navigation container, with centered lines and equal line space. Natural line
 * counts follow the actual width and font; single-line labels stay compact.
 */
@Composable
fun rememberNavigationLabel(labels: List<String>): @Composable (String) -> Unit {
  val lineCounts = remember(labels) { mutableStateMapOf<String, Int>() }
  return { text ->
    BrandText(
      text = text,
      textAlign = TextAlign.Center,
      minLines = lineCounts.values.maxOrNull() ?: 1,
      onTextLayout = { result ->
        if (lineCounts[text] != result.lineCount) lineCounts[text] = result.lineCount
      },
    )
  }
}
