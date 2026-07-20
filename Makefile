# Variables
MODULE ?=
SCENARIO ?= incremental_build
NAME ?=
PASCAL ?=
FEATURE ?=
GRADLE_PATH ?=

MODULE_TRIMMED := $(strip $(MODULE))
MODULE_PREFIX = $(if $(MODULE_TRIMMED),$(MODULE_TRIMMED):,)
UI_TEST_PREFIX = $(if $(MODULE_TRIMMED),$(MODULE_TRIMMED):,:app:)

# Wrapper for Gradle to support build-brief (bb) or rtk if available
GRADLE_RUNNER := $(shell if command -v bb >/dev/null 2>&1; then echo "bb ./gradlew"; elif command -v build-brief >/dev/null 2>&1; then echo "build-brief --gradle ./gradlew"; elif command -v rtk >/dev/null 2>&1; then echo "rtk ./gradlew"; else echo "./gradlew"; fi)

.PHONY: help setup setup-ai-tools update-android-skills build bundle-release install clean test konsist ui-test screenshot-record screenshot-verify screenshot-clean lint format check check-duplicates check-unused-deps benchmark-micro benchmark-macro benchmark-check generate-baseline gradle-benchmark jacoco-report install-profiler install-diffuse new-feature-module new-dev-app

help: ## Show this help message.
	@echo "\n📊 BillionBeers Makefile Help"
	@printf "%.s━" {1..40}
	@echo "\nUsage: make <target> [MODULE=<module_path>] [SCENARIO=<scenario_name>]\n"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-25s\033[0m %s\n", $$1, $$2}'
	@echo "\n💡 Examples:"
	@echo "  make setup"
	@echo "  make test MODULE=:feature:beerslist"
	@echo "  make screenshot-record MODULE=:core:designsystem"
	@echo "  make gradle-benchmark SCENARIO=clean_build"

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
JVM_TEST_MODULES := :core-common :testing-utils

test: ## Run unit tests for the specified module (or all).
ifeq ($(MODULE_TRIMMED),)
	$(GRADLE_RUNNER) testDebugUnitTest $(addsuffix :test,$(JVM_TEST_MODULES)) --continue
else ifneq ($(filter $(MODULE_TRIMMED),$(JVM_TEST_MODULES) :konsist),)
	$(GRADLE_RUNNER) $(MODULE_TRIMMED):test --continue
else
	$(GRADLE_RUNNER) $(MODULE_PREFIX)testDebugUnitTest --continue
endif

konsist: ## Run Konsist architecture rules.
	$(GRADLE_RUNNER) :konsist:test

ui-test: ## Run connected Android tests (UI tests) on an already-running device/emulator.
	$(GRADLE_RUNNER) $(UI_TEST_PREFIX)connectedDebugAndroidTest

ui-test-managed: ## Run instrumented tests on the ATD fast-lane managed device (boots/tears down its own emulator).
	$(GRADLE_RUNNER) $(UI_TEST_PREFIX)atdApi35DebugAndroidTest

ui-test-managed-newest: ## Run instrumented tests on the API 37 managed device (forward-compat lane).
	$(GRADLE_RUNNER) $(UI_TEST_PREFIX)pixel9Api37DebugAndroidTest

ui-test-managed-all: ## Run instrumented tests on both managed devices, every opted-in module (what CI runs).
	$(GRADLE_RUNNER) ciGroupDebugAndroidTest

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

detekt-baseline: ## Update Detekt baselines for all modules.
	$(GRADLE_RUNNER) $(MODULE_PREFIX)detektBaseline

format: ## Apply code formatting (Spotless).
	$(GRADLE_RUNNER) $(MODULE_PREFIX)spotlessApply

check: ## Run all quality checks (lint + test).
	$(GRADLE_RUNNER) $(MODULE_PREFIX)check

check-duplicates: ## Check for duplicate classes in the dependency graph.
	$(GRADLE_RUNNER) $(MODULE_PREFIX)checkDebugDuplicateClasses

check-unused-deps: ## Detect declared but unused dependencies.
	$(GRADLE_RUNNER) $(MODULE_PREFIX)detectUnusedDependencies

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

gradle-benchmark: ## Run Gradle Profiler with a specific scenario. Usage: make gradle-benchmark SCENARIO=clean_build
	gradle-profiler --benchmark --scenario-file ./benchmark.scenarios $(SCENARIO)

# Reporting
jacoco-report: ## Generate the unified Jacoco coverage report.
	$(GRADLE_RUNNER) jacocoRootReport

update-docs: ## Update README.md with the latest library versions from the catalog.
	@chmod +x scripts/update_readme_versions.sh
	@./scripts/update_readme_versions.sh

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
