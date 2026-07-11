package com.simtop.billionbeers.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.di.BaseAppGraph
import com.simtop.presentation_utils.core.LocalDebugDrawerToggle
import kotlinx.coroutines.launch

/**
 * Debug-build-only wrapper: a floating trigger opens a drawer with network fault injection, theme
 * override, feature flag overrides, and a deep link directory. Release builds get the no-op twin in
 * app/src/release - same signature.
 *
 * The trigger is hidden by default and shown by long-pressing a screen's title (wired via
 * [LocalDebugDrawerToggle]), so it doesn't float over every screen all the time.
 */
@Composable
fun DebugDrawerHost(appGraph: BaseAppGraph, content: @Composable () -> Unit) {
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  var isFabVisible by remember { mutableStateOf(false) }

  CompositionLocalProvider(LocalDebugDrawerToggle provides { isFabVisible = !isFabVisible }) {
    ModalNavigationDrawer(
      drawerState = drawerState,
      drawerContent = { ModalDrawerSheet { DebugDrawerContent(appGraph = appGraph) } },
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (isFabVisible) {
          FloatingActionButton(
            onClick = { scope.launch { drawerState.open() } },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
          ) {
            Icon(imageVector = Icons.Default.Build, contentDescription = "Open debug drawer")
          }
        }
      }
    }
  }
}
