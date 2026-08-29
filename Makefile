# Variables
MODULE ?=
REPO ?=
BRANCH ?=
SCENARIO ?= clean_build_warm
# Warm-ups matter here: at 1 warm-up every scenario was still descending (ADR 0011).
BUILD_BUDGET_WARMUPS ?= 6
BUILD_BUDGET_ITERATIONS ?= 10
NAME ?=
PASCAL ?=
FEATURE ?=
GRADLE_PATH ?=
LOCALE ?=

# Local resizable emulator defaults. Override one setting for a one-off run, or edit these values
# when changing the normal QA device. API 37 requires EMULATOR_IMAGE_TAG=google_apis_ps16k.
EMULATOR_AVD ?= BillionBeers_Resizable
EMULATOR_API ?= 35
EMULATOR_IMAGE_TAG ?= google_apis
EMULATOR_ABI ?= arm64-v8a
EMULATOR_DEVICE_PROFILE ?= resizable
EMULATOR_RAM_MB ?= 3072
EMULATOR_DATA_DISK ?= 8G
EMULATOR_CPU_CORES ?= 4
EMULATOR_LCD_WIDTH ?= 1080
EMULATOR_LCD_HEIGHT ?= 2400
EMULATOR_LCD_DENSITY ?= 420
EMULATOR_GPU_MODE ?= auto
CONFIRM ?= 0
ACCEPT_ANDROID_LICENSES ?= 0

EMULATOR_SCRIPT_ENV = \
	AVD_NAME="$(EMULATOR_AVD)" \
	ANDROID_API="$(EMULATOR_API)" \
	SYSTEM_IMAGE_TAG="$(EMULATOR_IMAGE_TAG)" \
	ABI="$(EMULATOR_ABI)" \
	DEVICE_PROFILE="$(EMULATOR_DEVICE_PROFILE)" \
	RAM_MB="$(EMULATOR_RAM_MB)" \
	DATA_DISK="$(EMULATOR_DATA_DISK)" \
	CPU_CORES="$(EMULATOR_CPU_CORES)" \
	LCD_WIDTH="$(EMULATOR_LCD_WIDTH)" \
	LCD_HEIGHT="$(EMULATOR_LCD_HEIGHT)" \
	LCD_DENSITY="$(EMULATOR_LCD_DENSITY)" \
	GPU_MODE="$(EMULATOR_GPU_MODE)" \
	CONFIRM="$(CONFIRM)" \
	ACCEPT_ANDROID_LICENSES="$(ACCEPT_ANDROID_LICENSES)"
EMULATOR_RUN = $(EMULATOR_SCRIPT_ENV) bash scripts/emulator.sh

MODULE_TRIMMED := $(strip $(MODULE))
MODULE_PREFIX = $(if $(MODULE_TRIMMED),$(MODULE_TRIMMED):,)
UI_TEST_PREFIX = $(if $(MODULE_TRIMMED),$(MODULE_TRIMMED):,:app:)

# Wrapper for Gradle to support build-brief (bb) or rtk if available
GRADLE_RUNNER := $(shell if command -v bb >/dev/null 2>&1; then echo "bb ./gradlew"; elif command -v build-brief >/dev/null 2>&1; then echo "build-brief --gradle ./gradlew"; elif command -v rtk >/dev/null 2>&1; then echo "rtk ./gradlew"; else echo "./gradlew"; fi)

.PHONY: detekt-baseline help setup setup-ai-tools update-android-skills build bundle-release install clean test konsist check-data-layer-boundary compose-metrics ui-test ui-test-local ui-test-managed ui-test-managed-newest ui-test-managed-ci ui-test-managed-all emulator-create emulator-recreate emulator-start emulator-stop emulator-status emulator-delete screenshot-record screenshot-verify screenshot-clean lint android-lint format check check-duplicates check-unused-deps dependency-guard dependency-guard-baseline verification-metadata health repo-doctor benchmark-micro benchmark-macro benchmark-check generate-baseline gradle-benchmark build-budget build-budget-check jacoco-report coverage-check install-profiler install-diffuse new-feature-module new-dev-app play-listing-check play-listing-capture play-listing-reset store-frames

