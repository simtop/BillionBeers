@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.simtop.beer_storage.browser

import com.simtop.beer_storage.api.StoredBeer
import kotlin.JsFun
import kotlin.js.JsString
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@JsFun("""
() => {
  const global = globalThis;
  const store = global.IDBObjectStore.prototype;
  const originals = {
    open: global.indexedDB.open,
    getAll: store.getAll,
    get: store.get,
    put: store.put,
    clear: store.clear,
    count: store.count,
    parse: global.JSON.parse,
    stringify: global.JSON.stringify,
  };
  const probe = {};
  const reset = () => {
    probe.opens = 0;
    probe.getAll = 0;
    probe.get = 0;
    probe.put = 0;
    probe.clear = 0;
    probe.count = 0;
    probe.parseCalls = 0;
    probe.parseChars = 0;
    probe.stringifyCalls = 0;
    probe.stringifyChars = 0;
  };
  const restore = () => {
    global.indexedDB.open = originals.open;
    store.getAll = originals.getAll;
    store.get = originals.get;
    store.put = originals.put;
    store.clear = originals.clear;
    store.count = originals.count;
    global.JSON.parse = originals.parse;
    global.JSON.stringify = originals.stringify;
    delete global.__billionBeersStorageProbe;
  };
  global.indexedDB.open = function (...args) {
    probe.opens += 1;
    return originals.open.apply(this, args);
  };
  store.getAll = function (...args) {
    probe.getAll += 1;
    return originals.getAll.apply(this, args);
  };
  store.get = function (...args) {
    probe.get += 1;
    return originals.get.apply(this, args);
  };
  store.put = function (...args) {
    probe.put += 1;
    return originals.put.apply(this, args);
  };
  store.clear = function (...args) {
    probe.clear += 1;
    return originals.clear.apply(this, args);
  };
  store.count = function (...args) {
    probe.count += 1;
    return originals.count.apply(this, args);
  };
  global.JSON.parse = function (value, ...args) {
    probe.parseCalls += 1;
    probe.parseChars += typeof value === 'string' ? value.length : 0;
    return originals.parse.call(this, value, ...args);
  };
  global.JSON.stringify = function (value, ...args) {
    probe.stringifyCalls += 1;
    const result = originals.stringify.call(this, value, ...args);
    probe.stringifyChars += typeof result === 'string' ? result.length : 0;
    return result;
  };
  global.__billionBeersStorageProbe = {probe, reset, restore, originals};
  reset();
}
""")
private external fun installStorageProbe()

@JsFun("""
() => {
  const state = globalThis.__billionBeersStorageProbe;
  if (state) state.reset();
}
""")
private external fun resetStorageProbe()

@JsFun("""
() => {
  const state = globalThis.__billionBeersStorageProbe;
  if (!state) return '{}';
  const result = state.originals.stringify.call(JSON, state.probe);
  state.restore();
  return result;
}
""")
private external fun finishStorageProbe(): JsString

class IndexedDbBeersStorageBrowserProbeTest {

  @Test
  fun browserStorageWorkloadReportsOperationPhasesForOneRow() = runTest {
    runWorkload(1)
  }

  @Test
  fun browserStorageWorkloadReportsOperationPhasesForThirtyTwoRows() = runTest {
    runWorkload(32)
  }

  @Test
  fun browserStorageWorkloadReportsOperationPhasesForOneHundredTwentyEightRows() = runTest {
    runWorkload(128)
  }

  @Test
  fun browserStorageWorkloadReportsOperationPhasesForOneThousandRows() = runTest {
    runWorkload(1_000)
  }

  private suspend fun runWorkload(size: Int) {
    val databaseName = "billionbeers-probe-${hashCode()}-$size"
    val storage = IndexedDbBeersStorage(databaseName)
    val catalog = (0 until size).map(::probeBeer)
    var probeInstalled = false
    try {
      storage.insertAll(catalog)
      installStorageProbe()
      probeInstalled = true

      val mutationSamples = mutableListOf<Double>()
      val consumerSamples = mutableListOf<Double>()
      storage.insertAll(listOf(catalog.first().copy(name = "Probe warmup")))
      resetStorageProbe()
      repeat(SAMPLE_COUNT) { sample ->
        mutationSamples += measureMillis {
          storage.insertAll(listOf(catalog.first().copy(name = "Probe update $sample")))
        }
        consumerSamples += measureMillis {
          val rows = storage.observeBeers().first { it.size == size }
          checksum(rows)
        }
      }
      val singleMutationMetrics = finishStorageProbe().toString()
      probeInstalled = false

      installStorageProbe()
      probeInstalled = true
      val burstTime = measureMillis {
        repeat(BURST_COUNT) { sample ->
          storage.insertAll(listOf(catalog.first().copy(name = "Probe burst $sample")))
        }
      }
      val burstConsumerTime = measureMillis {
        checksum(storage.observeBeers().first { it.size == size })
      }
      val burstMetrics = finishStorageProbe().toString()
      probeInstalled = false

      println(
        "browser-storage-probe rows=$size samples=$SAMPLE_COUNT " +
          "mutationMedianMs=${median(mutationSamples)} consumerMedianMs=${median(consumerSamples)} " +
          "singleCounters=$singleMutationMetrics " +
          "burstCount=$BURST_COUNT burstTimeMs=$burstTime burstConsumerMs=$burstConsumerTime " +
          "burstCounters=$burstMetrics"
      )
    } finally {
      if (probeInstalled) finishStorageProbe()
      storage.deleteAll()
      storage.close()
    }
  }

  private fun checksum(rows: List<StoredBeer>) {
    var checksum = 0
    repeat(CONSUMER_REPETITIONS) {
      checksum += rows.sumOf { it.name.length + it.description.length }
    }
    check(checksum > 0)
  }

  private fun probeBeer(index: Int): StoredBeer =
    StoredBeer(
      id = "probe-$index",
      name = "Probe Beer $index",
      tagline = "Measurement fixture $index",
      description = "A deterministic browser storage measurement fixture with enough text to exercise serialization.",
      imageUrl = "https://example.test/probe-$index.png",
      abv = 4.5,
      ibu = 20.0,
      foodPairing = listOf("pretzels", "pizza", "salad"),
      availability = index % 2 == 0,
      isFavorite = index % 5 == 0,
    )

  private suspend fun measureMillis(block: suspend () -> Unit): Double {
    val start = TimeSource.Monotonic.markNow()
    block()
    return start.elapsedNow().inWholeNanoseconds / 1_000_000.0
  }

  private fun median(values: List<Double>): Double = values.sorted()[values.size / 2]

  private companion object {
    val CATALOG_SIZES = listOf(1, 32, 128, 1_000)
    const val SAMPLE_COUNT = 5
    const val BURST_COUNT = 5
    const val CONSUMER_REPETITIONS = 100
  }
}
