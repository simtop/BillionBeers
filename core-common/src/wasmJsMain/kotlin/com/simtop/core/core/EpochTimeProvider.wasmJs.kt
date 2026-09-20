@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.simtop.core.core

import kotlin.JsFun

@JsFun("() => Date.now()")
private external fun dateNow(): Double

internal actual fun currentEpochMillis(): Long = dateNow().toLong()
