package com.simtop.core.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface CoroutineDispatcherProvider {

  val main: CoroutineDispatcher
    get() = Dispatchers.Main

  val default: CoroutineDispatcher
    get() = Dispatchers.Default

  val io: CoroutineDispatcher
    get() = Dispatchers.IO

  val unconfined: CoroutineDispatcher
    get() = Dispatchers.Unconfined
}

class DefaultCoroutineDispatcherProvider : CoroutineDispatcherProvider
