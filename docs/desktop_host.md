# Desktop host

The Desktop host is a Compose Multiplatform JVM application backed by the shared repository and
local database. Its deterministic data integration tests run with:

```shell
make desktop-test
```

## macOS distribution verification

The repository currently claims one packaged Desktop target: **macOS arm64**. Build the unsigned DMG
with:

```shell
make desktop-package
```

Build and structurally verify the application bundle and DMG with:

```shell
make desktop-package-verify
```

The package is produced under `desktop-app/build/compose/binaries/main/dmg/`, and the corresponding
application bundle is under `desktop-app/build/compose/binaries/main/app/BillionBeers.app/`. The
verifier checks the bundle's `Info.plist`, bundle identifier, executable, non-empty Resources
directory, and DMG presence. On macOS it also checks that the executable is a Mach-O arm64 binary.

This evidence establishes that the declared package can be assembled and contains the expected
application structure and resources. It does not claim GUI launch success, live API behavior,
upgrade compatibility, Intel/universal support, Linux or Windows support, signing, notarization,
App Store distribution, or end-user installation. The package is unsigned and distribution remains
local-only until a separate release request defines signing and hosting ownership.

The verifier's deterministic fixture tests run without a macOS host:

```shell
make desktop-package-test
```

The fixture tests skip Mach-O inspection because they use synthetic files; the real package verifier
keeps that check enabled.
