@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.simtop.billionbeers.web

import kotlin.JsFun

@JsFun("""
() => {
  const previous = globalThis.fetch;
  globalThis.__billionBeersPreviousFetch = previous;
  globalThis.__billionBeersFixtureRequestCount = 0;
  globalThis.fetch = (input, init) => {
    const requestUrl = new URL(input.url || input);
    globalThis.__billionBeersFixtureRequestCount++;
    const signal = init && init.signal;
    if (requestUrl.searchParams.get('q') === 'cancel') {
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => resolve(new Response('[]', {
          status: 200,
          headers: {'content-type': 'application/json'}
        })), 10000);
        signal?.addEventListener('abort', () => {
          clearTimeout(timer);
          reject(new DOMException('The operation was aborted', 'AbortError'));
        }, {once: true});
      });
    }
    if (requestUrl.pathname !== '/beers') {
      return Promise.resolve(new Response('{}', {
        status: 404,
        headers: {'content-type': 'application/json'}
      }));
    }
    const page = requestUrl.searchParams.get('_page');
    const mode = requestUrl.searchParams.get('q');
    if (mode === 'no-retry') {
      return Promise.resolve(new Response('{}', {
        status: 503,
        headers: {'content-type': 'application/json'}
      }));
    }
    if (mode === 'http-error') {
      return Promise.resolve(new Response('{}', {
        status: 404,
        headers: {'content-type': 'application/json'}
      }));
    }
    if (mode === 'malformed') {
      return Promise.resolve(new Response('{malformed', {
        status: 200,
        headers: {'content-type': 'application/json'}
      }));
    }
    const body = page === '1'
      ? JSON.stringify([{
          id: 'fixture-1', name: 'Fixture Lager', abv: 4.8, ibu: 20,
          image: {url: 'https://fixture.example/fixture-1.png'}, available: true,
          translations: [{language: {code: 'en'}, slogan: 'Fixture', description: 'Page one'}],
          food_pairing: ['chips']
        }])
      : page === '2'
        ? JSON.stringify([{
            id: 'fixture-2', name: 'Fixture Stout', abv: 5.1, ibu: 30,
            image: {url: 'https://fixture.example/fixture-2.png'}, available: false,
            translations: [{language: {code: 'en'}, slogan: 'Fixture', description: 'Page two'}],
            food_pairing: ['cake']
          }])
        : '[]';
    const headers = {'content-type': 'application/json'};
    if (mode !== 'no-header') headers['X-Total-Count'] = '40';
    return Promise.resolve(new Response(body, {
      status: 200,
      headers
    }));
  };
}
""")
private external fun installBrowserFetchFixture()

@JsFun("""
() => {
  if (globalThis.__billionBeersPreviousFetch) {
    globalThis.fetch = globalThis.__billionBeersPreviousFetch;
    delete globalThis.__billionBeersPreviousFetch;
  }
}
""")
private external fun uninstallBrowserFetchFixture()

@JsFun("() => globalThis.__billionBeersFixtureRequestCount || 0")
private external fun browserFetchFixtureRequestCount(): Int

internal fun browserFixtureRequestCount(): Int = browserFetchFixtureRequestCount()

internal suspend fun withBrowserFetchFixture(block: suspend () -> Unit) {
  installBrowserFetchFixture()
  try {
    block()
  } finally {
    uninstallBrowserFetchFixture()
  }
}
