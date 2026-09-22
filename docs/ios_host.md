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
simulator, use `make ios-host-run`. The host owns one `IosAppSession` for the scene; the session
owns the Room/HTTP graph and closes it when the Swift model is released. The Compose shell receives
iOS-owned copy and fallback image/resource adapters. Network image loading, full accessibility and
localized parity are follow-up work for T8.2.

The existing `make ios-compile`, `make ios-framework` and `make ios-test` targets remain the
platform-data gates. A simulator host build proves Xcode linkage and packaging; it is not a
physical-device signing or distribution proof.
