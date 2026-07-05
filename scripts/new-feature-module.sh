#!/bin/bash
set -euo pipefail

# Scaffolds a new feature/<name> module: build.gradle.kts (billionbeers.android.feature +
# billionbeers.android.screenshot), manifest, a minimal Screen/ViewModel skeleton with one
# @PreviewLightDark preview (so the screenshot convention plugin's auto-generated Paparazzi test
# has something to snapshot), and a starter unit test. No settings.gradle.kts edit needed -
# settings.gradle.kts auto-discovers any directory with a build.gradle.kts.
#
# This produces a REGULAR feature module only (com.android.library), not a dynamic feature.
# Turning it into a dynamic feature afterward is a separate, manual step - see
# feature/beerdetail/build.gradle.kts (applies billionbeers.android.dynamic.feature instead) and
# add its Gradle path to app/build.gradle.kts's `dynamicFeatures` set, plus a
# DynamicFeatureContentProvider implementation (see
# navigation/.../DynamicFeatureContentProvider.kt and feature/beerdetail's *ProviderImpl*).
#
# Usage:
#   scripts/new-feature-module.sh <name> [PascalName]
#
# Examples:
#   scripts/new-feature-module.sh favorites
#   scripts/new-feature-module.sh favorites Favorites

NAME="${1:-}"
if [ -z "$NAME" ]; then
  echo "Usage: scripts/new-feature-module.sh <name> [PascalName]" >&2
  echo "Example: scripts/new-feature-module.sh favorites Favorites" >&2
  exit 1
fi

DEFAULT_PASCAL="$(tr '[:lower:]' '[:upper:]' <<< "${NAME:0:1}")${NAME:1}"
PASCAL="${2:-$DEFAULT_PASCAL}"

MODULE_DIR="feature/$NAME"
PACKAGE="com.simtop.feature.$NAME"
PACKAGE_PATH="com/simtop/feature/$NAME"

if [ -d "$MODULE_DIR" ]; then
  echo "error: $MODULE_DIR already exists" >&2
  exit 1
fi

SRC_MAIN="$MODULE_DIR/src/main/java/$PACKAGE_PATH"
SRC_TEST="$MODULE_DIR/src/test/java/$PACKAGE_PATH"
mkdir -p "$SRC_MAIN" "$SRC_TEST" "$MODULE_DIR/src/main/res/values"

cat > "$MODULE_DIR/build.gradle.kts" <<EOF
plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.screenshot")
}

android { namespace = "$PACKAGE" }

dependencies {
  implementation(project(":beerdomain:api"))
  implementation(project(":navigation"))
  implementation(project(":presentation_utils"))
  implementation(project(":core"))
  implementation(project(":core:designsystem"))
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.androidx.material3.android)
  implementation(libs.androidx.ui.tooling.preview.android)

  testImplementation(project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)
}
EOF

cat > "$MODULE_DIR/.gitignore" <<'EOF'
/build
EOF

cat > "$MODULE_DIR/consumer-rules.pro" <<'EOF'
EOF

cat > "$MODULE_DIR/proguard-rules.pro" <<'EOF'
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
EOF

mkdir -p "$MODULE_DIR/src/main"
cat > "$MODULE_DIR/src/main/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
</manifest>
EOF

cat > "$MODULE_DIR/src/main/res/values/strings.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="${NAME}_title">$PASCAL</string>
</resources>
EOF

cat > "$SRC_MAIN/${PASCAL}ViewModel.kt" <<EOF
package $PACKAGE

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ${PASCAL}UiState(val isLoading: Boolean = false)

@ContributesIntoMap(AppScope::class)
@ViewModelKey(${PASCAL}ViewModel::class)
@Inject
class ${PASCAL}ViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(${PASCAL}UiState())
  val uiState: StateFlow<${PASCAL}UiState> = _uiState.asStateFlow()
}
EOF

cat > "$SRC_MAIN/${PASCAL}Screen.kt" <<EOF
package $PACKAGE

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ${PASCAL}Screen(viewModel: ${PASCAL}ViewModel = metroViewModel()) {
  val uiState by viewModel.uiState.collectAsState()
  ${PASCAL}Content(uiState = uiState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ${PASCAL}Content(uiState: ${PASCAL}UiState) {
  Scaffold(topBar = { TopAppBar(title = { Text(text = stringResource(R.string.${NAME}_title)) }) }) {
    paddingValues ->
    Box(
      modifier = Modifier.fillMaxSize().padding(paddingValues),
      contentAlignment = Alignment.Center,
    ) {
      // TODO: build the real $PASCAL screen here.
      Text(text = "TODO: $PASCAL (isLoading=\${uiState.isLoading})")
    }
  }
}

@PreviewLightDark
@Composable
fun ${PASCAL}ScreenPreview() {
  BillionBeersTheme { ${PASCAL}Content(uiState = ${PASCAL}UiState()) }
}
EOF

cat > "$SRC_TEST/${PASCAL}ViewModelTest.kt" <<EOF
package $PACKAGE

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isFalse

class ${PASCAL}ViewModelTest {

  @Test
  fun \`initial state is not loading\`() {
    val viewModel = ${PASCAL}ViewModel()

    expectThat(viewModel.uiState.value.isLoading).isFalse()
  }
}
EOF

cat > "$MODULE_DIR/README.md" <<EOF
# feature/$NAME

Stamped out by \`scripts/new-feature-module.sh\`. A regular (non-dynamic) feature module - no
settings.gradle.kts edit was needed, it's auto-discovered.

## Still TODO after generation

1. Replace \`${PASCAL}UiState\`/\`${PASCAL}ViewModel\`/\`${PASCAL}Content\` with the real screen -
   this is a bare skeleton with no repository/domain dependency wired in, since every feature
   needs a different one. Follow \`feature/beerslist\` as the reference pattern (paginated list,
   \`BeersRepository\` injected, \`CoroutineDispatcherProvider\` for dispatching).
2. If this needs a Konsist cross-feature boundary check (no other feature module should import
   \`$PACKAGE\`, and this module shouldn't import another feature's package), add a pair of tests
   to \`konsist/src/test/kotlin/com/simtop/konsist/FeatureModuleBoundaryTest.kt\` following its
   existing beerslist/beerdetail pattern.
3. If this screen needs on-demand delivery (Play Feature Delivery), it needs to become a dynamic
   feature instead - that's a manual conversion, not something this script does. See
   \`feature/beerdetail/build.gradle.kts\` (applies \`billionbeers.android.dynamic.feature\`) and
   \`docs/beerdetail_dev_app.md\` for why that comes with real trade-offs of its own.
4. \`./gradlew :feature:$NAME:testDebugUnitTest\` and
   \`./gradlew :feature:$NAME:recordPaparazziDebug\` (there's no baseline snapshot yet - record one
   for \`${PASCAL}ScreenPreview\` before running \`make screenshot-verify\`).
EOF

echo "Created $MODULE_DIR (auto-discovered by settings.gradle.kts - no include(...) needed)."
echo "Next steps:"
echo "  1. Read $MODULE_DIR/README.md"
echo "  2. ./gradlew :feature:$NAME:testDebugUnitTest"
echo "  3. ./gradlew :feature:$NAME:recordPaparazziDebug   # record the initial preview baseline"
