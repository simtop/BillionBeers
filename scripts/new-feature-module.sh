#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: scripts/new-feature-module.sh <name> [PascalName]" >&2
  echo "Example: scripts/new-feature-module.sh favorites Favorites" >&2
}

NAME="${1:-}"
if [[ -z "$NAME" ]]; then
  usage
  exit 1
fi
if [[ ! "$NAME" =~ ^[a-z][a-z0-9]*$ ]]; then
  echo "error: name must match ^[a-z][a-z0-9]*$" >&2
  exit 1
fi

DEFAULT_PASCAL="$(printf '%s' "${NAME:0:1}" | tr '[:lower:]' '[:upper:]')${NAME:1}"
PASCAL="${2:-$DEFAULT_PASCAL}"
if [[ ! "$PASCAL" =~ ^[A-Z][A-Za-z0-9]*$ ]]; then
  echo "error: PascalName must be a PascalCase Kotlin identifier" >&2
  exit 1
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
DEFAULT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
ROOT_INPUT="${BILLIONBEERS_REPO_ROOT:-$DEFAULT_ROOT}"
if ! ROOT="$(cd -- "$ROOT_INPUT" 2>/dev/null && pwd -P)"; then
  echo "error: repository root does not exist: $ROOT_INPUT" >&2
  exit 1
fi
if [[ ! -f "$ROOT/settings.gradle.kts" || ! -f "$ROOT/Makefile" || ! -d "$ROOT/feature" || ! -d "$ROOT/shared" ]]; then
  echo "error: repository root must contain settings.gradle.kts, Makefile, feature/, and shared/: $ROOT" >&2
  exit 1
fi

FEATURE_DEST="$ROOT/feature/$NAME"
SHARED_DEST="$ROOT/shared/$NAME"
LOCK_DIR="$ROOT/.new-feature-module.lock"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  echo "error: another feature scaffold is already running for $ROOT" >&2
  exit 1
fi

TRANSACTION_ID="$$-${RANDOM:-0}-$(date +%s)"
MARKER=".billionbeers-scaffold-$TRANSACTION_ID"
STAGING=""
COMMITTED=false
cleanup() {
  local status=$?
  trap - EXIT HUP INT TERM
  if [[ "$COMMITTED" == "true" ]]; then
    rm -f -- "$FEATURE_DEST/$MARKER" "$SHARED_DEST/$MARKER"
  else
    if [[ -f "$FEATURE_DEST/$MARKER" ]]; then
      rm -rf -- "$FEATURE_DEST"
    fi
    if [[ -f "$SHARED_DEST/$MARKER" ]]; then
      rm -rf -- "$SHARED_DEST"
    fi
  fi
  if [[ -n "$STAGING" ]]; then
    rm -rf -- "$STAGING"
  fi
  rm -rf -- "$LOCK_DIR"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' HUP INT TERM

for destination in "$FEATURE_DEST" "$SHARED_DEST"; do
  if [[ -e "$destination" ]]; then
    echo "error: ${destination#"$ROOT/"} already exists" >&2
    exit 1
  fi
done

STAGING="$(mktemp -d "$ROOT/.new-feature-module.XXXXXX")"
FEATURE_MODULE="$STAGING/feature/$NAME"
SHARED_MODULE="$STAGING/shared/$NAME"
FEATURE_PACKAGE="com.simtop.feature.$NAME"
FEATURE_PACKAGE_PATH="com/simtop/feature/$NAME"
SHARED_PACKAGE="com.simtop.billionbeers.shared.$NAME"
SHARED_PACKAGE_PATH="com/simtop/billionbeers/shared/$NAME"

mkdir -p \
  "$FEATURE_MODULE/src/main/java/$FEATURE_PACKAGE_PATH" \
  "$FEATURE_MODULE/src/main/res/values" \
  "$FEATURE_MODULE/src/main/res/values-es" \
  "$FEATURE_MODULE/src/main/res/values-fr" \
  "$FEATURE_MODULE/src/test/java/$FEATURE_PACKAGE_PATH" \
  "$SHARED_MODULE/src/commonMain/kotlin/$SHARED_PACKAGE_PATH" \
  "$SHARED_MODULE/src/commonTest/kotlin/$SHARED_PACKAGE_PATH"
touch "$FEATURE_MODULE/$MARKER" "$SHARED_MODULE/$MARKER"

cat > "$SHARED_MODULE/build.gradle.kts" <<'EOF'
plugins {
  id("billionbeers.kmp.feature")
}
EOF

cat > "$SHARED_MODULE/.gitignore" <<'EOF'
/build
EOF

cat > "$SHARED_MODULE/src/commonMain/kotlin/$SHARED_PACKAGE_PATH/${PASCAL}ViewModel.kt" <<EOF
package $SHARED_PACKAGE

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data object ${PASCAL}UiState

open class ${PASCAL}ViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(${PASCAL}UiState)
  val uiState: StateFlow<${PASCAL}UiState> = _uiState.asStateFlow()
}
EOF

