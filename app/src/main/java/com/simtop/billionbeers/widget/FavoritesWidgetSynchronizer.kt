package com.simtop.billionbeers.widget

import com.simtop.beerdomain.domain.repositories.BeersRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

internal class FavoritesWidgetSynchronizer(
  private val repository: BeersRepository,
  private val updateWidget: suspend () -> Unit,
  private val onUpdateFailure: (Throwable) -> Unit,
) {
  @Suppress("TooGenericExceptionCaught")
  suspend fun run() {
    repository.observeFavoritesWidgetItems().collect {
      try {
        updateWidget()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        onUpdateFailure(exception)
      }
    }
  }
}
