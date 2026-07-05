package com.simtop.presentation_utils.core

import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import io.mockk.every
import io.mockk.mockk
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class DynamicFeatureInstallerTest {

  private val manager = RecordingSplitInstallManager()
  private val installer = DynamicFeatureInstaller(manager, MODULE)

  @Test
  fun `already installed module short-circuits to Installed`() {
    val preInstalled = RecordingSplitInstallManager(installedModules = setOf(MODULE))

    val freshInstaller = DynamicFeatureInstaller(preInstalled, MODULE)

    freshInstaller.status shouldBeEqualTo InstallStatus.Installed
  }

  @Test
  fun `start registers one listener and moves to Pending`() {
    installer.start()

    installer.status shouldBeEqualTo InstallStatus.Pending
    manager.listeners.size shouldBeEqualTo 1
    manager.startTasks.size shouldBeEqualTo 1
  }

  @Test
  fun `happy path - downloading, installing, installed`() {
    installer.start()
    manager.startTasks.single().complete(SESSION_ID)

    manager.emit(state(SplitInstallSessionStatus.DOWNLOADING, downloaded = 50, total = 100))
    installer.status shouldBeEqualTo InstallStatus.Downloading(progress = 0.5f)

    manager.emit(state(SplitInstallSessionStatus.DOWNLOADED))
    installer.status shouldBeEqualTo InstallStatus.Installing

    manager.emit(state(SplitInstallSessionStatus.INSTALLED))
    installer.status shouldBeEqualTo InstallStatus.Installed
  }

  @Test
  fun `listener update that beats the session-id callback is adopted, not dropped`() {
    installer.start()

    // The state update arrives BEFORE startInstall's success callback delivers the session ID.
    manager.emit(state(SplitInstallSessionStatus.DOWNLOADING, downloaded = 25, total = 100))
    installer.status shouldBeEqualTo InstallStatus.Downloading(progress = 0.25f)

    // The late callback must not confuse the already-adopted session.
    manager.startTasks.single().complete(SESSION_ID)
    manager.emit(state(SplitInstallSessionStatus.INSTALLED))
    installer.status shouldBeEqualTo InstallStatus.Installed
  }

  @Test
  fun `updates from other sessions are ignored after adoption`() {
    installer.start()
    manager.startTasks.single().complete(SESSION_ID)
    manager.emit(state(SplitInstallSessionStatus.DOWNLOADING, downloaded = 50, total = 100))

    manager.emit(
      state(
        SplitInstallSessionStatus.FAILED,
        sessionId = OTHER_SESSION_ID,
        modules = listOf("other"),
      )
    )

    installer.status shouldBeEqualTo InstallStatus.Downloading(progress = 0.5f)
  }

  @Test
  fun `downloading with unknown total reports zero progress`() {
    installer.start()
    manager.startTasks.single().complete(SESSION_ID)

    manager.emit(state(SplitInstallSessionStatus.DOWNLOADING, downloaded = 10, total = 0))

    installer.status shouldBeEqualTo InstallStatus.Downloading(progress = 0f)
  }

  @Test
  fun `startInstall failure surfaces the SplitInstallException error code`() {
    installer.start()

    manager.startTasks.single().fail(SplitInstallException(SplitInstallErrorCode.NETWORK_ERROR))

    installer.status shouldBeEqualTo InstallStatus.Failed(SplitInstallErrorCode.NETWORK_ERROR)
  }

  @Test
  fun `startInstall failure without a SplitInstallException maps to INTERNAL_ERROR`() {
    installer.start()

    manager.startTasks.single().fail(IllegalStateException("boom"))

    installer.status shouldBeEqualTo InstallStatus.Failed(SplitInstallErrorCode.INTERNAL_ERROR)
  }

  @Test
  fun `failed install can be retried with a fresh session`() {
    installer.start()
    manager.startTasks.single().complete(SESSION_ID)
    manager.emit(
      state(SplitInstallSessionStatus.FAILED, errorCode = SplitInstallErrorCode.NETWORK_ERROR)
    )
    installer.status shouldBeEqualTo InstallStatus.Failed(SplitInstallErrorCode.NETWORK_ERROR)

    installer.start()

    installer.status shouldBeEqualTo InstallStatus.Pending
    manager.startTasks.size shouldBeEqualTo 2
    manager.startTasks.last().complete(OTHER_SESSION_ID)
    manager.emit(state(SplitInstallSessionStatus.INSTALLED, sessionId = OTHER_SESSION_ID))
    installer.status shouldBeEqualTo InstallStatus.Installed
  }

  @Test
  fun `cancel with an active session cancels via the manager and maps CANCELED to Cancelled`() {
    installer.start()
    manager.startTasks.single().complete(SESSION_ID)

    installer.cancel()
    manager.cancelledSessions shouldBeEqualTo listOf(SESSION_ID)

    manager.emit(state(SplitInstallSessionStatus.CANCELED))
    installer.status shouldBeEqualTo InstallStatus.Cancelled
  }

  @Test
  fun `cancel before the session id is known goes straight to Cancelled`() {
    installer.start()

    installer.cancel()

    installer.status shouldBeEqualTo InstallStatus.Cancelled
    manager.cancelledSessions shouldBeEqualTo emptyList()
  }

  @Test
  fun `requires-user-confirmation exposes the session state and forwards it to Play`() {
    installer.start()
    manager.startTasks.single().complete(SESSION_ID)
    val confirmationState = confirmationState()

    manager.emit(confirmationState)
    installer.status shouldBeInstanceOf InstallStatus.RequiresUserConfirmation::class

    installer.startConfirmationDialog(launcher = RecordingLauncher())
    manager.confirmationRequestedFor shouldBeEqualTo confirmationState
  }

  @Test
  fun `declined confirmation cancels the running session`() {
    installer.start()
    manager.startTasks.single().complete(SESSION_ID)
    manager.emit(confirmationState())

    // The loader calls cancel() when the Play sheet result is not RESULT_OK.
    installer.cancel()
    manager.cancelledSessions shouldBeEqualTo listOf(SESSION_ID)

    manager.emit(state(SplitInstallSessionStatus.CANCELED))
    installer.status shouldBeEqualTo InstallStatus.Cancelled
  }

  @Test
  fun `retirement releases the listener without cancelling the session`() {
    installer.start()
    manager.startTasks.single().complete(SESSION_ID)

    installer.onRetired()

    manager.listeners shouldBeEqualTo emptyList()
    manager.cancelledSessions shouldBeEqualTo emptyList()
    // Detached: further updates no longer reach the installer.
    manager.emit(state(SplitInstallSessionStatus.INSTALLED))
    installer.status shouldBeEqualTo InstallStatus.Pending
  }

  @Test
  fun `start is a no-op while an install is in flight`() {
    installer.start()
    manager.startTasks.single().complete(SESSION_ID)
    manager.emit(state(SplitInstallSessionStatus.DOWNLOADING, downloaded = 50, total = 100))

    installer.start()

    installer.status shouldBeEqualTo InstallStatus.Downloading(progress = 0.5f)
    manager.startTasks.size shouldBeEqualTo 1
    manager.listeners.size shouldBeEqualTo 1
  }

  private fun state(
    status: Int,
    sessionId: Int = SESSION_ID,
    errorCode: Int = SplitInstallErrorCode.NO_ERROR,
    downloaded: Long = 0,
    total: Long = 0,
    modules: List<String> = listOf(MODULE),
  ): SplitInstallSessionState =
    SplitInstallSessionState.create(
      sessionId,
      status,
      errorCode,
      downloaded,
      total,
      modules,
      emptyList(),
    )

  // SplitInstallSessionState.create() refuses REQUIRES_USER_CONFIRMATION (it demands a real
  // resolution intent), so that one state is mocked.
  private fun confirmationState(): SplitInstallSessionState =
    mockk<SplitInstallSessionState> {
      every { sessionId() } returns SESSION_ID
      every { status() } returns SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION
      every { moduleNames() } returns listOf(MODULE)
    }

  private companion object {
    const val MODULE = "beerdetail"
    const val SESSION_ID = 7
    const val OTHER_SESSION_ID = 9
  }
}
