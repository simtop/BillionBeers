package com.simtop.billionbeers.t71

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class T71ViewModel : ViewModel() {
  private val _state = MutableStateFlow(T71UiState())
  val state: StateFlow<T71UiState> = _state.asStateFlow()

  private val _events = Channel<T71Event>(capacity = Channel.BUFFERED)
  val events = _events.receiveAsFlow()

  fun updateText(value: String) {
    _state.value = _state.value.copy(text = value)
  }

  fun submit() {
    _state.value = _state.value.copy(submissionCount = _state.value.submissionCount + 1)
    viewModelScope.launch {
      _events.send(T71Event.Submitted)
    }
  }

  fun dispose() {
    viewModelScope.cancel()
    _events.close()
  }

  override fun onCleared() {
    dispose()
    super.onCleared()
  }
}

data class T71UiState(
  val text: String = "",
  val submissionCount: Int = 0,
)

sealed interface T71Event {
  data object Submitted : T71Event
}
