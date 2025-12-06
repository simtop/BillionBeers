package com.simtop.presentation_utils.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun DialogWithProgressBar(setShowDialog: (Boolean) -> Unit, number: Float = 0.1f) {
  Dialog(
    onDismissRequest = { setShowDialog(false) },
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.fillMaxWidth()
          .background(Color.White, shape = RoundedCornerShape(8.dp))
          .padding(16.dp)
    ) {
      LinearProgressIndicator(progress = { number }, modifier = Modifier.fillMaxWidth())
    }
  }
}
