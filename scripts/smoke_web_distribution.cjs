#!/usr/bin/env node

const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');

const distribution = path.resolve(process.argv[2] || 'web-app/build/dist/wasmJs/productionExecutable');
const prefix = '/web-smoke/';
const browserCandidates = [
  '/Applications/Brave Browser.app/Contents/MacOS/Brave Browser',
  'google-chrome',
  'google-chrome-stable',
  'chromium',
  'chromium-browser',
];
const browser = process.env.WEB_SMOKE_BROWSER || process.env.CHROME_BIN || browserCandidates.find(candidate => {
  if (candidate.includes('/')) return fs.existsSync(candidate);
  try {
    require('node:child_process').execFileSync('sh', ['-lc', `command -v ${candidate}`]);
    return true;
  } catch (_) {
    return false;
  }
});

if (!browser) {
  console.error('Web smoke browser not found; set WEB_SMOKE_BROWSER or CHROME_BIN');
  process.exit(2);
}

const smokeEvidenceDir = process.env.WEB_SMOKE_ARTIFACT_DIR ? path.resolve(process.env.WEB_SMOKE_ARTIFACT_DIR) : null;
const ANSI_PATTERN = /\x1b(?:\[[0-?]*[ -/]*[@-~]|\][^\x07]*(?:\x07|\x1b\\))/g;
const RUNNER_PATH_PATTERN = /(?:\/Users|\/home|\/opt|\/private|\/tmp)\/[^\s,)]+/g;
const WASM_MEMORY_DEPRECATION = 'Accessing `memory` via `wasmExports` is deprecated. Use `kotlin.wasm.unsafe.wasmMemory` or update dependencies. Read more: https://kotl.in/vr3szr';

function sanitizeDiagnostic(value) {
  return String(value)
    .replace(ANSI_PATTERN, '')
    .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, ' ')
    .replace(RUNNER_PATH_PATTERN, '<runner-path>')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 1000);
}

function originOnly(url) {
  try {
    return new URL(url).origin;
  } catch (_) {
    return '<invalid-url>';
  }
}

async function writeFailureEvidence({ devtools, browserVersion, errors, requests, smokeError }) {
  if (!smokeEvidenceDir) return;
  try {
    await fs.promises.mkdir(smokeEvidenceDir, { recursive: true });
    let page = null;
    if (devtools) {
      try {
        const result = await devtools.send('Runtime.evaluate', {
          expression: `({
            path: location.pathname,
            hash: location.hash,
            viewport: [innerWidth, innerHeight],
          })`,
          returnByValue: true,
        });
        page = result.result?.value || null;
      } catch (error) {
        errors.push(`page state: ${sanitizeDiagnostic(error.message)}`);
      }
    }
    const diagnostic = {
      schema: 1,
      browser: sanitizeDiagnostic(browserVersion?.product || browser),
      error: sanitizeDiagnostic(smokeError?.message || 'Web smoke failed'),
      errors: errors.slice(0, 20).map(sanitizeDiagnostic),
      page,
      request_origins: [...new Set(requests.map(originOnly))].sort().slice(0, 20),
    };
    await fs.promises.writeFile(
      path.join(smokeEvidenceDir, 'web-smoke-failure.json'),
      `${JSON.stringify(diagnostic, null, 2)}\n`,
      'utf8',
    );
    if (devtools) {
      try {
        const screenshot = await devtools.send('Page.captureScreenshot', { format: 'png' });
        await fs.promises.writeFile(path.join(smokeEvidenceDir, 'web-smoke-failure.png'), Buffer.from(screenshot.data, 'base64'));
      } catch (error) {
        console.error(`Web smoke screenshot capture failed: ${sanitizeDiagnostic(error.message)}`);
      }
    }
  } catch (error) {
    console.error(`Web smoke evidence write failed: ${sanitizeDiagnostic(error.message)}`);
  }
}

const beer = {
  id: 'web-smoke-1',
  name: 'Web Smoke Lager',
  abv: 4.8,
  ibu: 20,
  image: { url: '' },
  available: true,
  translations: [{ language: { code: 'en' }, slogan: 'Smoke fixture', description: 'Production smoke fixture.' }],
  food_pairing: ['chips'],
  minimum_serving_temperature: 4,
  maximum_serving_temperature: 8,
};

