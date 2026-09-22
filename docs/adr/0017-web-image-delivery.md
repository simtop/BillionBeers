# 0017: Keep Web image proxying local until production ownership is decided

## Status

Accepted for local manual QA. Production image delivery remains undecided.

## Context

The Web host receives image URLs in the Brew Buddy beer payload. The JSON API is browser-fetchable,
but the current `dropgate.malvik.dev` image responses do not expose `Access-Control-Allow-Origin`.
A browser Fetch therefore cannot read those image bytes and the Web host correctly falls back to its
placeholder. The application does not control that image host.

A local reverse proxy can fetch the image server-to-server and add narrowly scoped local CORS headers,
which makes manual Wasm QA useful. However, adding a proxy changes operational ownership. A local
process cannot establish that a deployed static Web host will work.

## Decision

Provide a loopback-only, allowlisted image proxy for `make web-run` and manual QA. It may fetch only
HTTPS image URLs on the known CDN host, rejects unsafe targets, and is not part of the Wasm bundle,
CI, or production deployment. The Web runtime uses it only when explicitly configured for localhost;
other hosts continue to request the CDN directly and retain placeholder fallback on CORS failure.

The proxy must not be replaced with `no-cors`, opaque-response handling, browser security-disable
flags, or unrestricted host forwarding. The existing browser-origin evidence boundary in ADR 0016
remains in force.

GitHub Pages is static hosting and cannot run this server-side proxy. A GitHub Pages deployment
therefore cannot claim image support under this decision: it will use direct CDN URLs, and current
CORS failures will remain placeholders.

## Consequences

- Local Web QA can exercise real image decoding without changing upstream hosting.
- Proxy startup, shutdown, allowlisting and CORS behavior become testable local tooling concerns.
- Local success does not prove production parity or image availability on GitHub Pages.
- The project avoids taking on a production proxy's security, cost, caching, availability and data
  ownership responsibilities before those tradeoffs are deliberately reviewed.

## Revisit triggers and options

Revisit before claiming production Web image support, especially if GitHub Pages or another static
host becomes a release target. Evaluate at least:

1. The CDN owner adding appropriate CORS headers and validating browser-origin image access.
2. Copying or mirroring the required image assets into same-origin static hosting, with a cache and
   update policy.
3. Deploying a separately owned edge/serverless/backend image proxy with authentication-free but
   tightly allowlisted forwarding, size/time limits, caching, monitoring and cost controls.
4. Continuing with placeholders if image delivery is not worth the operational ownership.

Any production option must include browser-origin verification, redirect/host validation, failure
behavior, static asset MIME/base-path/cache checks where applicable, and an explicit owner for the
service or asset mirror. A development proxy alone is not sufficient evidence.
