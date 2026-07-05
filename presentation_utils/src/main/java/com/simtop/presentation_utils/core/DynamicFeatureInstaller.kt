package com.simtop.presentation_utils.core

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus

/**
 * State holder driving one module's install flow and exposing it as a single [InstallStatus].
 *
 * Lifecycle contract:
 * - [start] begins (or retries) an install; it is a no-op while one is in flight.
 * - [cancel] is for **explicit user intent only** (dialog dismissed, confirmation declined).
 * - [release] only unregisters the listener — it never cancels. The Play Core install continues
 *   server-side across configuration changes and even process death; a new installer re-attaches
 *   via the constructor's `installedModules` check or by re-adopting the running session below.
 *   Cancelling on dispose would kill the very install the user just approved when the Play
 *   confirmation sheet backgrounds/recreates the Activity.
 */
@Stable
class DynamicFeatureInstaller(
  private val manager: SplitInstallManager,
  private val moduleName: String,
) {

  var status by
    mutableStateOf(
      if (moduleName in manager.installedModules) InstallStatus.Installed else InstallStatus.Idle
    )
    private set

  private var activeSessionId: Int? = null
  private var listenerRegistered = false

  private val listener = SplitInstallStateUpdatedListener { state ->
    // Race fix: on fast/cached installs the first (sometimes only) state update can arrive
    // before startInstall's success callback delivers the session ID. Adopt the session by
    // module name so that update is never dropped.
    if (activeSessionId == null && moduleName in state.moduleNames()) {
      activeSessionId = state.sessionId()
    }
    if (state.sessionId() == activeSessionId) updateStatus(state)
  }

  fun start() {
    when (status) {
      InstallStatus.Idle,
      InstallStatus.Cancelled,
      is InstallStatus.Failed -> Unit
      else -> return // in flight or already installed
    }
    if (!listenerRegistered) {
      manager.registerListener(listener)
      listenerRegistered = true
    }
    status = InstallStatus.Pending
    val request = SplitInstallRequest.newBuilder().addModule(moduleName).build()
    manager
      .startInstall(request)
      .addOnSuccessListener { sessionId ->
        if (activeSessionId == null) activeSessionId = sessionId
      }
      .addOnFailureListener { throwable ->
        status =
          InstallStatus.Failed(
            (throwable as? SplitInstallException)?.errorCode ?: SplitInstallErrorCode.INTERNAL_ERROR
          )
      }
  }

  /** Cancels the running session. Call only from explicit user intent — never from lifecycle. */
  fun cancel() {
    val sessionId = activeSessionId
    if (sessionId != null) {
      // The listener delivers CANCELED, which maps to InstallStatus.Cancelled.
      manager.cancelInstall(sessionId)
    } else {
      status = InstallStatus.Cancelled
    }
  }

  /** Launches the Play confirmation sheet if (and only if) confirmation is what Play awaits. */
  fun startConfirmationDialog(launcher: ActivityResultLauncher<IntentSenderRequest>) {
    (status as? InstallStatus.RequiresUserConfirmation)?.let {
      manager.startConfirmationDialogForResult(it.sessionState, launcher)
    }
  }

  /** Detaches from the manager. Does NOT cancel — see the class KDoc for why. */
  internal fun release() {
    if (listenerRegistered) {
      manager.unregisterListener(listener)
      listenerRegistered = false
    }
  }

  private fun updateStatus(state: SplitInstallSessionState) {
    status =
      when (state.status()) {
        SplitInstallSessionStatus.PENDING,
        SplitInstallSessionStatus.CANCELING -> InstallStatus.Pending
        SplitInstallSessionStatus.DOWNLOADING -> {
          val total = state.totalBytesToDownload()
          InstallStatus.Downloading(
            if (total > 0) state.bytesDownloaded().toFloat() / total.toFloat() else 0f
          )
        }
        SplitInstallSessionStatus.DOWNLOADED,
        SplitInstallSessionStatus.INSTALLING -> InstallStatus.Installing
        SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION ->
          InstallStatus.RequiresUserConfirmation(state)
        SplitInstallSessionStatus.INSTALLED -> {
          activeSessionId = null
          InstallStatus.Installed
        }
        SplitInstallSessionStatus.FAILED,
        SplitInstallSessionStatus.UNKNOWN -> {
          activeSessionId = null
          InstallStatus.Failed(state.errorCode())
        }
        SplitInstallSessionStatus.CANCELED -> {
          activeSessionId = null
          InstallStatus.Cancelled
        }
        // SplitInstallSessionStatus is an @IntDef, so the compiler can't prove
        // exhaustiveness; a status added in a future Play Core version keeps the current
        // state instead of being misclassified.
        else -> status
      }
  }
}
