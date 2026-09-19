@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.simtop.beer_storage.browser

import com.simtop.beer_storage.api.BeersStorage
import com.simtop.beer_storage.api.StoredBeer
import com.simtop.beer_storage.api.StoredPagingState
import kotlin.JsFun
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@JsFun("""
(name, version, operation, payload) => new Promise((resolve, reject) => {
  const request = indexedDB.open(name, version);
  request.onupgradeneeded = () => {
    const db = request.result;
    if (!db.objectStoreNames.contains('beers')) db.createObjectStore('beers', {keyPath: 'id'});
    if (!db.objectStoreNames.contains('paging_state')) db.createObjectStore('paging_state', {keyPath: 'surface'});
  };
  request.onblocked = () => reject(new Error('IndexedDB open blocked'));
  request.onerror = () => reject(request.error || new Error('IndexedDB open failed'));
  request.onsuccess = () => {
    const db = request.result;
    db.onversionchange = () => db.close();
    const readOnly = ['readBeers', 'readFavorites', 'readPaging', 'countPaging', 'count'].includes(operation);
    const stores = operation === 'readPaging' || operation === 'countPaging' ? ['paging_state'] :
      operation === 'count' || operation === 'readBeers' || operation === 'readFavorites' ? ['beers'] :
      operation === 'deleteAll' ? ['beers', 'paging_state'] : ['beers', 'paging_state'];
    const tx = db.transaction(stores, readOnly ? 'readonly' : 'readwrite');
    const beers = stores.includes('beers') ? tx.objectStore('beers') : null;
    const paging = stores.includes('paging_state') ? tx.objectStore('paging_state') : null;
    let result = 'null';
    let settled = false;
    const close = () => { try { db.close(); } catch (_) {} };
    const finishRead = (value) => { result = JSON.stringify(value); };
    const fail = (error) => {
      if (settled) return;
      settled = true;
      try { tx.abort(); } catch (_) {}
      close();
      reject(error || new Error('IndexedDB operation failed'));
    };
    tx.onerror = () => fail(tx.error || new Error('IndexedDB transaction failed'));
    tx.onabort = () => fail(tx.error || new Error('IndexedDB transaction aborted'));
    tx.oncomplete = () => {
      if (settled) return;
      settled = true;
      close();
      resolve(result);
    };
    try {
      if (operation === 'readBeers' || operation === 'readFavorites') {
        const request = beers.getAll();
        request.onsuccess = () => {
          const rows = operation === 'readFavorites' ? request.result.filter(row => row.isFavorite) : request.result;
          rows.sort((a, b) => a.name.localeCompare(b.name) || a.id.localeCompare(b.id));
          finishRead(rows);
        };
        request.onerror = () => fail(request.error);
      } else if (operation === 'readPaging') {
        const request = paging.get(payload);
        request.onsuccess = () => finishRead(request.result || null);
        request.onerror = () => fail(request.error);
      } else if (operation === 'countPaging') {
        const request = paging.count();
        request.onsuccess = () => finishRead(request.result);
        request.onerror = () => fail(request.error);
      } else if (operation === 'count') {
        const request = beers.count();
        request.onsuccess = () => finishRead(request.result);
        request.onerror = () => fail(request.error);
      } else if (operation === 'deleteAll') {
        beers.clear();
        paging.clear();
        result = 'null';
      } else if (operation === 'insertAll') {
        const rows = JSON.parse(payload);
        const existingRequest = beers.getAll();
        existingRequest.onsuccess = () => {
          const existingById = Object.fromEntries(existingRequest.result.map(row => [row.id, row]));
          rows.forEach(row => {
            const existing = existingById[row.id] || {};
            const merged = {
              ...existing,
              ...row,
              availability: existing.id == null ? row.availability : existing.availability,
              isFavorite: existing.id == null ? row.isFavorite : existing.isFavorite,
            };
            beers.put(merged);
            existingById[row.id] = merged;
          });
        };
        existingRequest.onerror = () => fail(existingRequest.error);
      } else if (operation === 'upsertAvailability' || operation === 'upsertFavorite') {
        const incoming = JSON.parse(payload);
        const existingRequest = beers.get(incoming.id);
        existingRequest.onsuccess = () => {
          const existing = existingRequest.result;
          if (existing) {
            beers.put({
              ...existing,
              ...(operation === 'upsertAvailability'
                ? {availability: incoming.availability}
                : {isFavorite: incoming.isFavorite}),
            });
          } else {
            beers.put(incoming);
          }
        };
        existingRequest.onerror = () => fail(existingRequest.error);
      } else if (operation === 'insertPage') {
        const input = JSON.parse(payload);
        const pagingRequest = paging.get(input.surface);
        pagingRequest.onsuccess = () => {
          const previous = pagingRequest.result || {};
          const existingTotal = previous.totalCount ?? null;
          const incomingNext = input.nextKey;
          const previousNext = previous.nextKey;
          const nextKey = previousNext == null || (incomingNext != null && incomingNext > previousNext) ? incomingNext : previousNext;
          const beerRequests = input.beers.map(row => beers.get(row.id));
          let remaining = beerRequests.length;
          const existingRows = {};
          if (remaining === 0) {
            input.beers.forEach(row => beers.put(row));
            paging.put({surface: input.surface, nextKey, totalCount: input.totalCount ?? existingTotal, refreshedAt: Date.now()});
          } else {
            beerRequests.forEach((request, index) => {
              request.onsuccess = () => {
                existingRows[input.beers[index].id] = request.result || {};
                remaining -= 1;
                if (remaining === 0) {
                  input.beers.forEach(row => {
                    const existing = existingRows[row.id] || {};
                    beers.put({
                      ...existing,
                      ...row,
                      availability: existing.id == null ? row.availability : existing.availability,
                      isFavorite: existing.id == null ? row.isFavorite : existing.isFavorite,
                    });
                  });
                  paging.put({surface: input.surface, nextKey, totalCount: input.totalCount ?? existingTotal, refreshedAt: Date.now()});
                }
              };
              request.onerror = () => fail(request.error);
            });
          }
        };
        pagingRequest.onerror = () => fail(pagingRequest.error);
      }
    } catch (error) { fail(error); }
  };
})
""")
private external fun executeIndexedDb(
  databaseName: String,
  version: Int,
  operation: String,
  payload: String,
): Promise<JsString>