function fixtureResponse(url) {
  const request = new URL(url);
  let body = [];
  if (request.pathname === '/beers') body = [beer];
  if (request.pathname === '/typologies') body = [{ id: 'web-smoke-style', name: 'Lager' }];
  if (request.pathname === '/breweries') body = [{ id: 'web-smoke-brewery', name: 'Smoke Brewery' }];
  return {
    status: 200,
    headers: {
      'content-type': 'application/json',
      'access-control-allow-origin': '*',
      'access-control-expose-headers': 'X-Total-Count',
      'x-total-count': '1',
    },
    body: JSON.stringify(body),
  };
}

function startServer() {
  const server = http.createServer((request, response) => {
    const requestPath = decodeURIComponent(new URL(request.url, 'http://127.0.0.1').pathname);
    if (!requestPath.startsWith(prefix)) {
      response.writeHead(404).end();
      return;
    }
    const relative = requestPath.slice(prefix.length);
    const file = path.resolve(distribution, relative || 'index.html');
    if (!file.startsWith(`${distribution}${path.sep}`) && file !== distribution) {
      response.writeHead(403).end();
      return;
    }
    fs.readFile(file, (error, contents) => {
      if (error) {
        response.writeHead(404).end();
        return;
      }
      const contentType = file.endsWith('.js') ? 'application/javascript' :
        file.endsWith('.wasm') ? 'application/wasm' : 'text/html; charset=utf-8';
      response.writeHead(200, { 'content-type': contentType });
      response.end(contents);
    });
  });
  return new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(server)));
}

class DevTools {
  constructor(socket) {
    this.socket = socket;
    this.nextId = 1;
    this.pending = new Map();
    this.events = new Map();
    socket.onmessage = event => {
      const message = JSON.parse(event.data);
      if (message.id) {
        const pending = this.pending.get(message.id);
        if (!pending) return;
        this.pending.delete(message.id);
        if (message.error) pending.reject(new Error(message.error.message));
        else pending.resolve(message.result);
        return;
      }
      const listeners = this.events.get(message.method) || [];
      for (const listener of listeners) listener(message.params || {});
    };
  }

  send(method, params = {}) {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }

  on(method, listener) {
    const listeners = this.events.get(method) || [];
    listeners.push(listener);
    this.events.set(method, listeners);
  }
}

