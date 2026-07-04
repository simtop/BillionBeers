# BillionBeers AI Developer & Token Optimization Guidelines

This guide details how AI coding agents and developers can optimize token usage, run common tasks, and interact with the BillionBeers project tooling.

---

## 🪙 AI Token Optimization Tools

To reduce token consumption by 60–90%, BillionBeers integrates support for three tools. You should use them locally and in CI:

1. **`rtk` (Rust Token Killer)**: Compresses stdout logs from tools like `git status`, test runners, etc.
   - Usage: Prepended to command, e.g. `rtk git status`.
2. **`snip`**: YAML-driven CLI proxy that filters LLM context inputs.
   - Usage: Prepended to command, e.g. `snip git diff`.
3. **`build-brief` (`bb`)**: Filters verbose Gradle outputs down to core summaries and failures while saving raw logs to `/tmp`.
   - Usage: Prepended to `gradlew`, e.g. `bb ./gradlew assembleDebug`.

---

## 🛠️ Build & Development Commands

Always use the `Makefile` wrappers which automatically detect if `bb` or `rtk` is installed and route commands through them.

- **Clean project**: `make clean`
- **Deep Cache Clean**: `make deep-clean`
- **Build APK**: `make build`
- **Install debug APK**: `make install`
- **Run Unit Tests**: `make test [MODULE=:feature:beerslist]`
- **Run Linting**: `make lint`
- **Auto-Format Code**: `make format`
- **Verify Screenshots**: `make screenshot-verify`
- **Record Screenshots**: `make screenshot-record`

---

## 📱 Android CLI & Custom Skills

This workspace includes the `android-cli` plugin skill. Coding agents can call:
- `android-cli` to start/stop emulators, install APKs, run tests, and check system/SDK status.
- Refer to the skill specifications in `android-cli/skills/SKILL.md` to trigger actions dynamically.
