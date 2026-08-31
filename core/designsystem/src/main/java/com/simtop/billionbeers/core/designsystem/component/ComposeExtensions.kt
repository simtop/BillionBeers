package com.simtop.billionbeers.core.designsystem.component

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Clickable modifier without a visual indication.
 *
 * The caller remains responsible for supplying a meaningful label and role through surrounding
 * semantics. Prefer a Material interactive component when it provides the required behavior.
 */
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
  this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
  )
}

/** Shows a short-lived platform toast; preview environments may not support it. */
@Suppress(
  "SwallowedException",
  "TooGenericExceptionCaught",
) // Toast is best-effort UI feedback; preview hosts may not provide a Toast-capable context.
fun showToast(context: Context, message: String) {
  try {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
  } catch (_: RuntimeException) {
    // Ignore for Paparazzi tests/previews where Toast might not be supported.
  }
}
