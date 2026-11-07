# Desktop host

The Desktop host is a Compose Multiplatform JVM application backed by the shared repository and
local database. Its tests cover the real data graph and the Desktop icons inside the shared detail
screen: Back has a clickable button label, and Favorite exposes its current action and state.
These Compose renderer assertions do not establish macOS VoiceOver behavior. Run them with:

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
verifier checks the bundle's `Info.plist`, bundle identifier, Mach-O arm64 executable and non-empty
Resources directory. It verifies the DMG with `hdiutil`, mounts it read-only, checks the app inside
the image and detaches the volume.

Run the packaged executable through its deterministic headless CLI path with:

```shell
make desktop-package-smoke
```

The smoke runs the structural checks and launches the macOS arm64 executable directly from the
read-only image. It passes `--cli --data-dir` with an isolated temporary directory, then checks
packaged process startup, bundled runtime and resources, database initialization, and a zero-row
local read without contacting the API. It removes the temporary data and detaches the image.

CI requires this check for changes to the Desktop app or packaging scripts, Gradle configuration
or dependency metadata, build conventions, the Makefile, and the CI selector or workflow.
An explicit full CI dispatch from the default branch also runs the check.

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
