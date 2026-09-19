package com.simtop.billionbeers.iosshared

import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class IosDataRuntimeTest {

  @Test
  fun graphResourcesAreSingletonsPerRuntimeGraph() {
    val graph = createGraphFactory<IosGraph.Factory>().create(testConfig())

    assertSame(graph.httpClient, graph.httpClient)
    assertSame(graph.database, graph.database)

    graph.httpClient.close()
    graph.database.close()
  }

  @Test
  fun closeBeforeUseIsSafeAndIdempotent() {
    val runtime = IosDataRuntime.open(testConfig())

    runtime.close()
    runtime.close()
  }

  @Test
  fun closeAfterResolvingConsumersIsSafeAndIdempotent() {
    val runtime = IosDataRuntime.open(testConfig())

    assertNotNull(runtime.repository)
    assertNotNull(runtime.pagerFactory)
    runtime.close()
    runtime.close()
  }

  private fun testConfig(): IosDataConfig =
    IosDataConfig(apiBaseUrl = "https://example.test/", languageCode = "en")
}
