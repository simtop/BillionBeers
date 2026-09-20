package com.simtop.core.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun defaultIoDispatcher(): CoroutineDispatcher = Dispatchers.Default
