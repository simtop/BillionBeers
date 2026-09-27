# iOS host

The checked-in `iosApp/BillionBeers.xcodeproj` is the smallest host for the shared Compose shell.
It uses direct Gradle framework integration; CocoaPods, Swift Package Manager and distributable
XCFramework publishing are not part of this milestone.

## Supported local proof

- Host: macOS with Xcode 26.4 or a compatible Xcode release.
- Simulator architecture: arm64.
- Simulator used for the local proof: iPhone 17 Pro on iOS 26.4 (Xcode 26.4).
- Deployment target: iOS 17.0.
- Xcode scheme: `BillionBeers`.
- Configuration: `Debug`.
- Kotlin framework: `BillionBeersData.framework` from `:ios-shared`.
- Framework task: `:ios-shared:embedAndSignAppleFrameworkForXcode`.

Build the Kotlin framework and host with:

```shell
make ios-framework
make ios-host-build
```

The host build is unsigned and intended for a simulator. To install and launch on a booted
simulator, use `make ios-host-run`.

## Session and URL ownership

SwiftUI owns one `AppModel` and one `IosAppSession` for the scene. The Kotlin session owns the
Room/HTTP graph, the Compose controller and the coroutine scope used for URL cache lookups.
`close()` cancels pending lookups before closing the graph and is idempotent; Swift releases the
session with the model. Compose destination view models cancel their own scopes when destinations
leave the shared shell.

The app registers the `billionbeers` URL scheme. Supported URLs are:

- `billionbeers://beers` — catalog.
- `billionbeers://favorites` — favorites.
- `billionbeers://beers/<id>` — detail only when `<id>` is already in the local Room cache.

A missing-cache detail URL is ignored rather than fetching a separate detail endpoint. Unsupported
schemes, hosts and path shapes are ignored. URL delivery is forwarded through SwiftUI's
`onOpenURL` to the existing session, so a new session is not created for each URL.

## UI/resource parity

The shared shell keeps host-owned strings, icons, rows, errors and image content behind
`SharedAppHost`. iOS selects the complete English or French catalog from the device language
(`fr-*` selects French); unsupported languages fall back to English. The shell follows the iOS
system light/dark appearance. Custom iOS rows and icons expose localized accessibility labels and
availability state, while decorative list placeholders are hidden from accessibility.

The current iOS adapter intentionally keeps a stable placeholder for beer images. Network image
loading, physical-device signing, and distribution-only flows are not claimed by this host proof.
VoiceOver, keyboard/IME, RTL, larger-text, background/foreground, recreation and cancellation
checks remain manual QA; simulator evidence must be recorded separately from physical-device QA.

## Verification

The existing platform-data gates remain:

```shell
make ios-compile
make ios-framework
make ios-test
```

For the host and focused adapter checks:

```shell
make test MODULE=:ios-shared
make ios-host-build
make ios-host-run
```

A simulator host build proves Xcode linkage and packaging; it is not a physical-device signing,
VoiceOver, or distribution proof.

## iOS simulator evidence packet

After the existing compile, framework, host-build, simulator-test, and native-report validation gates
pass, create the bounded simulator evidence packet with:

```shell
make ios-compile
make ios-framework
make ios-host-build
make ios-test
make ios-simulator-evidence
```

The packet is written to `iosApp/build/ios-simulator-confidence/`. It contains `manifest.json`, the
exact unsigned `Debug-iphonesimulator/BillionBeers.app`, its embedded simulator
`BillionBeersData.framework`, and the four required non-vacuous `iosSimulatorArm64Test` JUnit report
trees. The manifest records the actual simulator destination, configuration, SDK, arm64 architecture,
source revision, clean/dirty state, CI provenance when available, per-file sizes and SHA-256 hashes,
and a deterministic tree digest. Logs remain failure diagnostics and are not success evidence.

Verify a copied packet independently with:

```shell
python3 scripts/verify_ios_simulator_artifacts.py \
  --verify-packet iosApp/build/ios-simulator-confidence
```

Verification rejects missing, extra, changed, unsafe, or symlinked files; invalid bundle metadata or
embedded framework content; malformed, empty, failing, or skipped native reports; and source/CI SHA
mismatches. Discard a mismatched packet and rerun the same sequence from a clean checkout of the
intended revision. The packet strengthens exact simulator evidence but does not reconcile the separate
physical-device/signing prerequisite.
