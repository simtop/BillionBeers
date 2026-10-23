package com.simtop.billionbeers.presentation

import com.simtop.presentation_utils.core.IntroductionId
import com.simtop.presentation_utils.core.OnboardingHints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OnboardingState(
  val completedIds: Set<String> = emptySet(),
  val storageAvailable: Boolean = true,
) {
  fun isComplete(id: IntroductionId): Boolean = !storageAvailable || id.value in completedIds
}

interface OnboardingStore {
  suspend fun read(): Set<String>

  suspend fun write(completedIds: Set<String>)
}

/** App-scoped presentation state. Disk failures must never prevent catalog navigation. */
class OnboardingController(private val store: OnboardingStore, private val scope: CoroutineScope) :
  OnboardingHints {
  private val mutableState = MutableStateFlow<OnboardingState?>(null)
  val state = mutableState.asStateFlow()
  private val storageMutex = Mutex()
  private var revision = 0

  init {
    val loadRevision = revision
    scope.launch {
      storageMutex.withLock {
        val loaded =
          recoverStorageFailure(OnboardingState(storageAvailable = false)) {
            OnboardingState(store.read().toSet())
          }
        if (loadRevision == revision) mutableState.value = loaded
      }
    }
  }

  /** Completes a full-screen introduction after its action. Call on the main thread. */
  fun complete(id: IntroductionId) {
    val current = mutableState.value ?: return
    if (!current.isComplete(id))
      update(current.copy(completedIds = current.completedIds + id.value))
  }

  override fun claim(id: IntroductionId): Boolean {
    val current = mutableState.value
    if (current == null || current.isComplete(id)) return false
    complete(id)
    return true
  }

  /** Called only from the debug drawer and test setup, never by a release UI action. */
  fun reset() = update(OnboardingState())

  private fun update(state: OnboardingState) {
    revision++
    mutableState.value = state
    scope.launch {
      storageMutex.withLock {
        // Write the newest snapshot even when several UI actions arrive before the disk is ready.
        recoverStorageFailure(Unit) { store.write(checkNotNull(mutableState.value).completedIds) }
      }
    }
  }

  private suspend fun <T> recoverStorageFailure(fallback: T, action: suspend () -> T): T =
    try {
      action()
    } catch (exception: CancellationException) {
      throw exception
    } catch (_: java.io.IOException) {
      fallback
    } catch (_: IllegalStateException) {
      fallback
    } catch (_: ClassCastException) {
      fallback
    } catch (_: SecurityException) {
      fallback
    }
}
