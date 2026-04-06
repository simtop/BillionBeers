# Variables
MODULE ?= 
SCENARIO ?= incremental_build

.PHONY: help build install clean test ui-test screenshot-record screenshot-verify screenshot-clean lint format check check-duplicates check-unused-deps benchmark-micro benchmark-macro generate-baseline gradle-benchmark jacoco-report install-profiler install-diffuse

help: ## Show this help message.
	@echo "\n📊 BillionBeers Makefile Help"
	@printf "%.s━" {1..40}
	@echo "\nUsage: make <target> [MODULE=<module_path>] [SCENARIO=<scenario_name>]\n"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-25s\033[0m %s\n", $$1, $$2}'
	@echo "\n💡 Examples:"
	@echo "  make test MODULE=:feature:beerslist"
	@echo "  make screenshot-record MODULE=:core:designsystem"
	@echo "  make gradle-benchmark SCENARIO=clean_build"

# Basic Commands
build: ## Assemble the debug APK.
	./gradlew $(MODULE):assembleDebug

install: ## Install the debug APK to a connected device.
	./gradlew $(MODULE):installDebug

clean: ## Clean all build outputs.
	./gradlew clean

# Testing
test: ## Run unit tests for the specified module (or all).
	./gradlew $(MODULE):testDebugUnitTest

ui-test: ## Run connected Android tests (UI tests).
	./gradlew $(MODULE):connectedDebugAndroidTest

# Screenshots (Paparazzi)
screenshot-record: ## Record golden images for Paparazzi.
	./gradlew $(MODULE):recordPaparazziDebug

screenshot-verify: ## Verify screenshots against golden images.
	./gradlew $(MODULE):verifyPaparazziDebug

screenshot-clean: ## Clean and re-record golden images.
	./gradlew clean $(MODULE):recordPaparazziDebug

# Quality & Analysis
lint: ## Run static analysis (Detekt).
	./gradlew $(MODULE):detekt

format: ## Apply code formatting (Spotless).
	./gradlew $(MODULE):spotlessApply

check: ## Run all quality checks (lint + test).
	./gradlew $(MODULE):check

check-duplicates: ## Check for duplicate classes in the dependency graph.
	./gradlew $(MODULE):checkDebugDuplicateClasses

check-unused-deps: ## Detect declared but unused dependencies.
	./gradlew $(MODULE):detectUnusedDependencies

# Benchmarking
benchmark-micro: ## Run microbenchmarks on a connected device.
	./gradlew :benchmark:microbenchmark:connectedCheck

benchmark-macro: ## Run macrobenchmarks on a connected device.
	./gradlew :benchmark:macrobenchmark:connectedCheck

generate-baseline: ## Generate Baseline Profiles for the app.
	./gradlew :benchmark:baselineprofile:generateBaselineProfile

gradle-benchmark: ## Run Gradle Profiler with a specific scenario. Usage: make gradle-benchmark SCENARIO=clean_build
	gradle-profiler --benchmark --scenario-file ./benchmark.scenarios $(SCENARIO)

# Reporting
jacoco-report: ## Generate the unified Jacoco coverage report.
	./gradlew jacocoRootReport

update-docs: ## Update README.md with the latest library versions from the catalog.
	@chmod +x scripts/update_readme_versions.sh
	@./scripts/update_readme_versions.sh

# Helper Scripts
install-profiler: ## Install gradle-profiler via Homebrew.
	brew install gradle-profiler

install-diffuse: ## Install diffuse tool for APK analysis.
	./snapshot-testing/src/main/java/com/simtop/billionbeers/snapshot_testing/install-diffuse.sh
