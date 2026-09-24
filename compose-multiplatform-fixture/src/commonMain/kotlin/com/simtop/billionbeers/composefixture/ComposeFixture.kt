package com.simtop.billionbeers.composefixture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import billionbeers.compose_multiplatform_fixture.generated.resources.Res
import billionbeers.compose_multiplatform_fixture.generated.resources.compose_fixture_input_label
import billionbeers.compose_multiplatform_fixture.generated.resources.compose_fixture_submit
import billionbeers.compose_multiplatform_fixture.generated.resources.compose_fixture_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun ComposeFixtureScreen(
  viewModel: ComposeFixtureViewModel,
  modifier: Modifier = Modifier,
  onEvent: (ComposeFixtureEvent) -> Unit = {},
  title: String? = null,
  inputLabel: String? = null,
  submitLabel: String? = null,
) {
  val state by viewModel.state.collectAsState()
  LaunchedEffect(viewModel) {
    viewModel.events.collect(onEvent)
  }
  ComposeFixtureContent(
    state = state,
    onTextChanged = viewModel::updateText,
    onSubmit = viewModel::submit,
    title = title ?: stringResource(Res.string.compose_fixture_title),
    inputLabel = inputLabel ?: stringResource(Res.string.compose_fixture_input_label),
    submitLabel = submitLabel ?: stringResource(Res.string.compose_fixture_submit),
    modifier = modifier,
  )
}

@Composable
fun ComposeFixtureContent(
  state: ComposeFixtureUiState,
  onTextChanged: (String) -> Unit,
  onSubmit: () -> Unit,
  title: String,
  inputLabel: String,
  submitLabel: String,
  modifier: Modifier = Modifier,
  requestInitialFocus: Boolean = true,
) {
  val focusRequester = remember { FocusRequester() }
  if (requestInitialFocus) {
    LaunchedEffect(Unit) {
      focusRequester.requestFocus()
    }
  }

  Card(modifier = modifier) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
      )
      OutlinedTextField(
        value = state.text,
        onValueChange = onTextChanged,
        modifier =
          Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("compose-fixture-input"),
        label = { Text(inputLabel) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        singleLine = true,
      )
      Button(onClick = onSubmit) {
        Text(submitLabel)
      }
      Text("Submissions: ${state.submissionCount}")
    }
  }
}
