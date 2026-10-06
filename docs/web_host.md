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

For the CI-equivalent browser and static-output verification, run:

```shell
make web-verify
```

This runs the Web browser tests, builds `wasmJsBrowserDistribution`, and verifies Gradle's final
`web-app/build/dist/wasmJs/productionExecutable` output, including Compose resources. The intermediate
Webpack directory omits those resources and is not the distribution to publish. The command copies
the current HTML bootstrap into the final output, verifies that every JavaScript-referenced Wasm asset is
present, and boots that output in the packaged-production smoke. The smoke serves the bundle under a
non-root path and checks compact/wide root and canvas bounds plus deterministic fixture content. Set
`WEB_SMOKE_BROWSER` (or `CHROME_BIN`) when the browser executable is not discoverable on `PATH`.
The checks do not verify deployment headers, CDN image CORS, GitHub Pages behavior, or production
proxy operation; those remain separate evidence claims.

The proxy accepts only HTTPS URLs for the known `dropgate.malvik.dev/brewbuddy/images/` path and
allows CORS only from local development origins. Its tests do not contact the live CDN:

```shell
make web-image-proxy-test
```

The Makefile selects the configured Chromium-compatible browser (`CHROME_BIN`, or Brave
on the development machine). This verifies the Wasm browser task; it is not a claim of
support for every browser or operating system.

## Web release-confidence packet

A successful `make web-verify` also creates
`web-app/build/web-release-confidence/`. The packet contains `manifest.json` and a
self-contained `distribution/` copy of the exact output that passed static verification and the
packaged Chromium smoke. The manifest records stable relative paths, byte sizes, per-file
SHA-256 hashes, a deterministic tree SHA-256, the source revision, clean/dirty working-tree state,
and GitHub Actions repository/run/attempt/job/SHA provenance when running in CI.

Verify a downloaded packet independently with:

```shell
python3 scripts/verify_web_release_artifacts.py \
  --verify-packet web-app/build/web-release-confidence
```

Verification rejects missing, extra, changed, unsafe, or symlinked files, invalid static asset
references, and tree-digest mismatches. Compare `source_revision` and `GITHUB_SHA` with the
intended commit before using the packet; discard a mismatched packet and rerun `make web-verify`
from a clean checkout of that revision. The packet is success evidence only: the raw distribution
and `web-smoke-evidence` remain the appropriate failure diagnostics when the verification fails.

This packet proves only the bounded Web claims listed above. It does not prove deployed-host
MIME/cache/CSP headers, GitHub Pages behavior, upstream CDN image CORS or proxy behavior,
Safari/Firefox support, signing or store delivery, physical-device behavior, upgrade/rollback
behavior, or release readiness for Android, Desktop, or iOS. There is deliberately no aggregate
all-target release-ready verdict.

## Routes and history

The host uses hash routes so local development does not require server rewrite rules:

- `#catalog`
- `#favorites`
- `#search`
- `#browse`
- `#browse/style/<id>`
- `#browse/brewery/<id>`
- `#beer/<id>`

Only the route kind and stable ID are public; whole beer records are never serialized into
URLs. A beer detail route is resolved through the local IndexedDB-backed repository. An uncached ID
keeps its `#beer/<id>` URL and displays **Beer unavailable**, an explanation, and **Open catalog**.
The page's title, explanation and button use Compose resources in English, French and Spanish,
selected by browser locale; other locales fall back to English. This does not localize the rest of
the existing English Web shell. Opening Catalog replaces the unavailable history entry, so recovery
does not create a Back loop. No API request occurs for a cold missing-beer entry until recovery.
Malformed or unsupported routes still canonicalize to `#catalog` on startup, without a remote
detail request. Browse category hashes retain the category kind
and ID, using the ID as a deterministic cold-link title until browse metadata is available.

The shared shell emits explicit `Push`, `Pop`, and `Replace` events. User pushes use
`history.pushState`, app Back requests browser traversal with `history.back()`, and browser
`popstate`/`hashchange` events enter the shell without writing another history entry. Duplicate
browser notifications are suppressed so browser history remains the single traversal authority.
External requests use a buffered channel while the shell is absent on the missing-beer page. The
shell's dependencies remain stable across route-resolution recomposition, and superseded local
lookups are cancelled and checked against the current URL before publishing. Visiting an unavailable
entry disposes the shared shell; returning reconstructs the requested URL from local data rather than
promising restoration of unsaved drafts or scroll position across that entry.

The packaged smoke exercises uncached entry/reload in all three copy languages, zero API requests
before recovery, cached-detail reload, and warm missing-link Back/Forward/recovery alongside the
existing style-category journey. Its report records the exact Kotlin `wasmExports` memory-access
deprecation message as a known warning; every other console message or runtime exception still fails.
This compatibility warning remains a dependency/toolchain follow-up, not a claim of warning-free
runtime behavior.

## Saved-filter results and browser history

The preset list and applied results keep `#saved-filters` as their public URL. Applying a preset
creates a separate browser-history entry with an opaque, page-session token in `history.state`.
The Web adapter retains the applied name/query snapshot in memory; no query or whole preset is
written to the URL, browser storage or history state. Back/Forward within that page session restores
the exact applied snapshot, even if its entry owner was disposed. Restoration does not emit another
host navigation event. Returning to the list closes the result owner.

Reload deliberately restores the durable preset list, not the applied results. The new page session
cannot resolve older tokens, including entries reached through Back/Forward after reload, so those
entries also show the list. Saved presets themselves remain in IndexedDB and can be reapplied.
Session IDs prevent an old token from resolving to an unrelated newly applied preset. A copied URL
opens the list rather than sharing a query. A durable/shareable result URL remains a separate product
and route-contract decision; this host correction does not add `SavedFilterResults` to `PortableRoute`.

The packaged smoke saves and applies a preset using browser keyboard/mouse input, then checks detail,
both Back affordances, Forward, reload fallback, stale-token traversal and reapplication. This local
Chromium evidence does not establish deployed-origin or other-browser behavior.

## Lifecycle and images

`WebDataRuntime` owns the browser storage and Ktor Fetch client. It is opened once for the
page session and closed idempotently with the route listener. IndexedDB writes are committed
by the storage adapter rather than deferred to `unload`.

The Web host loads image bytes through the runtime's Ktor Fetch client and decodes them with
Skia. During `make web-run`, localhost configures the loopback proxy above for image requests;
other hosts request the CDN directly. Blank URLs, failed requests, decode failures, and blocked
image CORS requests fall back to a deterministic placeholder. There is no separate JavaScript
image implementation or failed-image retry UX yet. The current Compose/Wasm + Skia path is retained;
a browser-native image probe worked for representative CDN URLs, but synchronized DOM overlays were
rejected after they drifted during scrolling and adaptive layouts and crossed canvas layering in detail.
This is an image-rendering decision, not a claim that a complete DOM catalog/detail shell comparison
has been performed. See [ADR 0017](adr/0017-web-image-delivery.md) for the boundary and reconsideration criteria.

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
- deployed-host MIME/cache headers, external deployment, or GitHub Pages runtime behavior;
- live CDN image loading in production; the local proxy remains manual-QA-only.
