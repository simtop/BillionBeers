package com.simtop.presentation_utils.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.simtop.billionbeers.core.designsystem.component.CatalogSettings
import com.simtop.billionbeers.core.designsystem.component.DialogWithProgressBar
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.presentation_utils.R

/**
 * Gates [content] behind the on-demand install of the [featureName] dynamic feature module,
 * rendering install progress / confirmation / failure UI until the module is available.
 *
 * [onCancelled] fires when the user abandons the install (dismisses the progress dialog, declines
 * the Play confirmation sheet, or dismisses the failure dialog) — the caller should close whatever
 * flow needed the feature.
 */
@Composable
fun DynamicFeatureLoader(
  featureName: String,
  onCancelled: () -> Unit = {},
  content: @Composable () -> Unit,
) {
  val installer = rememberDynamicFeatureInstaller(featureName)
  when (val status = installer.status) {
    InstallStatus.Installed -> content()

    InstallStatus.Idle -> {
      LaunchedEffect(installer) { installer.start() }
      InstallProgressDialog(progress = PENDING_PROGRESS, onCancel = installer::cancel)
    }

    InstallStatus.Pending ->
      InstallProgressDialog(progress = PENDING_PROGRESS, onCancel = installer::cancel)

    is InstallStatus.Downloading ->
      InstallProgressDialog(
        // State reports the raw ratio; keeping the bar visibly moving is a UI decision.
        progress = status.progress.coerceAtLeast(MIN_VISIBLE_PROGRESS),
        onCancel = installer::cancel,
      )

    InstallStatus.Installing ->
      InstallProgressDialog(progress = FULL_PROGRESS, onCancel = installer::cancel)

    is InstallStatus.RequiresUserConfirmation ->
      // Confirmation sheet wiring lands in the next commit; until then treat it like Pending so
      // the user at least keeps a cancellable dialog instead of a frozen spinner.
      InstallProgressDialog(progress = PENDING_PROGRESS, onCancel = installer::cancel)

    is InstallStatus.Failed ->
      InstallFailedDialog(
        errorCode = status.errorCode,
        onRetry = installer::start,
        onCancel = installer::cancel,
      )

    InstallStatus.Cancelled -> LaunchedEffect(installer) { onCancelled() }
  }
}

@Composable
private fun rememberDynamicFeatureInstaller(moduleName: String): DynamicFeatureInstaller {
  val manager = LocalSplitInstallManager.current
  val installer = remember(manager, moduleName) { DynamicFeatureInstaller(manager, moduleName) }
  DisposableEffect(installer) { onDispose { installer.release() } }
  return installer
}

@Composable
private fun InstallProgressDialog(progress: Float, onCancel: () -> Unit) {
  DialogWithProgressBar(
    setShowDialog = { shown -> if (!shown) onCancel() },
    number = progress,
    text = stringResource(R.string.loading_dynamic_feature),
    settings = CatalogSettings(dismissOnClickOutside = true),
  )
}

@Composable
private fun InstallFailedDialog(errorCode: Int, onRetry: () -> Unit, onCancel: () -> Unit) {
  Dialog(onDismissRequest = onCancel) {
    InstallFailedContent(errorCode = errorCode, onRetry = onRetry, onCancel = onCancel)
  }
}

@Composable
internal fun InstallFailedContent(
  errorCode: Int,
  onRetry: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      modifier
        .fillMaxWidth()
        .background(Color.White, shape = RoundedCornerShape(8.dp))
        .padding(16.dp),
  ) {
    Text(
      text = stringResource(R.string.failed_to_install_feature),
      style = MaterialTheme.typography.titleMedium,
      color = Color.Black,
    )
    Text(
      text = stringResource(R.string.install_error_code, errorCode),
      style = MaterialTheme.typography.bodyMedium,
      color = Color.Black,
      modifier = Modifier.padding(top = 8.dp),
    )
    Row(modifier = Modifier.padding(top = 16.dp)) {
      TextButton(onClick = onCancel) { Text(stringResource(R.string.install_cancel)) }
      Spacer(modifier = Modifier.width(8.dp))
      TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
  }
}

@PreviewLightDark
@Composable
fun InstallFailedContentPreview() {
  BillionBeersTheme { InstallFailedContent(errorCode = -6, onRetry = {}, onCancel = {}) }
}

private const val MIN_VISIBLE_PROGRESS = 0.25f
private const val PENDING_PROGRESS = 0.1f
private const val FULL_PROGRESS = 1f
