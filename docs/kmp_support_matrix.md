# Kotlin Multiplatform support matrix

This document records the evidence currently available for each host. Compilation, test execution,
host rendering, packaging, and release distribution are separate claims; one does not imply the others.

## Current target evidence

| Target | Current evidence | Claim boundary |
|---|---|---|
| Android | The T9.1 audit executed `make test`, formatting, lint, architecture/static, Paparazzi, managed-device, minified `releaseSmoke` and dependency-guard gates. The Paparazzi run produced an eight-module, 275-row executed inventory. | Android remains the primary released target. The evidence is managed-emulator and local minified-release proof; physical-device breadth, manual accessibility, signing, Play distribution, upgrade/rollback and other release operations remain unclaimed. See [Android release evidence](android_release_evidence.md). |
| Desktop | Compose Desktop macOS arm64 DMG and `.app` bundle build successfully. The verifier checks `Info.plist`, bundle identity, executable/resources, DMG presence and Mach-O arm64 identity. | Unsigned, local-only macOS arm64 structural proof. GUI launch, live API behavior, upgrades, Intel/universal, Linux, Windows, signing, notarization and public distribution are not claimed. See [Desktop host](desktop_host.md). |
| iOS | Shared framework and unsigned Xcode simulator host build on the documented macOS/Xcode arm64 simulator setup; host lifecycle and URL-routing behavior are covered by the host implementation/tests. | Simulator-host and framework evidence only. Physical-device signing, VoiceOver/manual accessibility, distribution, network image loading and App Store delivery are not claimed. See [iOS host](ios_host.md). |
| Web | Wasm browser tests, IndexedDB/runtime behavior, hash-route tests, and production static-distribution verification pass through `make web-verify`. | Chromium-compatible browser and local static-output evidence only. Safari/Firefox, deployed-host headers/cache behavior, upstream CDN image loading and GitHub Pages proxy behavior are not claimed. See [Web host](web_host.md) and [ADR 0017](adr/0017-web-image-delivery.md). |

## Shared behavior and verification boundaries

The shared product scope is catalog, paging, search, browse/detail, favorites and availability.
Android-only delivery mechanisms remain at the Android edge: Glance widgets, Play dynamic delivery,
split installation and Android component plumbing are not reproduced on other hosts.

The following are separate evidence categories:

- Domain/data contracts and repository behavior are protected by JVM, Android-host, browser and
  target-specific tests where those tests are actually available.
- KMP metadata or target compilation does not prove runtime resources, persistence upgrades or host
  rendering.
- A simulator build does not prove physical-device signing or distribution.
- A static Web bundle does not prove deployment headers, browser support breadth or CDN CORS.
- A Desktop bundle verifier does not prove GUI launch, upgrade compatibility or release signing.

## Bounded follow-up queue

1. Reconcile the iOS simulator evidence with any separately authorized physical-device/signing proof;
   otherwise keep the release claim partial.
2. Reconcile the Android evidence record when the managed-device matrix, manual accessibility review
   or release/distribution evidence materially changes; otherwise keep the bounded claim above.
3. Decide the production Web image-delivery owner and mechanism before claiming deployed image support;
   the loopback proxy remains manual-QA-only.
4. Revisit Compose Multiplatform runtime resource loading when `.cvr` assets are staged and executable
   in the relevant host/test runner; do not weaken the Android resource adapter to hide the gap.
5. Keep coverage, test, workflow, catalog and build-cost inventories synchronized with executed tasks;
   do not infer completion from a commit or an aggregate green status alone.

No external deployment, artifact publication, signing operation, app-store submission, or backend
provisioning is authorized by this matrix.
