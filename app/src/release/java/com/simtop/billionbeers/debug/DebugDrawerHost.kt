package com.simtop.billionbeers.debug

import androidx.compose.runtime.Composable
import com.simtop.billionbeers.di.BaseAppGraph

/**
 * Release twin of the debug-build DebugDrawerHost (app/src/debug) - same signature, no drawer.
 * MainActivity calls this uniformly across build types.
 */
@Composable
fun DebugDrawerHost(appGraph: BaseAppGraph, content: @Composable () -> Unit) {
  content()
}
