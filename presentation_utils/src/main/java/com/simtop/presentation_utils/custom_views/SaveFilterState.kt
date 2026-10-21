package com.simtop.presentation_utils.custom_views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simtop.presentation_utils.R
import kotlinx.coroutines.launch

/**
 * Disclosure and draft survive restoration; an interrupted submission is never restored as busy.
 */
@Stable
class SaveFilterState(initiallyExpanded: Boolean = false, initialName: String = "") {
  var expanded by mutableStateOf(initiallyExpanded)
    private set

  var name by mutableStateOf(initialName)
  var saving by mutableStateOf(false)
    private set

  fun toggle() {
    if (!saving) expanded = !expanded
  }

  fun close() {
    expanded = false
  }

  suspend fun save(onSave: suspend (String) -> Boolean) {
    if (saving || name.isBlank()) return
    saving = true
    try {
      if (onSave(name)) {
        name = ""
        close()
      }
    } finally {
      saving = false
    }
  }

  companion object {
    val Saver =
      listSaver<SaveFilterState, Any>(
        save = { listOf(it.expanded, it.name) },
        restore = { SaveFilterState(it[0] as Boolean, it[1] as String) },
      )
  }
}

@Composable
fun rememberSaveFilterState(initiallyExpanded: Boolean = false): SaveFilterState =
  rememberSaveable(saver = SaveFilterState.Saver) { SaveFilterState(initiallyExpanded) }

@Composable
fun ComposeSaveFilterAction(
  state: SaveFilterState,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  maxWidth: Dp = 144.dp,
) {
  TextButton(
    onClick = state::toggle,
    enabled = (enabled || state.expanded) && !state.saving,
    modifier = modifier.widthIn(max = maxWidth).testTag("filter_preset_toggle"),
  ) {
    Text(stringResource(R.string.save_filter))
  }
}

/** Hosts supply the current query through their callback; no query is reconstructed here. */
@Composable
fun ComposeSaveFilterForm(
  state: SaveFilterState,
  onSave: suspend (String) -> Boolean,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val scope = rememberCoroutineScope()
  val focus = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  fun dismiss() {
    state.close()
    focus.clearFocus()
    keyboard?.hide()
  }
  BackHandler(enabled = state.expanded) { dismiss() }
  if (state.expanded) {
    ComposeFilterPresetForm(
      state = state,
      onSave = {
        if (enabled) {
          focus.clearFocus()
          keyboard?.hide()
          scope.launch { state.save(onSave) }
        }
      },
      enabled = enabled && !state.saving,
      modifier = modifier,
    )
  }
}

/** Leave translated action labels room to grow without squeezing the title or search editor. */
@Composable
fun saveFilterActionNeedsOwnRow(): Boolean =
  LocalDensity.current.fontScale > ACTION_STACK_FONT_SCALE

private const val ACTION_STACK_FONT_SCALE = 1.2f