cat > "$SHARED_MODULE/src/commonMain/kotlin/$SHARED_PACKAGE_PATH/${PASCAL}Content.kt" <<EOF
package $SHARED_PACKAGE

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun Shared${PASCAL}Content(
  uiState: ${PASCAL}UiState,
  title: String,
  modifier: Modifier = Modifier,
) {
  when (uiState) {
    ${PASCAL}UiState ->
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title)
      }
  }
}
EOF

cat > "$SHARED_MODULE/src/commonTest/kotlin/$SHARED_PACKAGE_PATH/${PASCAL}ViewModelTest.kt" <<EOF
package $SHARED_PACKAGE

import kotlin.test.Test
import kotlin.test.assertSame

class ${PASCAL}ViewModelTest {
  @Test
  fun initialStateIsAvailable() {
    val viewModel = ${PASCAL}ViewModel()

    assertSame(${PASCAL}UiState, viewModel.uiState.value)
  }
}
EOF

cat > "$SHARED_MODULE/README.md" <<EOF
# shared/$NAME

Portable UI and ViewModel ownership for the paired feature/$NAME Android adapter. The module is
auto-discovered; do not add it to settings.gradle.kts.

Before integrating the feature, register this shared module in the current Makefile KMP target lists
and add the required exact role and dependency edges to the architecture policy. Those registries are
not edited by the scaffold. Routes, platform hosts, product behavior, wording, and screenshots also
remain deliberate integration work. Dynamic delivery and managed-device coverage are separate
choices rather than scaffold defaults.
EOF

cat > "$FEATURE_MODULE/build.gradle.kts" <<EOF
plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.screenshot")
}

android { namespace = "$FEATURE_PACKAGE" }

dependencies {
  implementation(this.project(":core:designsystem"))
  implementation(this.project(":shared:$NAME"))
}
EOF

cat > "$FEATURE_MODULE/.gitignore" <<'EOF'
/build
EOF

: > "$FEATURE_MODULE/consumer-rules.pro"
cat > "$FEATURE_MODULE/proguard-rules.pro" <<'EOF'
# Add feature-specific shrinking rules only when the implementation requires them.
EOF

cat > "$FEATURE_MODULE/src/main/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
EOF

for locale in values values-es values-fr; do
  cat > "$FEATURE_MODULE/src/main/res/$locale/strings.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="${NAME}_title">$PASCAL</string>
</resources>
EOF
done

cat > "$FEATURE_MODULE/src/main/java/$FEATURE_PACKAGE_PATH/${PASCAL}ViewModel.kt" <<EOF
package $FEATURE_PACKAGE

import androidx.lifecycle.ViewModel
import com.simtop.billionbeers.shared.$NAME.${PASCAL}ViewModel as Shared${PASCAL}ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey(${PASCAL}ViewModel::class)
@Inject
class ${PASCAL}ViewModel : Shared${PASCAL}ViewModel()
EOF

