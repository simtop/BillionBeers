package com.simtop.presentation_utils.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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

@Composable
fun DynamicFeatureLoader(
    featureName: String,
    splitInstallManager: SplitInstallManager? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val manager = splitInstallManager ?: remember { SplitInstallManagerFactory.create(context) }
    var isInstalled by remember { mutableStateOf(manager.installedModules.contains(featureName)) }
    var isLoading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    if (isInstalled) {
        content()
    } else {
        LaunchedEffect(featureName) {
            isLoading = true
            showDialog = true
            val request = SplitInstallRequest.newBuilder()
                .addModule(featureName)
                .build()

            val listener = SplitInstallStateUpdatedListener { state ->
                when (state.status()) {
                    SplitInstallSessionStatus.INSTALLED -> {
                        downloadProgress = 1.0f
                        isInstalled = true
                        isLoading = false
                        showDialog = false
                    }
                    SplitInstallSessionStatus.DOWNLOADING -> {
                        val totalBytes = state.totalBytesToDownload()
                        val downloadedBytes = state.bytesDownloaded()
                        val progress = if (totalBytes > 0) {
                            downloadedBytes.toFloat() / totalBytes.toFloat()
                        } else {
                            0f
                        }
                        // Ensure progress is at least 0.25f so it's noticeable
                        downloadProgress = if (progress > 0.25f) progress else 0.25f
                    }
                    SplitInstallSessionStatus.PENDING -> {
                        downloadProgress = 0.1f
                    }
                    SplitInstallSessionStatus.FAILED -> {
                        isLoading = false
                        showDialog = false
                        error = "Failed to install feature: ${state.errorCode()}"
                    }
                    else -> {
                        // Handle other states
                    }
                }
            }

            manager.registerListener(listener)
            manager.startInstall(request)
                .addOnFailureListener {
                    isLoading = false
                    showDialog = false
                    error = it.message
                }
                .addOnSuccessListener {
                    // Listener handles success
                }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (showDialog) {
                DialogWithProgressBar(
                    setShowDialog = { showDialog = it },
                    number = downloadProgress
                )
            }
        }
    }
}
