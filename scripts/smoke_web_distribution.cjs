#!/usr/bin/env node

const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');

const distribution = path.resolve(process.argv[2] || 'web-app/build/kotlin-webpack/wasmJs/productionExecutable');
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
  ], { stdio: ['ignore', 'pipe', 'pipe'] });
  let output = '';
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
    const socket = new WebSocket(socketUrl);
    await new Promise((resolve, reject) => {
      socket.onopen = resolve;
      socket.onerror = reject;
    });
    const devtools = new DevTools(socket);
    const browserVersion = await devtools.send('Browser.getVersion');
    const errors = [];
    const requests = [];
    devtools.on('Runtime.exceptionThrown', event => {
      const details = event.exceptionDetails || {};
      errors.push(details.exception?.description || details.text || 'runtime exception');
    });
    devtools.on('Runtime.consoleAPICalled', event => {
      const values = (event.args || []).map(arg => arg.value ?? arg.description ?? '').join(' ');
      if (values) errors.push(`console.${event.type}: ${values}`);
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
    await devtools.send('Page.navigate', { url: `http://127.0.0.1:${port}${prefix}` });
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
    await devtools.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: 30, y: 110, button: 'left', clickCount: 1 });
    await devtools.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: 30, y: 110, button: 'left', clickCount: 1 });
    const route = await waitFor(devtools, `location.hash === '#beer/web-smoke-1' ? location.hash : ''`);
    if (route !== '#beer/web-smoke-1') throw new Error(`Beer interaction failed: ${route}`);

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
      const root = document.querySelector('#root')?.getBoundingClientRect();
      const canvas = findCanvas(document)?.getBoundingClientRect();
      return { viewport: [innerWidth, innerHeight], root: [root?.width, root?.height], canvas: [canvas?.width, canvas?.height] };
    })()`);
    if (wide.root[0] < 800 || wide.root[1] < 600 || wide.canvas[0] < 800 || wide.canvas[1] < 600) {
      throw new Error(`Wide Web smoke failed: ${JSON.stringify(wide)}`);
    }
    if (errors.length) throw new Error(`Unexpected browser errors: ${errors.join('; ')}`);
    console.log(JSON.stringify({ distribution, url: `http://127.0.0.1:${port}${prefix}`, browser: browserVersion.product, compact, route, wide, errors }, null, 2));
    socket.close();
  } finally {
    if (chrome.exitCode === null) {
      chrome.kill('SIGTERM');
      await new Promise(resolve => chrome.once('exit', resolve));
    }
    server.close();
    fs.rmSync(profile, { recursive: true, force: true });
  }
}

main().catch(error => {
  console.error(`Web production smoke failed: ${error.message}`);
  process.exitCode = 1;
});