async function waitForSocket(url) {
  for (let attempt = 0; attempt < 100; attempt++) {
    try {
      const response = await fetch(new URL('/json/list', url.replace('ws://', 'http://')).toString());
      const pages = await response.json();
      const page = pages.find(candidate => candidate.type === 'page');
      if (page) return page.webSocketDebuggerUrl;
    } catch (_) {
      // The browser needs a moment to expose its debugging endpoint.
    }
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error('Timed out waiting for Chrome DevTools page');
}

async function waitFor(devtools, expression, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const result = await devtools.send('Runtime.evaluate', { expression, returnByValue: true });
    if (result.result && result.result.value) return result.result.value;
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error(`Timed out waiting for: ${expression}`);
}

async function evaluate(devtools, expression) {
  const result = await devtools.send('Runtime.evaluate', { expression, returnByValue: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || 'Runtime evaluation failed');
  return result.result.value;
}

// Compose's accessibility nodes expose the bounds of the rendered canvas controls, including
// controls inside shadow roots. Click those bounds through real browser input.
function accessibilityNodesExpression() {
  return `(() => {
    const collect = root => [...root.querySelectorAll('*')].flatMap(node => [
      ...(node.id === 'cmp_a11y_root' ? [...node.querySelectorAll('*')] : []),
      ...(node.shadowRoot ? collect(node.shadowRoot) : [])
    ]);
    return collect(document);
  })()`;
}

async function clickControl(devtools, label) {
  const point = await waitFor(devtools, `(() => {
    const nodes = ${accessibilityNodesExpression()}.filter(node =>
      node.getAttribute('aria-label') === ${JSON.stringify(label)} ||
      node.textContent.trim() === ${JSON.stringify(label)});
    const bounds = nodes.map(node => node.getBoundingClientRect()).filter(bounds => {
      const x = bounds.left + bounds.width / 2;
      const y = bounds.top + bounds.height / 2;
      return bounds.width && bounds.height && x >= 0 && x < innerWidth && y >= 0 && y < innerHeight;
    }).sort((a, b) => a.width * a.height - b.width * b.height);
    // A container with only one label can have the same text but extend over empty list space.
    const target = bounds[0];
    return target ? { x: target.left + target.width / 2, y: target.top + target.height / 2 } : null;
  })()`);
  for (const type of ['mousePressed', 'mouseReleased']) {
    await devtools.send('Input.dispatchMouseEvent', { type, ...point, button: 'left', clickCount: 1 });
  }
}

async function waitForRouteContent(devtools, hash, text) {
  return waitFor(devtools, `location.hash === ${JSON.stringify(hash)} &&
    ${accessibilityNodesExpression()}.some(node =>
      node.textContent.includes(${JSON.stringify(text)})) &&
    (${JSON.stringify(text)} === 'Production smoke fixture.' ||
      !${accessibilityNodesExpression()}.some(node =>
        node.textContent.includes('Production smoke fixture.'))) ? location.hash : ''`);
}

async function verifyCompactFilterForms(devtools) {
  await devtools.send('Emulation.setDeviceMetricsOverride', { width: 320, height: 844, deviceScaleFactor: 1, mobile: false });
  const forms = [];
  const inspect = async (screen, fieldIndex) => {
    const bounds = await waitFor(devtools, `(() => {
      const nodes = ${accessibilityNodesExpression()};
      const field = nodes.filter(n => n.getAttribute('role') === 'textbox')[${fieldIndex}]?.getBoundingClientRect();
      const saves = nodes.filter(n => n.getAttribute('aria-label') === 'Save' || n.textContent.trim() === 'Save')
        .map(n => n.getBoundingClientRect()).filter(b => b.width && b.height)
        .sort((a, b) => a.width * a.height - b.width * b.height);
      const save = saves[0];
      return innerWidth === 320 && field?.width && save && field.right <= innerWidth + 1 && save.right <= innerWidth + 1
        ? {field: field.toJSON(), save: save.toJSON(), viewport: [innerWidth, innerHeight]} : null;
    })()`);
    if (bounds.field.width < 240 || bounds.save.top < bounds.field.bottom - 1 || bounds.save.bottom > bounds.viewport[1]) {
      throw new Error(`Compact ${screen} saved-filter form is squeezed or obscured: ${JSON.stringify(bounds)}`);
    }
    forms.push({ screen, ...bounds });
    if (smokeEvidenceDir) {
      await fs.promises.mkdir(smokeEvidenceDir, { recursive: true });
      const screenshot = await devtools.send('Page.captureScreenshot', { format: 'png' });
      await fs.promises.writeFile(path.join(smokeEvidenceDir, `web-${screen}-filter-form.png`), Buffer.from(screenshot.data, 'base64'));
    }
  };
  await inspect('catalog', 0);
  await clickControl(devtools, 'Search');
  // Compose exposes text-field hints through semantics rather than DOM textContent.
  await waitFor(devtools, `location.hash === '#search' && ${accessibilityNodesExpression()}.filter(n => n.getAttribute('role') === 'textbox').length === 2`);
  await inspect('search', 1);
  await clickControl(devtools, 'Back');
  await waitForRouteContent(devtools, '#catalog', 'Browse');
  await clickControl(devtools, 'Browse');
  await waitForRouteContent(devtools, '#browse', 'Lager');
  await clickControl(devtools, 'Lager');
  await waitForRouteContent(devtools, '#browse/style/web-smoke-style', 'Web Smoke Lager');
  await inspect('browse', 0);
  await clickControl(devtools, 'Back');
  await waitForRouteContent(devtools, '#browse', 'Styles');
  await clickControl(devtools, 'Back');
  await waitForRouteContent(devtools, '#catalog', 'Browse');
  await devtools.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: false });
  await waitFor(devtools, `${accessibilityNodesExpression()}.some(n => n.getAttribute('role') === 'textbox' && n.getBoundingClientRect().width > 320)`);
  return forms;
}

