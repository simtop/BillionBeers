package com.simtop.presentation_utils.core

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.play.core.common.IntentSenderForResultStarter
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import java.util.Locale
import java.util.concurrent.Executor

/**
 * JVM fake of [SplitInstallManager] that records interactions and lets tests drive both sides of
 * the async contract independently: [startTasks] controls when `startInstall`'s callback delivers
 * the session ID, and [emit] pushes listener state updates — including *before* that callback,
 * which is exactly the race [DynamicFeatureInstaller]'s session adoption exists for.
 *
 * The Play-provided `FakeSplitInstallManager` is not usable here: it needs a real [Context] and
 * dispatches through the main [android.os.Looper], neither of which exists in a plain JVM test.
 */
internal class RecordingSplitInstallManager(installedModules: Set<String> = emptySet()) :
  SplitInstallManager {

  private val installed = installedModules.toMutableSet()
  val listeners = mutableListOf<SplitInstallStateUpdatedListener>()
  val startTasks = mutableListOf<ControllableTask<Int>>()
  val cancelledSessions = mutableListOf<Int>()
  var confirmationRequestedFor: SplitInstallSessionState? = null

  fun emit(state: SplitInstallSessionState) {
    listeners.toList().forEach { it.onStateUpdate(state) }
  }

  override fun startInstall(request: SplitInstallRequest): Task<Int> =
    ControllableTask<Int>().also { startTasks += it }

  override fun cancelInstall(sessionId: Int): Task<Void> {
    cancelledSessions += sessionId
    return ControllableTask()
  }

  override fun registerListener(listener: SplitInstallStateUpdatedListener) {
    listeners += listener
  }

  override fun unregisterListener(listener: SplitInstallStateUpdatedListener) {
    listeners -= listener
  }

  override fun getInstalledModules(): Set<String> = installed

  override fun getInstalledLanguages(): Set<String> = emptySet()

  override fun getSessionState(sessionId: Int): Task<SplitInstallSessionState> = ControllableTask()

  override fun getSessionStates(): Task<List<SplitInstallSessionState>> = ControllableTask()

  override fun startConfirmationDialogForResult(
    state: SplitInstallSessionState,
    launcher: ActivityResultLauncher<IntentSenderRequest>,
  ): Boolean {
    confirmationRequestedFor = state
    return true
  }

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

  override fun deferredInstall(modules: List<String>): Task<Void> = ControllableTask()

  override fun deferredUninstall(modules: List<String>): Task<Void> = ControllableTask()

  override fun deferredLanguageInstall(languages: List<Locale>): Task<Void> = ControllableTask()

  override fun deferredLanguageUninstall(languages: List<Locale>): Task<Void> = ControllableTask()
}

/**
 * A [Task] the test completes explicitly via [complete]/[fail]; listeners run synchronously on the
 * calling thread (no Looper), which is what makes it usable in JVM unit tests.
 */
internal class ControllableTask<T> : Task<T>() {

  private val successListeners = mutableListOf<OnSuccessListener<in T>>()
  private val failureListeners = mutableListOf<OnFailureListener>()
  private var result: T? = null
  private var exception: Exception? = null
  private var completed = false

  fun complete(value: T) {
    completed = true
    result = value
    successListeners.forEach { it.onSuccess(value) }
  }

  fun fail(error: Exception) {
    completed = true
    exception = error
    failureListeners.forEach { it.onFailure(error) }
  }

  override fun addOnSuccessListener(listener: OnSuccessListener<in T>): Task<T> {
    if (completed && exception == null) {
      @Suppress("UNCHECKED_CAST") listener.onSuccess(result as T)
    } else {
      successListeners += listener
    }
    return this
  }

  override fun addOnSuccessListener(
    activity: Activity,
    listener: OnSuccessListener<in T>,
  ): Task<T> = addOnSuccessListener(listener)

  override fun addOnSuccessListener(
    executor: Executor,
    listener: OnSuccessListener<in T>,
  ): Task<T> = addOnSuccessListener(listener)

  override fun addOnFailureListener(listener: OnFailureListener): Task<T> {
    val error = exception
    if (error != null) listener.onFailure(error) else failureListeners += listener
    return this
  }

  override fun addOnFailureListener(activity: Activity, listener: OnFailureListener): Task<T> =
    addOnFailureListener(listener)

  override fun addOnFailureListener(executor: Executor, listener: OnFailureListener): Task<T> =
    addOnFailureListener(listener)

  override fun getException(): Exception? = exception

  override fun getResult(): T? = result

  override fun <X : Throwable> getResult(exceptionType: Class<X>): T? = result

  override fun isCanceled(): Boolean = false

  override fun isComplete(): Boolean = completed

  override fun isSuccessful(): Boolean = completed && exception == null
}

/** Never launched in these tests — the fake manager records the request instead. */
internal class RecordingLauncher : ActivityResultLauncher<IntentSenderRequest>() {

  override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) = Unit

  override fun unregister() = Unit

  override val contract: ActivityResultContract<IntentSenderRequest, *>
    get() = ActivityResultContracts.StartIntentSenderForResult()
}
