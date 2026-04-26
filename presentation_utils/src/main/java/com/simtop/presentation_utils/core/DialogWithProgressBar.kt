package com.simtop.presentation_utils.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.simtop.billionbeers.catalog_annotations.CatalogComponent
data class CatalogSettings(
    val dismissOnClickOutside: Boolean = false
)

@CatalogComponent(
    tab = "Utilities",
    name = "Progress Dialog",
    demoContainer = "DialogWithProgressBarDemo"
)
@Composable
fun DialogWithProgressBar(
    setShowDialog: (Boolean) -> Unit = {},
    number: Float = 0.0f,
    text: String = "Downloading feature...",
    settings: CatalogSettings = CatalogSettings()
) {
  Dialog(
    onDismissRequest = { setShowDialog(false) },
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = settings.dismissOnClickOutside)
  ) {
    DialogContent(number = number, text = text)
  }
}

@Composable
fun DialogWithProgressBarDemo(
    number: Float,
    text: String
) {
    var showDialog by remember { mutableStateOf(false) }
    var animate by remember { mutableStateOf(false) }
    var simulatedNumber by remember { mutableStateOf(number) }

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
        horizontalAlignment = Alignment.CenterHorizontally
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
                settings = CatalogSettings(dismissOnClickOutside = true)
            )
        }
    }
}

@Composable
fun DialogContent(
    number: Float,
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
        modifier.fillMaxWidth()
            .background(Color.White, shape = RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            LinearProgressIndicator(progress = { number }, modifier = Modifier.fillMaxWidth())
        }
    }
}