help: ## Show this help message.
	@echo "\n📊 BillionBeers Makefile Help"
	@printf "%.s━" {1..40}
	@echo "\nUsage: make <target> [MODULE=<module_path>] [SCENARIO=<scenario_name>]\n"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-25s\033[0m %s\n", $$1, $$2}'
	@echo "\n💡 Examples:"
	@echo "  make setup"
	@echo "  make test MODULE=:feature:beerslist"
	@echo "  make screenshot-record MODULE=:core:designsystem"
	@echo "  make gradle-benchmark SCENARIO=clean_build_warm"

setup: ## Setup local development environment (Git hooks, Git LFS, etc.).
	@echo "🔧 Setting up local development environment..."
	@if ! command -v git-lfs >/dev/null 2>&1; then \
		echo "⚠️ git-lfs not found! Please install it (e.g., 'brew install git-lfs' or 'sudo apt install git-lfs')."; \
	else \
		echo "✅ git-lfs is installed."; \
		echo "📦 Configuring Git LFS locally..."; \
		git lfs install --local; \
		git lfs pull; \
	fi
	@echo "🪝 Installing Git hooks..."
	@mkdir -p .git/hooks
	@cp config/git-hooks/pre-commit .git/hooks/pre-commit
	@chmod +x .git/hooks/pre-commit
	@echo "🎉 Setup complete! You're ready to build and test BillionBeers."

setup-ai-tools: ## Install token-saving tools (rtk, build-brief, snip) via Homebrew.
	@echo "🔧 Installing AI token-saving tools..."
	brew tap static-var/tap && brew install build-brief || true
	brew install rtk || true
	brew install edouard-claude/tap/snip || true
	@echo "🎉 AI tools setup complete!"

update-android-skills: ## Sync official Android skills (github.com/android/skills) into .claude/skills.
	@bash scripts/update-android-skills.sh

# Basic Commands
build: ## Assemble the debug APK.
	$(GRADLE_RUNNER) $(MODULE)assembleDebug

install: ## Install debug build. App install includes on-demand beerdetail via bundletool local-testing; pass MODULE=:foo for a plain installDebug.
ifeq ($(MODULE_TRIMMED),)
	$(GRADLE_RUNNER) :app:bundleDebug
	@bash scripts/install-local-testing.sh
else
	$(GRADLE_RUNNER) $(MODULE)installDebug
endif

bundle-release: ## Assemble the signed release App Bundle (.aab) for Play Store upload, incl. the beerdetail dynamic feature. Needs keystore.properties or STORE_FILE/STORE_PASSWORD/ALIAS/PASSWORD env vars.
	@if [ ! -f keystore.properties ] && [ -z "$$STORE_FILE" ]; then \
		echo "⚠️  No signing config found: create keystore.properties (STORE_FILE, STORE_PASSWORD, ALIAS, PASSWORD)"; \
		echo "    or export those as environment variables. The .aab will be unsigned otherwise."; \
	fi
	$(GRADLE_RUNNER) :app:bundleRelease
	@echo "📦 Release bundle (bundles the :feature:beerdetail on-demand module): app/build/outputs/bundle/release/app-release.aab"

clean: ## Clean all build outputs.
	$(GRADLE_RUNNER) clean

deep-clean: ## Stop daemon and deeply clean all gradle caches to fix corrupted states.
	$(GRADLE_RUNNER) --stop
	rm -rf .gradle build build-logic/.gradle build-logic/build build-logic/convention/.gradle build-logic/convention/build
	find . -name "build" -type d -prune -exec rm -rf '{}' +
	rm -rf ~/.gradle/caches/build-cache-*
	rm -rf ~/.gradle/caches/8.*
	rm -rf ~/.gradle/caches/9.*
	$(GRADLE_RUNNER) clean

# Testing
# Pure-JVM modules have no testDebugUnitTest task, so they are invisible to the Android-flavored
# test invocation and must be listed here explicitly (:konsist has its own target).
JVM_TEST_MODULES := :core-common :testing-utils :snapshot-processor

test: ## Run unit tests for the specified module (or all).
ifeq ($(MODULE_TRIMMED),)
	$(GRADLE_RUNNER) testDebugUnitTest $(addsuffix :test,$(JVM_TEST_MODULES)) --continue
	$(GRADLE_RUNNER) -p build-logic :convention:test --continue
else ifneq ($(filter $(MODULE_TRIMMED),$(JVM_TEST_MODULES) :konsist),)
	$(GRADLE_RUNNER) $(MODULE_TRIMMED):test --continue
else
	$(GRADLE_RUNNER) $(MODULE_PREFIX)testDebugUnitTest --continue
endif

konsist: ## Run Konsist architecture rules.
	$(GRADLE_RUNNER) :konsist:test

check-data-layer-boundary: ## Fail if a feature module's resolved compile classpath includes a data-layer module, even transitively (invariant 13's classpath backstop - konsist reads build-script text only).
	$(GRADLE_RUNNER) checkDataLayerClasspathBoundary

compose-metrics: ## Regenerate Compose compiler stability/skippability reports into each module's build/compose_compiler.
	# --rerun-tasks so up-to-date modules re-emit; reports are opt-in (composeCompilerReports) to
	# keep them off the normal build. Read <module>/build/compose_compiler/*-composables.txt for
	# non-skippable composables and *-classes.txt for unstable classes; stabilise the genuinely
	# immutable ones via compose-stability.conf (see that file's header).
	$(GRADLE_RUNNER) --rerun-tasks -PcomposeCompilerReports=true $(if $(MODULE_TRIMMED),$(MODULE_TRIMMED):,)compileReleaseKotlin

ui-test: ## Run connected Android tests (UI tests) on an already-running device/emulator.
	$(GRADLE_RUNNER) $(UI_TEST_PREFIX)connectedDebugAndroidTest

ui-test-local: emulator-start ## Start/reuse the local resizable emulator, then run connected UI tests.
	$(GRADLE_RUNNER) $(UI_TEST_PREFIX)connectedDebugAndroidTest

# Local emulator lifecycle. Settings are explicit above and can be overridden with EMULATOR_*.
emulator-create: ## Create the local resizable emulator and install its system image if needed.
	@$(EMULATOR_RUN) create

emulator-recreate: ## Recreate the local emulator with current settings (wipes data; CONFIRM=1 required).
	@$(EMULATOR_RUN) recreate

emulator-start: ## Start/reuse the local resizable emulator and wait for Android to boot.
	@$(EMULATOR_RUN) start

emulator-stop: ## Stop the local resizable emulator.
	@$(EMULATOR_RUN) stop

emulator-status: ## Show local emulator settings and running state.
	@$(EMULATOR_RUN) status

emulator-delete: ## Delete the local emulator (wipes data; CONFIRM=1 required).
	@$(EMULATOR_RUN) delete

ui-test-managed: ## Run instrumented tests on the ATD fast-lane managed device (boots/tears down its own emulator).
	$(GRADLE_RUNNER) $(UI_TEST_PREFIX)atdApi35DebugAndroidTest

ui-test-managed-newest: ## Run instrumented tests on the API 37 managed device (forward-compat lane).
	$(GRADLE_RUNNER) $(UI_TEST_PREFIX)pixel9Api37DebugAndroidTest

ui-test-managed-ci: ## Run what CI runs per push: the ATD fast lane, every opted-in module.
	$(GRADLE_RUNNER) ciGroupDebugAndroidTest

ui-test-managed-all: ## Run instrumented tests on both managed devices, every opted-in module.
	$(GRADLE_RUNNER) allDevicesDebugAndroidTest

# Screenshots (Paparazzi)
screenshot-record: ## Record golden images for Paparazzi.
	$(GRADLE_RUNNER) $(MODULE_PREFIX)recordPaparazziDebug

screenshot-verify: ## Verify screenshots against golden images.
	$(GRADLE_RUNNER) $(MODULE_PREFIX)verifyPaparazziDebug --continue

screenshot-clean: ## Clean and re-record golden images.
	$(GRADLE_RUNNER) clean $(MODULE_PREFIX)recordPaparazziDebug

# Quality & Analysis
lint: ## Run static analysis (Detekt).
	$(GRADLE_RUNNER) $(MODULE_PREFIX)detekt

android-lint: ## Run Android Lint over the app and its whole library graph (checkDependencies), gated by app/lint-baseline.xml.
	$(GRADLE_RUNNER) :app:lintDebug

detekt-baseline: ## Re-baseline Detekt (all modules, or MODULE=:foo). ALWAYS review the diff - a baseline is a suppression, and regenerating one to silence a NEW finding buries it.
	$(GRADLE_RUNNER) $(MODULE_PREFIX)detektBaseline

format: ## Apply code formatting (Spotless).
	$(GRADLE_RUNNER) $(MODULE_PREFIX)spotlessApply

check: ## Run all quality checks (lint + test).
	$(GRADLE_RUNNER) $(MODULE_PREFIX)check

check-duplicates: ## Check for duplicate classes in the dependency graph.
	$(GRADLE_RUNNER) $(MODULE_PREFIX)checkDebugDuplicateClasses

check-unused-deps: ## Detect declared but unused dependencies.
	$(GRADLE_RUNNER) $(MODULE_PREFIX)detectUnusedDependencies

dependency-guard: ## Verify the app's release runtime dependency graph against its committed baseline.
	$(GRADLE_RUNNER) :app:dependencyGuard

dependency-guard-baseline: ## Re-baseline the dependency graph after an intentional change (review the diff before committing).
	$(GRADLE_RUNNER) :app:dependencyGuardBaseline

# The task set mirrors what CI executes (ci.yml) plus assembleDebug for local builds - the ledger
# only covers configurations a build actually resolves, so anything CI runs must be resolved here
# or CI fails verification. Two passes on purpose, mirroring CI's separate lanes: writes merge
# into the existing file, and combining :app:lintDebug with verifyPaparazziDebug in ONE invocation
# trips Gradle's implicit-dependency validation on generatePaparazziTest outputs (a combination CI
# never runs). Don't add broader tasks (e.g. root assembleDebugAndroidTest) for the same reason.
# ideSyncArtifacts is the deliberate exception to "mirrors what CI executes": it resolves what
# Android Studio's sync needs and no build does, which the CI graph by definition cannot cover.
# It reads the bundled Groovy version off the running distribution, so a Gradle upgrade re-records
# the right coordinates instead of leaving the ledger pinned to the old ones (ADR 0007).
# The jvmargs override is needed because these are no-configuration-cache builds of the whole
# graph; the default 2g heap GC-thrashes.
# ORDER MATTERS after a dependency bump: :app:dependencyGuard below fails on a stale baseline and
# aborts the regen before the ledger is written. Run `make dependency-guard-baseline` first (the
# regen workflow does this automatically on Dependabot branches - ADR 0007).
VERIFICATION_WRITE_FLAGS := --write-verification-metadata sha256 --no-configuration-cache --continue \
	"-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8"
verification-metadata: ## Regenerate gradle/verification-metadata.xml over the full CI task graph (ADR 0007).
	$(GRADLE_RUNNER) $(VERIFICATION_WRITE_FLAGS) \
		help spotlessCheck detekt :app:lintDebug :app:dependencyGuard ideSyncArtifacts
	$(GRADLE_RUNNER) $(VERIFICATION_WRITE_FLAGS) \
		assembleDebug testDebugUnitTest :konsist:test :core-common:test :testing-utils:test \
		verifyPaparazziDebug jacocoRootReport ciGroupDebugAndroidTest

health: ## Aggregate the read-only health checks into a markdown report (docs/health/REPORT.md).
	@bash scripts/health-report.sh --run docs/health/REPORT.md

repo-doctor: ## Verify read-only GitHub repository settings and CODEOWNERS coverage.
	@REPO="$(REPO)" BRANCH="$(BRANCH)" bash scripts/repo-doctor.sh

# Benchmarking
benchmark-micro: ## Run microbenchmarks on a connected device.
	$(GRADLE_RUNNER) :benchmark:microbenchmark:connectedCheck

benchmark-macro: ## Run macrobenchmarks on a connected device.
	$(GRADLE_RUNNER) :benchmark:macrobenchmark:connectedCheck

benchmark-check: ## Run macrobenchmarks and fail if results exceed the configured performance budget.
	$(GRADLE_RUNNER) :benchmark:macrobenchmark:connectedCheck
	@chmod +x scripts/check-benchmark-budget.sh
	@JSON_FILE=$$(find benchmark/macrobenchmark/build/outputs/connected_android_test_additional_output -iname "*-benchmarkData.json" | head -1); \
	if [ -z "$$JSON_FILE" ]; then echo "error: no benchmarkData.json found" >&2; exit 1; fi; \
	./scripts/check-benchmark-budget.sh "$$JSON_FILE"

generate-baseline: ## Generate Baseline Profiles for the app (needs a booted device/emulator).
	$(GRADLE_RUNNER) :app:generateBaselineProfile

gradle-benchmark: ## Run Gradle Profiler with a specific scenario. Usage: make gradle-benchmark SCENARIO=clean_build_warm
	gradle-profiler --benchmark --scenario-file ./benchmark.scenarios $(SCENARIO)

build-budget: ## Measure build times and check them against config/build-time-budget.txt. Local only - see ADR 0011.
	@command -v gradle-profiler >/dev/null 2>&1 || \
		{ echo "❌ gradle-profiler not found. Install it with 'brew install gradle-profiler'." >&2; exit 1; }
	@echo "⏱  Measuring build times (~15-20 min). Close other heavy processes - this measures your machine."
	@rm -rf profile-out/baseline
	gradle-profiler --benchmark --scenario-file ./benchmark.scenarios \
		--warmups $(BUILD_BUDGET_WARMUPS) --iterations $(BUILD_BUDGET_ITERATIONS) \
		--output-dir profile-out/baseline
	@bash scripts/check-build-budget.sh profile-out/baseline/benchmark.csv

build-budget-check: ## Re-check the last build-budget measurement without re-measuring.
	@bash scripts/check-build-budget.sh profile-out/baseline/benchmark.csv

# Reporting
jacoco-report: ## Generate the unified Jacoco coverage report.
	$(GRADLE_RUNNER) jacocoRootReport

coverage-check: ## Fail if line coverage dropped below config/coverage-floor.txt (the high-water ratchet).
	$(GRADLE_RUNNER) jacocoRootReport
	@bash scripts/coverage-check.sh

update-docs: ## Update README.md with the latest library versions from the catalog.
	@chmod +x scripts/update_readme_versions.sh
	@./scripts/update_readme_versions.sh

store-frames: ## Render marketing store frames from the captured screenshots. Usage: make store-frames [LOCALE=en-US]
	@bash scripts/store-frames.sh $(if $(strip $(LOCALE)),$(LOCALE),en-US)

play-listing-check: ## Validate the Play Store listing assets against Play's limits.
	@bash scripts/play-listing.sh check

play-listing-capture: ## Put a device in capture state for Play screenshots (see the play-listing skill).
	@bash scripts/play-listing.sh prepare

play-listing-reset: ## Restore a device after `make play-listing-capture`.
	@bash scripts/play-listing.sh reset

new-feature-module: ## Scaffold a feature/<NAME> module. Usage: make new-feature-module NAME=favorites [PASCAL=Favorites]
	@chmod +x scripts/new-feature-module.sh
	@./scripts/new-feature-module.sh "$(NAME)" "$(PASCAL)"

new-dev-app: ## Scaffold a standalone app-dev-<FEATURE> module. Usage: make new-dev-app FEATURE=beerdetail [PASCAL=BeerDetail] [GRADLE_PATH=:feature:beerdetail]
	@chmod +x scripts/new-dev-app.sh
	@./scripts/new-dev-app.sh "$(FEATURE)" "$(PASCAL)" "$(GRADLE_PATH)"

# Helper Scripts
install-profiler: ## Install gradle-profiler via Homebrew.
	brew install gradle-profiler

install-diffuse: ## Install diffuse tool for APK analysis.
	./snapshot-testing/src/main/java/com/simtop/billionbeers/snapshot_testing/install-diffuse.sh
