package com.simtop.core.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal expect fun defaultIoDispatcher(): CoroutineDispatcher

interface CoroutineDispatcherProvider {

  val main: CoroutineDispatcher
    get() = Dispatchers.Main

  val default: CoroutineDispatcher
    get() = Dispatchers.Default

  val io: CoroutineDispatcher
    get() = defaultIoDispatcher()

  val unconfined: CoroutineDispatcher
    get() = Dispatchers.Unconfined
}

class DefaultCoroutineDispatcherProvider : CoroutineDispatcherProvider