async function verifyBrowseHistory(devtools) {
  const category = '#browse/style/web-smoke-style';
  const detail = '#beer/web-smoke-1';
  const steps = [];
  const record = async (name, hash, text) => {
    try {
      steps.push({ name, hash: await waitForRouteContent(devtools, hash, text) });
    } catch (error) {
      const observed = await evaluate(devtools, 'location.hash');
      throw new Error(`Browse history step '${name}' expected ${hash}, observed ${observed}: ${error.message}`);
    }
  };
  await evaluate(devtools, 'history.back()');
  await record('catalog', '#catalog', 'Browse');
  await clickControl(devtools, 'Browse');
  await record('browse', '#browse', 'Lager');
  await clickControl(devtools, 'Lager');
  await record('category', category, 'Web Smoke Lager');
  await clickControl(devtools, 'Web Smoke Lager. Available');
  await record('detail', detail, 'Production smoke fixture.');
  await clickControl(devtools, 'Back');
  await record('in-app back to category', category, 'Web Smoke Lager');
  await clickControl(devtools, 'Back');
  await record('in-app back to browse', '#browse', 'Styles');
  await evaluate(devtools, 'history.forward()');
  await record('browser forward to category', category, 'Web Smoke Lager');
  await evaluate(devtools, 'history.forward()');
  await record('browser forward to detail', detail, 'Production smoke fixture.');
  await evaluate(devtools, 'history.back()');
  await record('browser back to category', category, 'Web Smoke Lager');
  await devtools.send('Page.reload');
  await record('reload category', category, 'Web Smoke Lager');
  await evaluate(devtools, 'history.back()');
  await record('browser back after reload', '#browse', 'Styles');
  await evaluate(devtools, 'history.forward()');
  await record('browser forward after reload', category, 'Web Smoke Lager');
  return steps;
}

async function verifySavedFilterHistory(devtools) {
  const steps = [];
  const record = async (name, hash, text) => {
    try {
      steps.push({ name, hash: await waitForRouteContent(devtools, hash, text) });
    } catch (error) {
      const observed = await evaluate(devtools, 'location.hash');
      throw new Error(`Saved-filter history step '${name}' expected ${hash}, observed ${observed}: ${error.message}`);
    }
  };
  const point = await waitFor(devtools, `(() => {
    const field = ${accessibilityNodesExpression()}.find(n => n.getAttribute('role') === 'textbox');
    const bounds = field?.getBoundingClientRect();
    return bounds?.width ? {x: bounds.left + bounds.width / 2, y: bounds.top + bounds.height / 2} : null;
  })()`);
  for (const type of ['mousePressed', 'mouseReleased']) {
    await devtools.send('Input.dispatchMouseEvent', { type, ...point, button: 'left', clickCount: 1 });
  }
  for (const text of 'Smoke saved filter') {
    await devtools.send('Input.dispatchKeyEvent', { type: 'keyDown', key: text, text });
    await devtools.send('Input.dispatchKeyEvent', { type: 'keyUp', key: text });
  }
  await waitFor(devtools, `${accessibilityNodesExpression()}.some(n => n.textContent.includes('Smoke saved filter'))`);
  await new Promise(resolve => setTimeout(resolve, 300));
  await clickControl(devtools, 'Save');
  await new Promise(resolve => setTimeout(resolve, 300));
  await clickControl(devtools, 'Saved filters');
  await record('preset list', '#saved-filters', 'Smoke saved filter');
  await new Promise(resolve => setTimeout(resolve, 300));
  await clickControl(devtools, 'Smoke saved filter');
  await record('applied results', '#saved-filters', 'Web Smoke Lager');
  await waitFor(devtools, `!${accessibilityNodesExpression()}.some(n => n.textContent.trim() === 'Rename')`);
  await new Promise(resolve => setTimeout(resolve, 300));
  await clickControl(devtools, 'Web Smoke Lager. Available');
  await record('detail', '#beer/web-smoke-1', 'Production smoke fixture.');
  await clickControl(devtools, 'Back');
  await record('in-app back to results', '#saved-filters', 'Web Smoke Lager');
  await new Promise(resolve => setTimeout(resolve, 300));
  await clickControl(devtools, 'Back');
  await record('in-app back to preset list', '#saved-filters', 'Rename');
  await evaluate(devtools, 'history.forward()');
  await record('browser forward to results', '#saved-filters', 'Web Smoke Lager');
  await evaluate(devtools, 'history.forward()');
  await record('browser forward to detail', '#beer/web-smoke-1', 'Production smoke fixture.');
  await evaluate(devtools, 'history.back()');
  await record('browser back to results', '#saved-filters', 'Web Smoke Lager');
  await evaluate(devtools, 'history.back()');
  await record('browser back to preset list', '#saved-filters', 'Rename');
  await evaluate(devtools, 'history.forward()');
  await record('browser forward to results again', '#saved-filters', 'Web Smoke Lager');
  await devtools.send('Page.reload');
  await record('reload results falls back to preset list', '#saved-filters', 'Rename');
  await evaluate(devtools, 'history.back()');
  await record('browser back after reload', '#saved-filters', 'Rename');
  await evaluate(devtools, 'history.forward()');
  await record('browser forward after reload', '#saved-filters', 'Rename');
  await evaluate(devtools, 'history.forward()');
  await record('detail survives reload of prior result entry', '#beer/web-smoke-1', 'Production smoke fixture.');
  await evaluate(devtools, 'history.back()');
  await record('old result token falls back to list', '#saved-filters', 'Rename');
  await new Promise(resolve => setTimeout(resolve, 300));
  await clickControl(devtools, 'Smoke saved filter');
  await record('reapply after reload', '#saved-filters', 'Web Smoke Lager');
  await evaluate(devtools, 'history.back()');
  await record('back after reapply', '#saved-filters', 'Rename');
  if (smokeEvidenceDir) {
    const screenshot = await devtools.send('Page.captureScreenshot', { format: 'png' });
    await fs.promises.writeFile(path.join(smokeEvidenceDir, 'web-saved-filter-list.png'), Buffer.from(screenshot.data, 'base64'));
  }
  return steps;
}