@JsFun("""
(name, callback) => {
  const listeners = globalThis.__billionBeersStorageListeners || (globalThis.__billionBeersStorageListeners = new Map());
  const id = (globalThis.__billionBeersStorageListenerId || 0) + 1;
  globalThis.__billionBeersStorageListenerId = id;
  const channelName = 'billionbeers-storage:' + name;
  let channel = null;
  let onMessage = null;
  let onStorage = null;
  let onVisibility = null;
  let onPageShow = null;
  const notify = () => callback();
  if (typeof BroadcastChannel !== 'undefined') {
    try {
      channel = new BroadcastChannel(channelName);
      onMessage = () => notify();
      channel.addEventListener('message', onMessage);
    } catch (_) {
      channel = null;
    }
  }
  if (!channel && typeof window !== 'undefined') {
    onStorage = event => {
      if (event.key === channelName) notify();
    };
    window.addEventListener('storage', onStorage);
  }
  if (typeof document !== 'undefined') {
    onVisibility = () => {
      if (document.visibilityState === 'visible') notify();
    };
    document.addEventListener('visibilitychange', onVisibility);
  }
  if (typeof window !== 'undefined') {
    onPageShow = () => notify();
    window.addEventListener('pageshow', onPageShow);
  }
  listeners.set(id, {channel, onMessage, onStorage, onVisibility, onPageShow, channelName});
  return id;
}
""")
private external fun registerInvalidationListener(databaseName: String, callback: () -> Unit): Int