cat > "$FEATURE_MODULE/src/main/java/$FEATURE_PACKAGE_PATH/${PASCAL}Screen.kt" <<EOF
package $FEATURE_PACKAGE

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.$NAME.${PASCAL}UiState
import com.simtop.billionbeers.shared.$NAME.Shared${PASCAL}Content
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun ${PASCAL}Screen(
  modifier: Modifier = Modifier,
  viewModel: ${PASCAL}ViewModel = metroViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()
  ${PASCAL}Content(uiState = uiState, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ${PASCAL}Content(
  uiState: ${PASCAL}UiState,
  modifier: Modifier = Modifier,
) {
  val title = stringResource(R.string.${NAME}_title)
  Scaffold(
    modifier = modifier,
    topBar = { TopAppBar(title = { Text(text = title) }) },
  ) { paddingValues ->
    Shared${PASCAL}Content(
      uiState = uiState,
      title = title,
      modifier = Modifier.fillMaxSize().padding(paddingValues),
    )
  }
}

@PreviewLightDark
@Composable
internal fun ${PASCAL}ScreenPreview() {
  BillionBeersTheme { ${PASCAL}Content(uiState = ${PASCAL}UiState) }
}
EOF

cat > "$FEATURE_MODULE/src/test/java/$FEATURE_PACKAGE_PATH/${PASCAL}ViewModelTest.kt" <<EOF
package $FEATURE_PACKAGE

import com.simtop.billionbeers.shared.$NAME.${PASCAL}UiState
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ${PASCAL}ViewModelTest {
  @Test
  fun adapterExposesSharedInitialState() {
    val viewModel = ${PASCAL}ViewModel()

    assertSame(${PASCAL}UiState, viewModel.uiState.value)
  }
}
EOF

cat > "$FEATURE_MODULE/README.md" <<EOF
# feature/$NAME

Regular Android adapter for shared/$NAME, generated by scripts/new-feature-module.sh. Both modules
are auto-discovered; do not edit settings.gradle.kts.

## Remaining integration

1. Replace the neutral starter state/content with the real domain behavior. Keep portable ViewModel
   and Compose content in shared/$NAME and Android resources, Metro binding, previews, and platform
   behavior here.
2. Review the default, Spanish, and French title resources with the product wording. Register routes,
   navigation contracts, Android and shared platform hosts, app dependencies, and required strings.
3. Register shared/$NAME in the current Makefile KMP target lists. Add its exact architecture-policy
   role and the feature/$NAME to shared/$NAME edge with enforcement in the same change. The scaffold
   intentionally does not edit global registries.
4. This is not a dynamic feature and has no managed-device or Android UI-test enrollment. Make either
   change only when delivery or behavior requires it.
5. Run make build MODULE=:feature:$NAME, make test MODULE=:feature:$NAME, and the shared-module target
   checks. Record and inspect the preview with make screenshot-record MODULE=:feature:$NAME, then run
   screenshot verification, Android lint for resources, Konsist, and the architecture policy gate.
EOF

for destination in "$FEATURE_DEST" "$SHARED_DEST"; do
  if [[ -e "$destination" ]]; then
    echo "error: ${destination#"$ROOT/"} appeared while the scaffold was being prepared" >&2
    exit 1
  fi
done

mv -- "$FEATURE_MODULE" "$FEATURE_DEST"
if [[ "${BILLIONBEERS_SCAFFOLD_TEST_FAIL_AFTER_FIRST_PUBLISH:-}" == "1" ]]; then
  echo "error: injected scaffold publication failure" >&2
  exit 1
fi
mv -- "$SHARED_MODULE" "$SHARED_DEST"
COMMITTED=true

echo "Created feature/$NAME and shared/$NAME."
echo "Next steps:"
echo "  1. Read feature/$NAME/README.md and shared/$NAME/README.md"
echo "  2. Register architecture-policy and current KMP test-routing entries"
echo "  3. Integrate routes, hosts, strings, behavior, and screenshots deliberately"
