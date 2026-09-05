package com.simtop.billionbeers.di

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.common.IntentSenderForResultStarter
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.simtop.navigation.DynamicFeature
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class, replaces = [SplitInstallModule::class])
@BindingContainer
object ReleaseSmokeSplitInstallModule {
  @Provides
  @SingleIn(AppScope::class)
  fun provideSplitInstallManager(): SplitInstallManager = ReleaseSmokeSplitInstallManager
}

private object ReleaseSmokeSplitInstallManager : SplitInstallManager {
  private val installedModules = DynamicFeature.entries.mapTo(mutableSetOf()) { it.moduleName }

  override fun startInstall(request: SplitInstallRequest): Task<Int> = Tasks.forResult(0)

  override fun cancelInstall(sessionId: Int): Task<Void> = Tasks.forResult(null)

  override fun getSessionState(sessionId: Int): Task<SplitInstallSessionState> = Tasks.forCanceled()

  override fun getSessionStates(): Task<List<SplitInstallSessionState>> =
    Tasks.forResult(emptyList())

  override fun registerListener(listener: SplitInstallStateUpdatedListener) = Unit

  override fun unregisterListener(listener: SplitInstallStateUpdatedListener) = Unit

  override fun getInstalledModules(): Set<String> = installedModules

  override fun getInstalledLanguages(): Set<String> = emptySet()

  override fun startConfirmationDialogForResult(
    state: SplitInstallSessionState,
    launcher: ActivityResultLauncher<IntentSenderRequest>,
  ): Boolean = false

  override fun startConfirmationDialogForResult(
    state: SplitInstallSessionState,
    activity: Activity,
    requestCode: Int,
  ): Boolean = false

  override fun startConfirmationDialogForResult(
    state: SplitInstallSessionState,
    starter: IntentSenderForResultStarter,
    requestCode: Int,
  ): Boolean = false

  override fun zza(listener: SplitInstallStateUpdatedListener) = Unit

  override fun zzb(listener: SplitInstallStateUpdatedListener) = Unit

  override fun deferredInstall(modules: List<String>): Task<Void> = Tasks.forResult(null)

  override fun deferredUninstall(modules: List<String>): Task<Void> = Tasks.forResult(null)

  override fun deferredLanguageInstall(languages: List<java.util.Locale>): Task<Void> =
    Tasks.forResult(null)

  override fun deferredLanguageUninstall(languages: List<java.util.Locale>): Task<Void> =
    Tasks.forResult(null)
}
