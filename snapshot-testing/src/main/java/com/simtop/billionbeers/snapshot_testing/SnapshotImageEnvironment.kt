package com.simtop.billionbeers.snapshot_testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler

/**
 * Pins every Coil `AsyncImage` inside a screenshot to its loading state.
 *
 * Left alone, `AsyncImage` runs the real request pipeline. Paparazzi has no network, so the request
 * eventually fails and swaps in the error drawable - but whether it gets that far before the single
 * frame is captured is a race, so the same preview flip-flopped between the loading skeleton and
 * the error drawable from one CI run to the next.
 *
 * Coil only consults [LocalAsyncImagePreviewHandler] while [LocalInspectionMode] is true, and it
 * resolves the handler undispatched, so a handler that never suspends settles the state during
 * composition instead of racing the capture. Loading is the state these screenshots are meant to
 * document: with no image to fetch, the skeleton is what the UI is designed to show.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun SnapshotImageEnvironment(content: @Composable () -> Unit) {
  CompositionLocalProvider(
    LocalInspectionMode provides true,
    LocalAsyncImagePreviewHandler provides LoadingPreviewHandler,
    content = content,
  )
}

@OptIn(ExperimentalCoilApi::class)
private val LoadingPreviewHandler = AsyncImagePreviewHandler { _, _ ->
  AsyncImagePainter.State.Loading(painter = null)
}
