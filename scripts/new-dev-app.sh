#!/bin/bash
set -euo pipefail

# Scaffolds a new standalone "app-dev-<feature>" module: a minimal Android application that
# compiles only one feature module (+ fakes), for a seconds-scale build/install loop instead of
# the full :app. See app-dev-beerslist/README.md for the pattern this script replicates.
#
# Usage:
#   scripts/new-dev-app.sh <feature> [PascalName] [gradlePath]
#
# Examples:
#   scripts/new-dev-app.sh beerdetail
#   scripts/new-dev-app.sh beerdetail BeerDetail :feature:beerdetail
#
# <feature>    lowercase suffix used for the module dir (app-dev-<feature>) and package
#              (com.simtop.billionbeers.dev<feature>). Required.
# [PascalName] class-name prefix (Dev<PascalName>Application, etc). Defaults to <feature> with
#              just its first letter capitalized - pass this explicitly for multi-word names
#              (e.g. "beerdetail" -> "BeerDetail", not the default "Beerdetail").
# [gradlePath] the feature module's Gradle project path. Defaults to ":feature:<feature>".

FEATURE="${1:-}"
if [ -z "$FEATURE" ]; then
  echo "Usage: scripts/new-dev-app.sh <feature> [PascalName] [gradlePath]" >&2
  echo "Example: scripts/new-dev-app.sh beerdetail BeerDetail :feature:beerdetail" >&2
  exit 1
fi

DEFAULT_PASCAL="$(tr '[:lower:]' '[:upper:]' <<< "${FEATURE:0:1}")${FEATURE:1}"
PASCAL="${2:-$DEFAULT_PASCAL}"
GRADLE_PATH="${3:-:feature:$FEATURE}"

MODULE_DIR="app-dev-$FEATURE"
PACKAGE="com.simtop.billionbeers.dev$FEATURE"
PACKAGE_PATH="com/simtop/billionbeers/dev$FEATURE"
APP_CLASS="Dev${PASCAL}Application"

if [ -d "$MODULE_DIR" ]; then
  echo "error: $MODULE_DIR already exists" >&2
  exit 1
fi

GRADLE_PATH_DIR="$(echo "$GRADLE_PATH" | tr ':' '/' | sed 's#^/##')"
if [ ! -d "$GRADLE_PATH_DIR" ]; then
  echo "warning: $GRADLE_PATH_DIR does not look like an existing module directory - continuing anyway" >&2
fi

# Dynamic feature modules (billionbeers.android.dynamic.feature) hardcode
# implementation(project(":app")) and can only ever attach to that one base app - AGP rejects
# any other application depending on them at resource-link time ("this application is not
# configured to use dynamic features"), not just at runtime. There is no config flag around
# this. See docs/beerdetail_dev_app.md for the full investigation and possible workarounds.
if [ -f "$GRADLE_PATH_DIR/build.gradle.kts" ] && grep -q "dynamic.feature\|dynamic-feature" "$GRADLE_PATH_DIR/build.gradle.kts"; then
  echo "error: $GRADLE_PATH is a dynamic feature module - this script cannot make a" >&2
  echo "standalone dev-app depend on it (AGP hard-blocks it at assembleDebug, not just a" >&2
  echo "build-speed cost). See docs/beerdetail_dev_app.md for context and possible workarounds." >&2
  exit 1
fi

SRC_DIR="$MODULE_DIR/src/main/java/$PACKAGE_PATH"
DI_DIR="$SRC_DIR/di"
mkdir -p "$DI_DIR"

cat > "$MODULE_DIR/build.gradle.kts" <<EOF
plugins {
  id("billionbeers.android.application")
  id("billionbeers.android.compose")
  id("billionbeers.android.metro")
}

android {
  namespace = "$PACKAGE"

  defaultConfig {
    applicationId = "$PACKAGE"
    versionCode = 1
    versionName = "1.0"
  }
}

dependencies {
  // Only the module under active development, plus its fakes - keep this list as small as
  // possible so this assembles in seconds. Add whichever fakes module(s) $FEATURE needs.
  implementation(project("$GRADLE_PATH"))
  implementation(project(":beerdomain:api"))
  implementation(project(":beerdomain:fakes"))
  implementation(project(":core"))
  implementation(project(":core:designsystem"))
  implementation(project(":navigation"))
  implementation(project(":presentation_utils"))

  implementation(libs.androidPlayCore)
  implementation(libs.androidPlayCoreKtx)
  implementation(libs.androidxActivityCompose)
  implementation(libs.kotlinx.serialization.json)
}
EOF

cat > "$MODULE_DIR/src/main/AndroidManifest.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".${APP_CLASS}"
        android:allowBackup="false"
        android:label="Dev: $PASCAL"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:theme="@style/AppTheme.NoActionBar"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
EOF

cat > "$SRC_DIR/${APP_CLASS}.kt" <<EOF
package $PACKAGE

import android.app.Application
import $PACKAGE.di.DevAppGraph
import com.simtop.core.di.GraphProvider
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

class $APP_CLASS : Application(), GraphProvider {
  lateinit var appGraph: DevAppGraph

  override val metroViewModelFactory: MetroViewModelFactory
    get() = appGraph.metroViewModelFactory

  override fun onCreate() {
    super.onCreate()
    appGraph = createGraphFactory<DevAppGraph.Factory>().create(this)
  }
}
EOF

