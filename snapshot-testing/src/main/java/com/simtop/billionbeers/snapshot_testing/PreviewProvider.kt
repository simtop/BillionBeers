package com.simtop.billionbeers.snapshot_testing

import androidx.compose.runtime.Composable

interface PreviewProvider {
    val snapshots: List<Snapshot>
}

data class Snapshot(
    val name: String,
    val content: @Composable () -> Unit
)
