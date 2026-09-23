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

Run the packaged executable through its deterministic headless CLI path with:

```shell
make desktop-package-smoke
```

The smoke launches the bundled macOS arm64 executable with `--cli --data-dir` pointing at an isolated
temporary directory. It proves packaged process startup, bundled runtime/resource loading, local
database initialization, and a zero-row local read without contacting the API. The temporary directory
is removed after the process exits. The smoke is separate from the structural verifier so a bundle
layout failure and a runtime-launch failure remain diagnosable independently.

This evidence establishes that the declared package can be assembled, has the expected application
structure/resources, and can start its offline CLI path. It does not claim GUI launch or rendering,
live API behavior, network reachability, upgrade compatibility or migrations, Intel/universal support,
Linux or Windows support, signing, notarization, App Store distribution, or end-user installation. The
package is unsigned and distribution remains local-only until a separate release request defines
signing and hosting ownership.

The verifier's deterministic fixture tests run without a macOS host:

```shell
make desktop-package-test
```

The fixture tests skip Mach-O inspection because they use synthetic files; the real package verifier
keeps that check enabled.