# MainActivity is deliberately left as a TODO scaffold: every feature's screen has a different
# entry-point signature (BeersListScreen just takes an onBeerClick callback; BeerDetail needs a
# Beer instance and is normally loaded reflectively from a dynamic feature), so this can't be
# templated correctly without knowing which feature is being wired up.
cat > "$SRC_DIR/MainActivity.kt" <<EOF
package $PACKAGE

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.presentation_utils.core.LocalSplitInstallManager
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val appGraph = (applicationContext as $APP_CLASS).appGraph
    enableEdgeToEdge()
    setContent {
      CompositionLocalProvider(
        LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
        LocalSplitInstallManager provides appGraph.splitInstallManager,
      ) {
        BillionBeersTheme {
          // TODO: host $PASCAL's real screen here, e.g.:
          //   ${PASCAL}Screen(onBackClick = {})
          // If the screen needs data its ViewModel doesn't fetch itself (like BeerDetail's Beer
          // parameter), construct a sample instance here or seed it via a fake repository in
          // di/DevFakesModule.kt instead.
        }
      }
    }
  }
}
EOF

cat > "$DI_DIR/DevAppGraph.kt" <<EOF
package $PACKAGE.di

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelMultibindings

@DependencyGraph(AppScope::class)
interface DevAppGraph : MetroViewModelMultibindings {
  val metroViewModelFactory: MetroViewModelFactory
  val splitInstallManager: SplitInstallManager

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides @ApplicationContext context: Context): DevAppGraph
  }
}
EOF

# Every standalone app graph needs its own copy: Metro's multibinding maps don't cross
# application-module boundaries.
cat > "$DI_DIR/ViewModelMapsModule.kt" <<EOF
package $PACKAGE.di

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

@ContributesTo(AppScope::class)
interface ViewModelMapsModule {
  @Multibinds(allowEmpty = true) fun viewModels(): Map<KClass<out ViewModel>, ViewModel>

  @Multibinds(allowEmpty = true)
  fun assistedViewModels(): Map<KClass<out ViewModel>, ViewModelAssistedFactory>

  @Multibinds(allowEmpty = true)
  fun manualAssistedViewModels():
    Map<KClass<out ManualViewModelAssistedFactory>, ManualViewModelAssistedFactory>
}
EOF

cat > "$DI_DIR/SplitInstallModule.kt" <<EOF
package $PACKAGE.di

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.simtop.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface SplitInstallModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideSplitInstallManager(@ApplicationContext context: Context): SplitInstallManager {
    return SplitInstallManagerFactory.create(context)
  }
}
EOF

# Left as a commented-out stub - every feature depends on different repositories, so there's no
# single correct fake binding to generate here.
cat > "$DI_DIR/DevFakesModule.kt" <<EOF
package $PACKAGE.di

// TODO: bind $PASCAL's repositories to fakes here, e.g.:
//
// import com.simtop.beerdomain.domain.repositories.BeersRepository
// import com.simtop.beerdomain.fakes.FakeBeersRepository
// import dev.zacsweers.metro.AppScope
// import dev.zacsweers.metro.ContributesTo
// import dev.zacsweers.metro.Provides
// import dev.zacsweers.metro.SingleIn
//
// @ContributesTo(AppScope::class)
// interface DevFakesModule {
//   @Provides
//   @SingleIn(AppScope::class)
//   fun provideBeersRepository(): BeersRepository = FakeBeersRepository(initialBeers = sampleBeers)
// }
EOF

cat > "$MODULE_DIR/README.md" <<EOF
# $MODULE_DIR

Standalone dev-app for \`$GRADLE_PATH\`, stamped out by \`scripts/new-dev-app.sh\`. See
\`app-dev-beerslist/README.md\` for the pattern this replicates in full.

## Still TODO after generation

1. **di/DevFakesModule.kt**: bind whatever repositories $PASCAL depends on to fakes (uncomment
   and adapt the template, or write a new fake if one doesn't exist in a \`:fakes\` module yet).
2. **MainActivity.kt**: replace the TODO block with the real call to $PASCAL's screen composable.
3. If \`$GRADLE_PATH\` is a dynamic feature (like \`:feature:beerdetail\`), its screen is normally
   loaded reflectively via \`DynamicFeatureContentProvider\` - for this dev-app you can most
   likely call the underlying \`@Composable\` screen function directly instead, since there's no
   split-install to gate here (check what the feature's provider implementation wraps).

## Regenerating

Delete \`$MODULE_DIR/\` and its \`include(":$MODULE_DIR")\` line in \`settings.gradle.kts\`, then
rerun: \`scripts/new-dev-app.sh $FEATURE $PASCAL $GRADLE_PATH\`
EOF

if ! grep -q "include(\":$MODULE_DIR\")" settings.gradle.kts; then
  awk -v line="include(\":$MODULE_DIR\")" '
    { print }
    /include\(":app"\)/ && !done { print line; done=1 }
  ' settings.gradle.kts > settings.gradle.kts.tmp
  mv settings.gradle.kts.tmp settings.gradle.kts
fi

echo "Created $MODULE_DIR and registered it in settings.gradle.kts."
echo "Next steps:"
echo "  1. Fill in $DI_DIR/DevFakesModule.kt"
echo "  2. Fill in $SRC_DIR/MainActivity.kt"
echo "  3. ./gradlew :$MODULE_DIR:installDebug"
