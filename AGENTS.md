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

## 📱 Android Skills

Skills live in `.claude/skills/` and Claude Code agents should reach for them
whenever a task matches their domain.

**Project skill — `billionbeers-android`** (`.claude/skills/billionbeers-android/SKILL.md`):
**Always use it whenever a task touches a real device or emulator** — starting/stopping
emulators, checking devices/SDK status, installing/launching the app, screenshots, logcat,
or running instrumented (`androidTest`) tests. It handles the correct `adb` path
(`$ANDROID_HOME/platform-tools/adb`) and the known `installDebug` bundle workaround. For
device-free work (unit tests, builds, lint, screenshots) keep using the `Makefile` targets above.

**Official Android skills** — a curated subset from
[github.com/android/skills](https://github.com/android/skills) is vendored into
`.claude/skills/` (each carries a `.android-skill-source` marker). Agents should use the
relevant one for its domain, e.g. `android-cli` (Google's `android` tool), `navigation-3`,
`r8-analyzer`, `perfetto-trace-analysis`, `testing-setup`, `edge-to-edge`, `adaptive`,
`android-intent-security`, and more.

- **Update / pull new skills**: `make update-android-skills` (wraps
  `scripts/update-android-skills.sh`). Re-run any time — it installs newly added upstream
  skills, prunes ones removed upstream, and never touches locally-authored skills like
  `billionbeers-android`.
- **Opt out of a skill**: add its name to `.claude/skills/.android-skills-ignore`. Ignored
  skills are skipped and removed on sync, so `make update-android-skills` won't resurrect
  skills you deliberately dropped. Currently ignored: `agp-9-upgrade`, `camera1-to-camerax`,
  `display-glasses-with-jetpack-compose-glimmer`, `migrate-xml-views-to-jetpack-compose`,
  `wear-compose-m3`.