@JsFun("""
(name) => {
  const channelName = 'billionbeers-storage:' + name;
  let published = false;
  if (typeof BroadcastChannel !== 'undefined') {
    try {
      const channel = new BroadcastChannel(channelName);
      channel.postMessage('committed');
      channel.close();
      published = true;
    } catch (_) {}
  }
  if (!published && typeof localStorage !== 'undefined') {
    localStorage.setItem(channelName, String(Date.now()));
    localStorage.removeItem(channelName);
  }
}
""")
private external fun publishInvalidation(databaseName: String)

@JsFun("""
(id) => {
  const listeners = globalThis.__billionBeersStorageListeners;
  const entry = listeners && listeners.get(id);
  if (!entry) return;
  if (entry.channel) {
    entry.channel.removeEventListener('message', entry.onMessage);
    entry.channel.close();
  }
  if (typeof window !== 'undefined' && entry.onStorage) {
    window.removeEventListener('storage', entry.onStorage);
  }
  if (typeof document !== 'undefined' && entry.onVisibility) {
    document.removeEventListener('visibilitychange', entry.onVisibility);
  }
  if (typeof window !== 'undefined' && entry.onPageShow) {
    window.removeEventListener('pageshow', entry.onPageShow);
  }
  listeners.delete(id);
}
""")
private external fun unregisterInvalidationListener(listenerId: Int)

@Serializable
private data class BeerRecord(
  val id: String,
  val name: String,
  val tagline: String,
  val description: String,
  val imageUrl: String,
  val abv: Double,
  val ibu: Double,
  val foodPairing: List<String>,
  val availability: Boolean,
  val isFavorite: Boolean,
  val styleName: String,
  val breweryName: String,
  val srm: Int?,
  val releasedYear: Int?,
  val minServingTemperature: Int?,
  val maxServingTemperature: Int?,
  val fermentationMethod: String,
  val ingredients: List<String>,
  val recommendedGlasses: List<String>,
)

@Serializable
private data class PagingRecord(
  val surface: String,
  val nextKey: Int?,
  val totalCount: Int?,
  val refreshedAt: Long,
)

private val json = Json { ignoreUnknownKeys = true }
private val liveStorages = mutableMapOf<String, MutableSet<IndexedDbBeersStorage>>()

