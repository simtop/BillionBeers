package com.simtop.billionbeers.iosshared

import com.simtop.navigation.contract.PortableRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal class IosRouteRequestBuffer {
  private val channel = Channel<PortableRoute>(Channel.BUFFERED)

  val routes: Flow<PortableRoute> = channel.receiveAsFlow()

  fun trySend(route: PortableRoute): Boolean = channel.trySend(route).isSuccess

  fun close() {
    channel.close()
  }
}
