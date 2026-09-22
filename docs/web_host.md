# Web host

The Web host is a Wasm browser entrypoint for the shared Compose shell. It uses one
`WebDataRuntime` per page session, waits for IndexedDB startup before rendering, and
reuses the shared repository, pager and feature view models.

## Local verification

Run the browser data and host tests with:

```shell
make test MODULE=:web-app
```

Start the local browser host and its development-only image proxy for manual QA with:

```shell
make web-run
```

The command stops stale Gradle daemons, starts a loopback image proxy on
`http://127.0.0.1:8787`, waits for its health endpoint, and includes the Homebrew Node.js directory
in the Wasm server process PATH. Open the URL printed by Gradle, usually `http://localhost:8080`.
The proxy is stopped with the Wasm server when the command exits.

The proxy accepts only HTTPS URLs for the known `dropgate.malvik.dev/brewbuddy/images/` path and
allows CORS only from local development origins. Its tests do not contact the live CDN:

```shell
make web-image-proxy-test
```

The Makefile selects the configured Chromium-compatible browser (`CHROME_BIN`, or Brave
on the development machine). This verifies the Wasm browser task; it is not a claim of
support for every browser or operating system.

## Routes and history

The host uses hash routes so local development does not require server rewrite rules:

- `#catalog`
- `#favorites`
- `#search`
- `#browse`
- `#beer/<id>`

Only the route kind and beer ID are public. A beer detail route is resolved through the
local IndexedDB-backed repository. A missing cached ID falls back to the catalog and does
not trigger a remote detail request. User navigation reports route changes to the host,
which writes browser history; hash changes are validated before they enter the shared
shell.

## Lifecycle and images

`WebDataRuntime` owns the browser storage and Ktor Fetch client. It is opened once for the
page session and closed idempotently with the route listener. IndexedDB writes are committed
by the storage adapter rather than deferred to `unload`.

The Web host loads image bytes through the runtime's Ktor Fetch client and decodes them with
Skia. During `make web-run`, localhost configures the loopback proxy above for image requests;
other hosts request the CDN directly. Blank URLs, failed requests, decode failures, and blocked
image CORS requests fall back to a deterministic placeholder. There is no separate JavaScript
image implementation or failed-image retry UX yet.

## Evidence boundary

Currently proven by browser tests:

- real Fetch requests reach the common repository and typed HTTP/serialization failures;
- cancellation and paging behavior remain in the shared data layer;
- IndexedDB rows survive runtime close/reopen and preserve local favorites;
- supported and unsupported hash forms are parsed deterministically;
- public detail hashes contain only an ID, not a serialized `Beer` payload;
- the `brewbuddy.dev` JSON API is browser-fetchable from local development.

The published API collection documents `GET /images/:id`, but that endpoint returns image
metadata and a CDN URL; it does not proxy the image bytes. The returned
`dropgate.malvik.dev` image responses currently omit `Access-Control-Allow-Origin`, so direct browser
image Fetch is blocked and the Web host displays its placeholder. The local proxy moves that request
server-side for manual QA; it is not production parity and is not deployed by this project. GitHub
Pages cannot run a server-side proxy, so a GitHub Pages deployment will still use direct CDN URLs
and cannot claim image support until the CDN adds CORS, assets are mirrored to same-origin static
hosting, or a separately operated production proxy is approved and deployed. This is an upstream
hosting limitation, not a client-side Fetch or Skia failure. See ADR 0017.

Not yet claimed in this slice:

- Safari/Firefox support or a browser/OS support matrix;
- browser image loading against the current upstream CDN;
- VoiceOver/screen-reader, RTL, reduced-motion, keyboard/IME and broad responsive QA;
- optimized static output, subpath hosting, MIME/cache headers or external deployment;
- a CI Web lane. These are T8.4 or later verification work.