class IndexedDbBeersStorage(
  private val databaseName: String = DEFAULT_DATABASE_NAME,
) : BeersStorage {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val writeMutex = Mutex()
  private val refreshMutex = Mutex()
  private val beersState = MutableStateFlow<List<StoredBeer>>(emptyList())
  private val favoritesState = MutableStateFlow<List<StoredBeer>>(emptyList())
  private val initialized = CompletableDeferred<Unit>()
  private var closed = false
  private val listenerId = registerInvalidationListener(databaseName) {
    scope.launch {
      initialized.await()
      refresh()
    }
  }

  init {
    liveStorages.getOrPut(databaseName) { mutableSetOf() }.add(this)
    scope.launch {
      try {
        refresh()
        initialized.complete(Unit)
      } catch (error: Throwable) {
        initialized.completeExceptionally(error)
      }
    }
  }

  override fun observeBeers(): Flow<List<StoredBeer>> = initializedFlow(beersState)

  override fun observeFavoriteBeers(): Flow<List<StoredBeer>> = initializedFlow(favoritesState)

  override suspend fun insertAll(beers: List<StoredBeer>) {
    mutate("insertAll", json.encodeToString(beers.map(::toRecord)))
  }

  override suspend fun insertPage(
    beers: List<StoredBeer>,
    surface: String,
    nextKey: Int?,
    totalCount: Int?,
  ) {
    mutate(
      "insertPage",
      json.encodeToString(InsertPagePayload(beers.map(::toRecord), surface, nextKey, totalCount)),
    )
  }

  override suspend fun getPagingState(surface: String): StoredPagingState? =
    read("readPaging", surface).takeUnless { it == "null" }?.let { json.decodeFromString<PagingRecord>(it).toStored() }

  override suspend fun countPagingStates(): Int = read("countPaging", "0").toInt()

  override suspend fun upsertAvailability(beer: StoredBeer) {
    mutate("upsertAvailability", json.encodeToString(toRecord(beer)))
  }

  override suspend fun upsertFavorite(beer: StoredBeer) {
    mutate("upsertFavorite", json.encodeToString(toRecord(beer)))
  }

  override suspend fun deleteAll() {
    mutate("deleteAll", "null")
  }

  override suspend fun count(): Int = read("count", "0").toInt()

  fun close() {
    if (closed) return
    closed = true
    unregisterInvalidationListener(listenerId)
    liveStorages[databaseName]?.let { peers ->
      peers.remove(this)
      if (peers.isEmpty()) liveStorages.remove(databaseName)
    }
    initialized.completeExceptionally(CancellationException("IndexedDbBeersStorage is closed"))
    scope.coroutineContext.cancel()
  }

  private suspend fun mutate(operation: String, payload: String) {
    initialized.await()
    writeMutex.withLock {
      check(!closed) { "IndexedDbBeersStorage is closed" }
      execute(operation, payload)
      refresh()
      liveStorages[databaseName]
        ?.filter { it !== this && !it.closed }
        ?.forEach { peer -> peer.scope.launch { peer.refresh() } }
      publishInvalidation(databaseName)
    }
  }

  private suspend fun read(operation: String, payload: String): String {
    initialized.await()
    check(!closed) { "IndexedDbBeersStorage is closed" }
    return execute(operation, payload)
  }

  private suspend fun execute(operation: String, payload: String): String =
    executeIndexedDb(databaseName, DATABASE_VERSION, operation, payload).await().toString()

  private fun initializedFlow(state: MutableStateFlow<List<StoredBeer>>): Flow<List<StoredBeer>> =
    flow {
      initialized.await()
      emitAll(state.asStateFlow())
    }

  private suspend fun refresh() {
    refreshMutex.withLock {
      if (closed) return
      val beers = json.decodeFromString<List<BeerRecord>>(execute("readBeers", "")).map(BeerRecord::toStored)
      beersState.value = beers
      favoritesState.value = beers.filter { it.isFavorite }.sortedWith(compareBy<StoredBeer> { it.name }.thenBy { it.id })
    }
  }

  private companion object {
    const val DEFAULT_DATABASE_NAME = "billionbeers"
    const val DATABASE_VERSION = 1
  }
}

@Serializable
private data class InsertPagePayload(
  val beers: List<BeerRecord>,
  val surface: String,
  val nextKey: Int?,
  val totalCount: Int?,
)

private fun toRecord(beer: StoredBeer) = BeerRecord(
  id = beer.id,
  name = beer.name,
  tagline = beer.tagline,
  description = beer.description,
  imageUrl = beer.imageUrl,
  abv = beer.abv,
  ibu = beer.ibu,
  foodPairing = beer.foodPairing,
  availability = beer.availability,
  isFavorite = beer.isFavorite,
  styleName = beer.styleName,
  breweryName = beer.breweryName,
  srm = beer.srm,
  releasedYear = beer.releasedYear,
  minServingTemperature = beer.minServingTemperature,
  maxServingTemperature = beer.maxServingTemperature,
  fermentationMethod = beer.fermentationMethod,
  ingredients = beer.ingredients,
  recommendedGlasses = beer.recommendedGlasses,
)

private fun BeerRecord.toStored() = StoredBeer(
  id, name, tagline, description, imageUrl, abv, ibu, foodPairing, availability, isFavorite,
  styleName, breweryName, srm, releasedYear, minServingTemperature, maxServingTemperature,
  fermentationMethod, ingredients, recommendedGlasses,
)

private fun PagingRecord.toStored() = StoredPagingState(surface, nextKey, totalCount, refreshedAt)
