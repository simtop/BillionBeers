package com.simtop.billionbeers.debug

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.BillionBeersApplication
import com.simtop.billionbeers.di.ApiEnvironment
import com.simtop.billionbeers.di.ApiEnvironmentController
import com.simtop.billionbeers.di.BaseAppGraph
import com.simtop.core.core.FeatureFlag
import com.simtop.core.core.NetworkFaultMode
import com.simtop.core.core.ThemeMode
import com.simtop.navigation.DeepLinkParser

private data class DeepLinkEntry(val label: String, val uri: String)

@Composable
fun DebugDrawerContent(appGraph: BaseAppGraph, modifier: Modifier = Modifier) {
  val context = LocalContext.current

  Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text(text = "Debug Drawer", style = MaterialTheme.typography.titleLarge)

    Spacer(modifier = Modifier.padding(top = 12.dp))
    SectionTitle("API environment")
    val selectedEnvironment by
      appGraph.apiEnvironmentController.selectedEnvironment.collectAsState()
    ApiEnvironment.entries.forEach { environment ->
      RadioRow(
        label = "${environment.displayName} — ${environment.apiBaseUrl}",
        selected = selectedEnvironment == environment,
        onClick = {
          applyApiEnvironment(
            context = context,
            controller = appGraph.apiEnvironmentController,
            environment = environment,
          )
        },
      )
    }

    Spacer(modifier = Modifier.padding(top = 12.dp))
    SectionTitle("Network fault injection")
    val faultMode by appGraph.networkFaultController.mode.collectAsState()
    NetworkFaultMode.entries.forEach { mode ->
      RadioRow(
        label = mode.name,
        selected = faultMode == mode,
        onClick = { appGraph.networkFaultController.setMode(mode) },
      )
    }

    Spacer(modifier = Modifier.padding(top = 12.dp))
    SectionTitle("Theme")
    val themeMode by appGraph.themeController.mode.collectAsState()
    ThemeMode.entries.forEach { mode ->
      RadioRow(
        label = mode.name,
        selected = themeMode == mode,
        onClick = { appGraph.themeController.setMode(mode) },
      )
    }

    Spacer(modifier = Modifier.padding(top = 12.dp))
    SectionTitle("Feature flags")
    val overrides by appGraph.featureFlagProvider.overrides.collectAsState()
    FeatureFlag.entries.forEach { flag ->
      SwitchRow(
        label = flag.displayName,
        checked = overrides[flag] ?: flag.defaultValue,
        onCheckedChange = { enabled -> appGraph.featureFlagProvider.setOverride(flag, enabled) },
      )
    }

    Spacer(modifier = Modifier.padding(top = 12.dp))
    SectionTitle("Deep link directory")
    var sampleBeerId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
      sampleBeerId = appGraph.beersRepository.getAllBeersFromDB().firstOrNull()?.id
    }
    val entries = buildList {
      add(DeepLinkEntry("Beers list", "${DeepLinkParser.SCHEME}://${DeepLinkParser.HOST_BEERS}"))
      sampleBeerId?.let { id ->
        add(
          DeepLinkEntry(
            "Beer detail (id=$id)",
            "${DeepLinkParser.SCHEME}://${DeepLinkParser.HOST_BEERS}/$id",
          )
        )
      }
    }
    entries.forEach { entry ->
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.uri))) }
            .padding(vertical = 8.dp)
      ) {
        Text(text = entry.label, style = MaterialTheme.typography.bodyLarge)
        Text(
          text = entry.uri,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      HorizontalDivider()
    }
  }
}

private fun applyApiEnvironment(
  context: Context,
  controller: ApiEnvironmentController,
  environment: ApiEnvironment,
) {
  if (controller.selectedEnvironment.value == environment) return

  val activity = context.findActivity() ?: return
  controller.selectEnvironment(environment)
  (activity.application as BillionBeersApplication).rebuildAppGraph()
  activity.startActivity(Intent.makeRestartActivityTask(activity.componentName))
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

@Composable
private fun SectionTitle(text: String) {
  Text(text = text, style = MaterialTheme.typography.titleMedium)
}

/**
 * The [RadioRow] reasoning, for a [Switch] whose label was likewise a sibling rather than merged.
 */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = null)
  }
}

/**
 * `onClick = null` on the [RadioButton] is load-bearing, not a tidy-up. Given its own handler it
 * becomes a separately focusable target carrying no label of its own - the row holds the [Text] -
 * so a screen reader announces an unlabelled control next to the one it should read. Passing null
 * leaves the button as decoration and lets the row's `selectable` own both the click and the merged
 * semantics, which is what TalkBack should land on. `role` makes it announce as a radio rather than
 * a generic button.
 */
@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Text(text = label)
  }
}
