package com.simtop.billionbeers.composefixture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ComposeFixtureViewModel : ViewModel() {
  private val _state = MutableStateFlow(ComposeFixtureUiState())
  val state: StateFlow<ComposeFixtureUiState> = _state.asStateFlow()

  private val _events = Channel<ComposeFixtureEvent>(capacity = Channel.BUFFERED)
  val events = _events.receiveAsFlow()

  fun updateText(value: String) {
    _state.value = _state.value.copy(text = value)
  }

  fun submit() {
    _state.value = _state.value.copy(submissionCount = _state.value.submissionCount + 1)
    viewModelScope.launch {
      _events.send(ComposeFixtureEvent.Submitted)
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

data class ComposeFixtureUiState(
  val text: String = "",
  val submissionCount: Int = 0,
)

sealed interface ComposeFixtureEvent {
  data object Submitted : ComposeFixtureEvent
}
