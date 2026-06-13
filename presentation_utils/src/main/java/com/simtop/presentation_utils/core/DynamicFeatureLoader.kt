package com.simtop.presentation_utils.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.simtop.billionbeers.core.designsystem.component.DialogWithProgressBar

@Composable
fun DynamicFeatureLoader(
    featureName: String,
    splitInstallManager: SplitInstallManager? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val manager = splitInstallManager ?: remember { SplitInstallManagerFactory.create(context) }
    var isInstalled by
    remember(featureName) { mutableStateOf(manager.installedModules.contains(featureName)) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    // Track the session we started so the listener ignores updates from other installs.
    var sessionId by remember { mutableStateOf<Int?>(null) }
    if (isInstalled) {
        content()
        return
    }

    DisposableEffect(featureName) {
        showDialog = true

        val listener = SplitInstallStateUpdatedListener { state ->
            // Ignore state updates that belong to a different module's install session.
            if (state.sessionId() != sessionId) return@SplitInstallStateUpdatedListener
            when (state.status()) {
                SplitInstallSessionStatus.INSTALLED -> {
                    downloadProgress = 1.0f
                    isInstalled = true
                    showDialog = false
                }

                SplitInstallSessionStatus.DOWNLOADING -> {
                    val totalBytes = state.totalBytesToDownload()
                    val downloadedBytes = state.bytesDownloaded()
                    val progress =
                        if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
                    // Ensure progress is at least MIN_VISIBLE_PROGRESS so it's noticeable.
                    downloadProgress =
                        if (progress > MIN_VISIBLE_PROGRESS) progress else MIN_VISIBLE_PROGRESS
                }

                SplitInstallSessionStatus.PENDING -> {
                    downloadProgress = PENDING_PROGRESS
                }

                SplitInstallSessionStatus.FAILED,
                SplitInstallSessionStatus.CANCELED -> {
                    showDialog = false
                    error = "Failed to install feature: ${state.errorCode()}"
                }

                else -> {
                    // Other transient states (REQUIRES_USER_CONFIRMATION, INSTALLING, ...) need no UI change.
                }
            }
        }
        manager.registerListener(listener)

        val request = SplitInstallRequest.newBuilder().addModule(featureName).build()
        manager
            .startInstall(request)
            .addOnSuccessListener { sessionId = it }
            .addOnFailureListener {
                showDialog = false
                error = it.message
            }

        onDispose {
            // Always unregister to avoid leaking the listener for the lifetime of the manager.
            manager.unregisterListener(listener)
            // If we left before the installation finished, cancel the in-flight session.
            sessionId?.let { id -> if (!isInstalled) manager.cancelInstall(id) }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showDialog) {
            DialogWithProgressBar(setShowDialog = { showDialog = it }, number = downloadProgress)
        }
    }
}

private const val MIN_VISIBLE_PROGRESS = 0.25f
private const val PENDING_PROGRESS = 0.1f