const missingBeerCopy = {
  en: {
    title: 'Beer unavailable',
    message: 'This browser has not loaded this beer yet. Open the catalog to find beers.',
    action: 'Open catalog',
  },
  fr: {
    title: 'Bière indisponible',
    message: 'Ce navigateur n’a pas encore chargé cette bière. Ouvrez le catalogue pour trouver des bières.',
    action: 'Ouvrir le catalogue',
  },
  es: {
    title: 'Cerveza no disponible',
    message: 'Este navegador aún no ha cargado esta cerveza. Abre el catálogo para encontrar cervezas.',
    action: 'Abrir catálogo',
  },
};

async function waitForMissingBeer(devtools, hash, language = 'en') {
  const copy = missingBeerCopy[language];
  await waitForRouteContent(devtools, hash, copy.title);
  await waitForRouteContent(devtools, hash, copy.message);
  await waitFor(devtools, `${accessibilityNodesExpression()}.some(node =>
    node.textContent.trim() === ${JSON.stringify(copy.action)})`);
}

async function verifyColdMissingBeer(devtools, requests, browserVersion) {
  const hash = '#beer/web-smoke-1';
  const steps = [];
  await waitForMissingBeer(devtools, hash);
  steps.push({ name: 'cold uncached detail', hash: await evaluate(devtools, 'location.hash') });
  if (smokeEvidenceDir) {
    await fs.promises.mkdir(smokeEvidenceDir, { recursive: true });
    const screenshot = await devtools.send('Page.captureScreenshot', { format: 'png' });
    await fs.promises.writeFile(path.join(smokeEvidenceDir, 'web-missing-beer.png'), Buffer.from(screenshot.data, 'base64'));
  }
  for (const language of ['fr', 'es', 'en']) {
    await devtools.send('Emulation.setUserAgentOverride', {
      userAgent: browserVersion.userAgent,
      acceptLanguage: language,
    });
    await devtools.send('Page.reload');
    await waitForMissingBeer(devtools, hash, language);
    steps.push({ name: `uncached reload (${language})`, hash: await evaluate(devtools, 'location.hash') });
  }
  const apiRequests = requests.filter(url => url.startsWith('https://brewbuddy.dev/'));
  if (apiRequests.length) throw new Error(`An uncached detail entry made API requests: ${JSON.stringify(apiRequests)}`);
  const historyLength = await evaluate(devtools, 'history.length');
  await clickControl(devtools, missingBeerCopy.en.action);
  await waitForRouteContent(devtools, '#catalog', 'Web Smoke Lager');
  if (await evaluate(devtools, 'history.length') !== historyLength) throw new Error('Missing-beer recovery added a browser history entry');
  steps.push({ name: 'open catalog', hash: await evaluate(devtools, 'location.hash'), apiRequestsBeforeRecovery: 0 });
  return steps;
}

