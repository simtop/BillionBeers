package com.simtop.billionbeers.core.designsystem.component

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
  this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick
  )
}

fun showToast(context: Context, message: String) {
  Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
