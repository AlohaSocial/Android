# Contributing to Aloha Social for Android

## Requirements

- JDK 21 and an Android SDK with platform 37 and build-tools 36 or newer.
- Nothing else: there are no API keys and no app-wide secrets. OAuth clients are
  registered at runtime per server through `POST /api/v1/apps`.

## Architecture in one paragraph

Offline-first, Room is the source of truth, unidirectional data flow from
repositories to `StateFlow` UI state. `:core:*` modules hold everything two
features share; `:feature:*` modules see only `:core:*`; `:app` wires them
together. The allowed module edges are enforced by `./gradlew alohaArchitectureCheck`
(`build-logic/convention/src/main/kotlin/social/aloha/buildlogic/ModuleRules.kt`).
`:core:model` and `:core:html` are pure Kotlin modules with no Android SDK.

## Quality gates

A pull request merges only when every CI job is green:

- `detekt` and `ktlintCheck` (android_studio style, 120 columns) with zero findings;
- Android Lint with warnings as errors;
- `alohaArchitectureCheck`;
- unit tests and the Roborazzi screenshot comparison;
- REUSE compliance and the Gradle dependency verification.

There are no baseline files. When one is unavoidable (a dependency bump that
brings findings you cannot fix in the same pull request), it is added with an
issue link, and CI fails any pull request that grows a baseline.

## Code conventions

- Kotlin only. No `!!`, no `runBlocking` outside tests, no `GlobalScope`, no
  `android.util.Log` (Timber), no hard-coded dispatchers, no `System.currentTimeMillis`
  (inject a `Clock`). detekt enforces these.
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