async function verifyWarmMissingBeer(devtools) {
  const previous = '#browse/style/web-smoke-style';
  const missing = '#beer/web-smoke-missing';
  const steps = [];
  await evaluate(devtools, `location.hash = ${JSON.stringify(missing)}`);
  await waitForMissingBeer(devtools, missing);
  steps.push({ name: 'warm missing detail', hash: await evaluate(devtools, 'location.hash') });
  await evaluate(devtools, 'history.back()');
  await waitForRouteContent(devtools, previous, 'Web Smoke Lager');
  steps.push({ name: 'back from missing detail', hash: await evaluate(devtools, 'location.hash') });
  await evaluate(devtools, 'history.forward()');
  await waitForMissingBeer(devtools, missing);
  await devtools.send('Page.reload');
  await waitForMissingBeer(devtools, missing);
  steps.push({ name: 'forward and reload missing detail', hash: await evaluate(devtools, 'location.hash') });
  const historyLength = await evaluate(devtools, 'history.length');
  await clickControl(devtools, missingBeerCopy.en.action);
  await waitForRouteContent(devtools, '#catalog', 'Web Smoke Lager');
  if (await evaluate(devtools, 'history.length') !== historyLength) throw new Error('Warm missing-beer recovery added a browser history entry');
  await evaluate(devtools, 'history.back()');
  await waitForRouteContent(devtools, previous, 'Web Smoke Lager');
  await evaluate(devtools, 'history.forward()');
  await waitForRouteContent(devtools, '#catalog', 'Web Smoke Lager');
  steps.push({ name: 'recovery back/forward', hash: await evaluate(devtools, 'location.hash') });
  return steps;
}

function waitForExit(child, timeoutMs) {
  if (child.exitCode !== null || child.signalCode !== null) return Promise.resolve(true);
  return new Promise(resolve => {
    let settled = false;
    const finish = () => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      resolve(true);
    };
    const timeout = setTimeout(() => {
      if (settled) return;
      settled = true;
      resolve(false);
    }, timeoutMs);
    child.once('exit', finish);
  });
}

function signalChrome(chrome, signal) {
  if (process.platform === 'win32') {
    chrome.kill(signal);
    return;
  }
  try {
    process.kill(-chrome.pid, signal);
  } catch (error) {
    if (error.code !== 'ESRCH') throw error;
  }
}

async function closeServer(server) {
  if (!server.listening) return;
  await new Promise(resolve => server.close(resolve));
}

