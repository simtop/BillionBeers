package com.simtop.presentation_utils.core

import com.google.android.play.core.splitinstall.SplitInstallSessionState

/**
 * The dynamic-feature install flow as a sealed state machine. One value describes the whole flow,
 * so contradictory combinations (dialog visible + error set + installed) are unrepresentable — the
 * UI renders a `when` over this and nothing else.
 *
 * States report raw truth (e.g. [Downloading.progress] is the real byte ratio); presentation
 * concerns like a minimum visible progress belong to the composable that renders the state.
 */
sealed interface InstallStatus {

  /** Nothing started yet (or a fresh installer found the module absent). */
  data object Idle : InstallStatus

  /** Install requested; Play has not started transferring bytes yet. */
  data object Pending : InstallStatus

  /** @param progress raw `0f..1f` byte ratio; `0f` when the total is still unknown. */
  data class Downloading(val progress: Float) : InstallStatus

  /** Bytes are on device; Play is installing the split. */
  data object Installing : InstallStatus

  /**
   * Play requires explicit user consent (large download or metered network). The session state is
   * what
   * [com.google.android.play.core.splitinstall.SplitInstallManager .startConfirmationDialogForResult]
   * needs to launch the Play confirmation sheet.
   */
  data class RequiresUserConfirmation(val sessionState: SplitInstallSessionState) : InstallStatus

  /** @param errorCode a [com.google.android.play.core.splitinstall.model.SplitInstallErrorCode]. */
  data class Failed(val errorCode: Int) : InstallStatus

  /**
   * The user cancelled (dialog dismissed or Play confirmation declined). Terminal until
   * [DynamicFeatureInstaller.start] is called again — deliberately distinct from [Idle] so the UI
   * can tell "never started, auto-start now" from "user said no, don't restart".
   */
  data object Cancelled : InstallStatus

  /** Terminal success: the module is on device and its code is loadable. */
  data object Installed : InstallStatus
}
