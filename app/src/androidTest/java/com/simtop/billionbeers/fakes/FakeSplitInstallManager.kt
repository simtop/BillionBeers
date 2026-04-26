package com.simtop.billionbeers.fakes

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.simtop.navigation.FeatureConstants

class FakeSplitInstallManager : SplitInstallManager {
    private val installed = mutableSetOf(FeatureConstants.BEER_DETAIL_MODULE)
    
    override fun startInstall(request: com.google.android.play.core.splitinstall.SplitInstallRequest): Task<Int> {
        return Tasks.forResult(0)
    }

    override fun cancelInstall(sessionId: Int): Task<Void> {
        return Tasks.forResult(null)
    }

    override fun getSessionState(sessionId: Int): Task<SplitInstallSessionState> {
        return Tasks.forCanceled()
    }

    override fun getSessionStates(): Task<List<SplitInstallSessionState>> {
        return Tasks.forResult(emptyList())
    }

    override fun registerListener(p0: SplitInstallStateUpdatedListener) {}

    override fun unregisterListener(p0: SplitInstallStateUpdatedListener) {}

    override fun getInstalledModules(): Set<String> = installed

    override fun getInstalledLanguages(): Set<String> = emptySet()

    override fun startConfirmationDialogForResult(
        p0: SplitInstallSessionState,
        p1: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>
    ): Boolean = false

    override fun startConfirmationDialogForResult(
        p0: SplitInstallSessionState,
        p1: android.app.Activity,
        p2: Int
    ): Boolean = false

    override fun startConfirmationDialogForResult(
        p0: SplitInstallSessionState,
        p1: com.google.android.play.core.common.IntentSenderForResultStarter,
        p2: Int
    ): Boolean = false

    override fun zza(p0: SplitInstallStateUpdatedListener) {}

    override fun zzb(p0: SplitInstallStateUpdatedListener) {}

    override fun deferredInstall(modules: List<String>): Task<Void> {
        return Tasks.forResult(null)
    }

    override fun deferredUninstall(modules: List<String>): Task<Void> {
        return Tasks.forResult(null)
    }

    override fun deferredLanguageInstall(languages: List<java.util.Locale>): Task<Void> {
        return Tasks.forResult(null)
    }

    override fun deferredLanguageUninstall(languages: List<java.util.Locale>): Task<Void> {
        return Tasks.forResult(null)
    }
}