async function main() {
  if (!fs.existsSync(path.join(distribution, 'index.html'))) {
    throw new Error(`Distribution entrypoint missing: ${distribution}`);
  }
  const server = await startServer();
  const port = server.address().port;
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'billionbeers-web-smoke-'));
  const chrome = spawn(browser, [
    '--headless=new', '--hide-scrollbars', '--use-angle=swiftshader',
    '--remote-debugging-port=0', `--user-data-dir=${profile}`, '--window-size=390,844', 'about:blank',
  ], { stdio: ['ignore', 'pipe', 'pipe'], detached: process.platform !== 'win32' });
  let output = '';
  let socket;
  let devtools;
  let browserVersion;
  let smokeError;
  const errors = [];
  const warnings = [];
  const requests = [];
  const collect = chunk => { output += chunk.toString(); };
  chrome.stdout.on('data', collect);
  chrome.stderr.on('data', collect);

  try {
    const debugging = await new Promise((resolve, reject) => {
      const deadline = Date.now() + 15000;
      const poll = () => {
        const match = output.match(/DevTools listening on (ws:\/\/[^\s]+)/);
        if (match) return resolve(match[1]);
        if (chrome.exitCode !== null) return reject(new Error(`Browser exited with code ${chrome.exitCode}`));
        if (Date.now() >= deadline) return reject(new Error(`Timed out starting browser: ${output}`));
        setTimeout(poll, 50);
      };
      poll();
    });
    const socketUrl = await waitForSocket(debugging);
    socket = new WebSocket(socketUrl);
    await new Promise((resolve, reject) => {
      socket.onopen = resolve;
      socket.onerror = reject;
    });
    devtools = new DevTools(socket);
    browserVersion = await devtools.send('Browser.getVersion');
    devtools.on('Runtime.exceptionThrown', event => {
      const details = event.exceptionDetails || {};
      errors.push(details.exception?.description || details.text || 'runtime exception');
    });
    devtools.on('Runtime.consoleAPICalled', event => {
      const values = (event.args || []).map(arg => arg.value ?? arg.description ?? '').join(' ');
      // Kotlin logs this compatibility warning as an error when Compose resources read Wasm
      // memory. Record the exact warning; every other console/runtime message still fails.
      if (event.type === 'error' && values === WASM_MEMORY_DEPRECATION) {
        warnings.push(values);
      } else if (values) {
        errors.push(`console.${event.type}: ${values}`);
      }
    });
    devtools.on('Fetch.requestPaused', async event => {
      requests.push(event.request.url);
      try {
        if (event.request.url.startsWith('https://brewbuddy.dev/')) {
          const fixture = fixtureResponse(event.request.url);
          await devtools.send('Fetch.fulfillRequest', {
            requestId: event.requestId,
            responseCode: fixture.status,
            responseHeaders: Object.entries(fixture.headers).map(([name, value]) => ({ name, value })),
            body: Buffer.from(fixture.body).toString('base64'),
          });
        } else {
          await devtools.send('Fetch.continueRequest', { requestId: event.requestId });
        }
      } catch (error) {
        errors.push(String(error));
      }
    });
    await devtools.send('Runtime.enable');
    await devtools.send('Page.enable');
    await devtools.send('Fetch.enable', { patterns: [{ urlPattern: 'https://brewbuddy.dev/*', requestStage: 'Request' }] });
    await devtools.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: false });
    await devtools.send('Emulation.setUserAgentOverride', { userAgent: browserVersion.userAgent, acceptLanguage: 'en' });
    await devtools.send('Page.navigate', { url: `http://127.0.0.1:${port}${prefix}#beer/web-smoke-1` });
    const coldMissingBeer = await verifyColdMissingBeer(devtools, requests, browserVersion);
    const canvasExpression = `(() => {
      const findCanvas = root => {
        const direct = root.querySelector?.('canvas');
        if (direct) return direct;
        for (const element of root.querySelectorAll?.('*') || []) {
          if (element.shadowRoot) {
            const nested = findCanvas(element.shadowRoot);
            if (nested) return nested;
          }
        }
        return null;
      };
      return Boolean(findCanvas(document));
    })()`;
    await waitFor(devtools, canvasExpression);
    await new Promise(resolve => setTimeout(resolve, 3000));

    const compact = await evaluate(devtools, `(() => {
      const findCanvas = root => {
        const direct = root.querySelector?.('canvas');
        if (direct) return direct;
        for (const element of root.querySelectorAll?.('*') || []) {
          if (element.shadowRoot) {
            const nested = findCanvas(element.shadowRoot);
            if (nested) return nested;
          }
        }
        return null;
      };
      const root = document.querySelector('#root')?.getBoundingClientRect();
      const canvas = findCanvas(document)?.getBoundingClientRect();
      const findNodes = root => {
        const nodes = [...(root.querySelectorAll?.('*') || [])];
        const result = [];
        for (const node of nodes) {
          if (node.id === 'cmp_a11y_root') result.push(node);
          if (node.shadowRoot) result.push(...findNodes(node.shadowRoot));
        }
        return result;
      };
      const a11yNodes = findNodes(document);
      const a11yText = a11yNodes.map(node => node.textContent || '').join(' ');
      return { viewport: [innerWidth, innerHeight], root: [root?.width, root?.height], canvas: [canvas?.width, canvas?.height], hasFixture: a11yText.includes('Web Smoke Lager'), text: a11yText.slice(0, 1000), a11y: a11yNodes.map(node => node.outerHTML.slice(0, 1000)) };
    })()`);
    if (compact.root[0] < 300 || compact.root[1] < 600 || compact.canvas[0] < 300 || compact.canvas[1] < 600 || !compact.hasFixture) {
      throw new Error(`Compact Web smoke failed: ${JSON.stringify({ ...compact, requests, errors })}`);
    }
    const compactFilterForms = await verifyCompactFilterForms(devtools);
    await devtools.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: 30, y: 110, button: 'left', clickCount: 1 });
    await devtools.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: 30, y: 110, button: 'left', clickCount: 1 });
    const route = await waitFor(devtools, `location.hash === '#beer/web-smoke-1' ? location.hash : ''`);
    if (route !== '#beer/web-smoke-1') throw new Error(`Beer interaction failed: ${route}`);
    await waitFor(devtools, `(() => {
      const findNodes = root => {
        const nodes = [...(root.querySelectorAll?.('*') || [])];
        const result = [];
        for (const node of nodes) {
          if (node.id === 'cmp_a11y_root') result.push(node);
          if (node.shadowRoot) result.push(...findNodes(node.shadowRoot));
        }
        return result;
      };
      return findNodes(document).some(node => (node.textContent || '').includes('Production smoke fixture.'))
        ? 'detail'
        : '';
    })()`);

    await devtools.send('Emulation.setDeviceMetricsOverride', { width: 1000, height: 850, deviceScaleFactor: 1, mobile: false });
    await new Promise(resolve => setTimeout(resolve, 300));
    const wide = await evaluate(devtools, `(() => {
      const findCanvas = root => {
        const direct = root.querySelector?.('canvas');
        if (direct) return direct;
        for (const element of root.querySelectorAll?.('*') || []) {
          if (element.shadowRoot) {
            const nested = findCanvas(element.shadowRoot);
            if (nested) return nested;
          }
        }
        return null;
      };
      const findNodes = root => {
        const nodes = [...(root.querySelectorAll?.('*') || [])];
        const result = [];
        for (const node of nodes) {
          if (node.id === 'cmp_a11y_root') result.push(node);
          if (node.shadowRoot) result.push(...findNodes(node.shadowRoot));
        }
        return result;
      };
      const root = document.querySelector('#root')?.getBoundingClientRect();
      const canvas = findCanvas(document)?.getBoundingClientRect();
      const a11yText = findNodes(document).map(node => node.textContent || '').join(' ');
      return {
        viewport: [innerWidth, innerHeight],
        root: [root?.width, root?.height],
        canvas: [canvas?.width, canvas?.height],
        hasCatalog: a11yText.includes('Web Smoke Lager'),
        hasDetail: a11yText.includes('Production smoke fixture.'),
        text: a11yText.slice(0, 1000),
      };
    })()`);
    if (
      wide.root[0] < 800 ||
      wide.root[1] < 600 ||
      wide.canvas[0] < 800 ||
      wide.canvas[1] < 600 ||
      !wide.hasCatalog ||
      !wide.hasDetail
    ) {
      throw new Error(`Wide Web smoke failed: ${JSON.stringify(wide)}`);
    }
    const wideRoute = await evaluate(devtools, 'location.hash');
    if (wideRoute !== route) throw new Error(`Wide resize changed route: ${wideRoute}`);
    await devtools.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: false });
    await devtools.send('Page.reload');
    await waitForRouteContent(devtools, route, 'Production smoke fixture.');
    const browseHistory = await verifyBrowseHistory(devtools);
    const warmMissingBeer = await verifyWarmMissingBeer(devtools);
    const savedFilterHistory = await verifySavedFilterHistory(devtools);
    if (errors.length) throw new Error(`Unexpected browser errors: ${errors.join('; ')}`);
    console.log(JSON.stringify({ distribution, url: `http://127.0.0.1:${port}${prefix}`, browser: browserVersion.product, coldMissingBeer, compact, compactFilterForms, route, wide, wideRoute, cachedDetailReload: route, browseHistory, warmMissingBeer, savedFilterHistory, warnings, errors }, null, 2));
  } catch (error) {
    smokeError = error;
    throw error;
  } finally {
    if (smokeError) {
      await writeFailureEvidence({ devtools, browserVersion, errors, requests, smokeError });
    }
    if (devtools) {
      await devtools.send('Browser.close').catch(() => {});
    }
    socket?.close();
    signalChrome(chrome, 'SIGTERM');
    if (!(await waitForExit(chrome, 5000))) {
      signalChrome(chrome, 'SIGKILL');
      await waitForExit(chrome, 1000);
    }
    await closeServer(server);
    try {
      fs.rmSync(profile, { recursive: true, force: true, maxRetries: 20, retryDelay: 250 });
    } catch (cleanupError) {
      const message = `Web production smoke cleanup failed: ${cleanupError.message}`;
      if (smokeError) {
        console.error(message);
      } else {
        throw cleanupError;
      }
    }
  }
}

main().catch(error => {
  console.error(`Web production smoke failed: ${error.message}`);
  process.exitCode = 1;
});
