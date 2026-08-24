package com.simtop.billionbeers.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.simtop.billionbeers.catalog_annotations.CatalogComponent
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import kotlinx.coroutines.delay

/** Configuration exposed to the catalog and consumers of [DialogWithProgressBar]. */
data class CatalogSettings(val dismissOnClickOutside: Boolean = false)

@CatalogComponent(
  tab = "Utilities",
  name = "Progress Dialog",
  demoContainer = "DialogWithProgressBarDemo",
)
/**
 * Displays modal progress content.
 *
 * This component supports loading/progress and long-text states. Disabled, selected, and error
 * states do not apply; callers should model those states outside the progress dialog.
 */
@Composable
fun DialogWithProgressBar(
  setShowDialog: (Boolean) -> Unit = {},
  number: Float = 0.0f,
  text: String = "Downloading feature...",
  settings: CatalogSettings = CatalogSettings(),
) {
  Dialog(
    onDismissRequest = { setShowDialog(false) },
    properties =
      DialogProperties(
        dismissOnBackPress = false,
        dismissOnClickOutside = settings.dismissOnClickOutside,
      ),
  ) {
    DialogContent(number = number, text = text)
  }
}

/** Catalog-only interactive demo for [DialogWithProgressBar]. */
@Composable
fun DialogWithProgressBarDemo(number: Float, text: String) {
  var showDialog by remember { mutableStateOf(false) }
  var animate by remember { mutableStateOf(false) }
  var simulatedNumber by remember { mutableFloatStateOf(number) }

  // Sync simulatedNumber with the external number control when not animating
  LaunchedEffect(number) {
    if (!animate) {
      simulatedNumber = number
    }
  }

  LaunchedEffect(animate) {
    if (animate) {
      showDialog = true
      // Start from where the slider is
      simulatedNumber = number
      while (simulatedNumber < 1f) {
        delay(50)
        simulatedNumber = (simulatedNumber + 0.01f).coerceAtMost(1f)
      }
      delay(500) // Hold at 100% for a moment
      showDialog = false
      animate = false
    }
  }

  Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Show Dialog")
      Checkbox(checked = showDialog, onCheckedChange = { showDialog = it })
      Spacer(modifier = Modifier.width(16.dp))
      Text("Animate Loading")
      Checkbox(checked = animate, onCheckedChange = { animate = it })
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (showDialog) {
      DialogWithProgressBar(
        setShowDialog = {
          showDialog = it
          if (!it) animate = false // Stop animation if dismissed
        },
        number = simulatedNumber,
        text = text,
        settings = CatalogSettings(dismissOnClickOutside = true),
      )
    }
  }
}

/** Renders the reusable progress surface used by [DialogWithProgressBar]. */
@Composable
fun DialogContent(number: Float, text: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    shadowElevation = 6.dp,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 16.dp),
      )
      LinearProgressIndicator(progress = { number }, modifier = Modifier.fillMaxWidth())
    }
  }
}

/** Preview-only progress values for the dialog's supported loading states. */
class DialogProgressProvider : PreviewParameterProvider<Float> {
  override val values = sequenceOf(0f, 0.5f, 1f)
}

@PreviewLightDark
@Composable
fun DialogContentPreview(@PreviewParameter(DialogProgressProvider::class) progress: Float) {
  BillionBeersTheme { DialogContent(number = progress, text = "Downloading feature...") }
}

@AccessibilityMatrixPreview
@Composable
@Suppress("PreviewPublic")
internal fun DialogContentAccessibilityMatrixPreview() {
  BillionBeersTheme {
    DialogContent(
      number = 0.5f,
      text =
        "Downloading a very long feature description that must remain readable at large font sizes.",
    )
  }
}
