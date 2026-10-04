# Contributing to Aloha Social for Android

## Requirements

- JDK 21 and an Android SDK with platform 37 and build-tools 37.0.0, which is what
  CI installs. `compileSdk` is 37, `targetSdk` 36 and `minSdk` 26.
- Nothing else: there are no API keys and no app-wide secrets. OAuth clients are
  registered at runtime per server through `POST /api/v1/apps`.

## Architecture in one paragraph

Offline-first, Room is the source of truth, unidirectional data flow from
repositories to `StateFlow` UI state. `:core:*` modules hold everything two
features share; `:feature:*` modules see only `:core:*`; `:app` wires them
together. The allowed module edges are enforced by `./gradlew alohaArchitectureCheck`
(`build-logic/convention/src/main/kotlin/social/aloha/buildlogic/ModuleRules.kt`).
`:core:model` and `:core:html` are pure Kotlin modules with no Android SDK. The
whole picture is in [docs/01-architecture.md](docs/01-architecture.md).

## Quality gates

A pull request merges only when every CI job is green:

- `detekt` and `ktlintCheck` (android_studio style, 120 columns) with zero findings;
- Android Lint with warnings as errors;
- `alohaArchitectureCheck`, which runs `alohaStringsCheck` too: every string has
  a translator comment on the line above it and is defined in one module only;
- `alohaUnitTests` and `alohaScreenshotTests`: every module's unit tests and
  Roborazzi comparison, with the Accessibility Test Framework checking each
  screenshot test's screen;
- every variant assembles, and the release APKs stay within 2 % of
  `config/size-baseline.json` (`scripts/check-apk-size.sh`); a pull request that
  grows the app on purpose moves the baseline to CI's measurement;
- REUSE compliance and the Gradle dependency verification.

Run them locally with

```sh
./gradlew detekt ktlintCheck lint alohaArchitectureCheck alohaUnitTests alohaScreenshotTests
```

Debug builds carry the pseudolocales `en-XA` (accented, longer) and `ar-XB`
(mirrored); switch the device to them to see a screen stretched and mirrored.
A weekly OSV scan (`osv.yml`) reports known vulnerabilities in the dependencies
and gates nothing.

The weekly `ui` workflow runs on emulators and gates nothing, but a change to
sign-in, sharing, shortcuts, links or the watch page should pass
`scripts/ui-flows.sh` locally: it signs the debug build in against the mock
server and drives those surfaces as other apps and the system reach them (on a
foldable emulator also the watch page in tabletop posture).

There are no baseline files. When one is unavoidable (a dependency bump that
brings findings you cannot fix in the same pull request), it is added with an
issue link, and CI fails any pull request that grows a baseline.

## Code conventions

- Kotlin only. No `!!`, no `runBlocking` outside tests, no `GlobalScope`, no
  hard-coded dispatchers, no `System.currentTimeMillis` (inject a `Clock`).
  detekt enforces these.
- No logging. There is no logging framework, and detekt forbids
  `android.util.Log` and `println`: a failure reaches the person through the UI
  state, and a developer through a test.
- `:core:*` modules use explicit API mode; public declarations carry KDoc that
  states behaviour and constraints.
- Comments say what the code does or which server behaviour it works around,
  never how the code came to be. `ponytail:` marks a deliberate ceiling and names
  its upgrade path.
- Every string lives in `strings.xml` with a comment for translators.
- Every screen ships the `@AlohaPreviews` set (light, dark, 200 % font, right to
  left, tablet), and every interactive element has a 48 dp target and a label.
- Never two consecutive blank lines in any file.
- Every source file starts with an SPDX header:

  ```kotlin
  // SPDX-FileCopyrightText: 2026 Aloha Social contributors
  // SPDX-License-Identifier: MIT
  ```

## Commits and pull requests

- [Conventional Commits](https://www.conventionalcommits.org/): `feat(timeline): …`,
  `fix(network): …`, `build: …`, `ci: …`, `docs: …`.
- Sign off and sign every commit: `git commit -s -S`. The sign-off is your
  Developer Certificate of Origin.
- AI-assisted commits carry an `Assisted-by: <tool>:<model>` trailer; CI labels
  such pull requests.
- One concern per pull request; split anything approaching a thousand changed
  lines. The pull request template's checklist is the definition of done.
