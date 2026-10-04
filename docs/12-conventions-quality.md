# 12 — Conventions and quality

How the app is tested, what CI runs and gates on, and the conventions a change follows. [CONTRIBUTING.md](../CONTRIBUTING.md) is the short version; this is the long one. Written from the build, the workflows and the scripts.

## Testing layers

### Unit tests and the mock server

Every module has JVM unit tests: JUnit 5 for plain code, JUnit 4 on Robolectric where Android is needed. `./gradlew alohaUnitTests` runs them all (`Aggregates.kt` collects every module's `test` or `test*DebugUnitTest` task); `:app`'s own `testGenericDebugUnitTest` would leave every other module out.

Server behaviour comes from a recorded corpus, never from assumptions. `:core:testing` provides:

- **`MockSocialServer`**, a MockWebServer that answers from the corpus in the shape of one `MockServerConfiguration`: Nextcloud Social with and without the root rewrite rules, Nextcloud with the Nextcloud connected, Mastodon, a core-only server whose extension routes answer 404, a rate-limited server (the first read of each path answers 429), and one that sends malformed entities. Absolute addresses in bodies and headers are rewritten to the mock's origin, every request is recorded, and a test can pin a fixture or a status to a route.
- **The fixture corpus** in `core/testing/src/main/resources/fixtures/`: `nextcloud-social-0.26.97/` (captured from the dev instance; `api/`, `plain/` and `root/` for the two address shapes, `errors/` and `writes/`) and `mastodon-social-4.8.0-alpha.3/` (instance, NodeInfo and OAuth metadata). Each file is one request and its answer, secrets redacted, listed in the folder's `index.txt`.
- **`scripts/capture-mastodon-fixtures.py`** captures the Mastodon files from `https://mastodon.social`, replacing the contact account and e-mail with fictional ones; add new names to `index.txt` by hand.
- **`LiveInstanceTest`** signs in end to end against a real Nextcloud Social when `ALOHA_LIVE_SERVER`, `ALOHA_LIVE_USER` and `ALOHA_LIVE_PASSWORD` are set, and skips otherwise. CI never sets them.

### Screenshots and accessibility

Screens are tested as screenshots with Roborazzi on Robolectric (`aloha.screenshot`): hand-written test classes render a screen in fixed states on a Pixel 7 profile and compare it with the reference image in the module's `src/test/screenshots/`. `./gradlew alohaScreenshotTests` verifies every module. When a change to the UI is intended, record again and review the new images in the pull request:

```sh
./gradlew recordRoborazziDebug recordRoborazziGenericDebug
```

Before capturing, the tests run the Accessibility Test Framework over the screen (`enableAccessibilityChecks()`, `tryPerformAccessibilityChecks()`), so a missing label, a small touch target or low contrast fails the test. Some tests render in the pseudolocales and right to left. `SemanticContrastTest` checks that boost, favourite and bookmark stay distinct.

`@AlohaPreviews` (light, dark, 200 % font, right to left, tablet) is the preview set for Android Studio.

### UI flows on a device

Maestro flows in `.maestro/` run against a real build on an emulator:

- `smoke.yaml` installs a release build, accepts the terms, restarts and checks that the state survived R8.
- `signed-in/` drives the debug build signed in to the mock server: sign-in, text shared from another app, launcher shortcuts, "Open in Aloha", a thread, and the watch page. `system-ui.yaml` is a subflow that dismisses an "isn't responding" dialog.

`scripts/ui-flows.sh` runs the signed-in set locally: it serves `MockSocialServer` on the device through `adb reverse` (the `ServeForDevice` test), installs the generic debug build, runs the flows, checks the published shortcuts and Direct Share target, opens a link in a freeform window, and on a foldable checks the watch page in tabletop posture.

```sh
./gradlew :app:assembleGenericDebug && scripts/ui-flows.sh
```

A change to sign-in, sharing, shortcuts, links or the watch page should pass it before review.

### Benchmarks

`:benchmark` holds `StartupBenchmark`, `TimelineScrollBenchmark` and the baseline profile generator. The budgets, measured on a Pixel 6a class phone, are TTID ≤ 800 ms and TTFD ≤ 1.2 s at the median, and a frame overrun of at most 0 ms at P90 and 8 ms at P99 while scrolling Home. The Apple specification's 400 ms to the first timeline row on an iPhone 15 ([00-overview.md](00-overview.md) §8) does not apply. Its Android counterpart is TTFD, which the app reports once the first row is drawn, budgeted at 1.2 s on a much slower phone; TTID, the first frame, is budgeted at 800 ms. How to run them, and the last results, are in [benchmarking.md](benchmarking.md); the medians are kept in `config/benchmark-baseline.json` and compared by hand. The baseline and startup profiles are checked in under `app/src/main/generated/baselineProfiles/`.

## CI

Every workflow starts with no permissions and grants each job what it needs, pins actions by commit, uses Temurin JDK 21, and installs `platforms;android-37.0` and `build-tools;37.0.0`.

| Workflow | When | What it runs | Can fail a pull request |
|---|---|---|---|
| `checks.yml` | Push to `main`, pull requests | `static`: `detekt ktlintCheck lint alohaArchitectureCheck`, and on pull requests `scripts/check-baseline-growth.sh`; `unit`: `alohaUnitTests`; `screenshot`: `alohaScreenshotTests`, uploading the diffs when it fails | Yes |
| `build.yml` | Push to `main`, pull requests | Assembles debug and release of both flavours and the benchmark module, then `scripts/check-apk-size.sh` | Yes |
| `reuse.yml` | Push to `main`, pull requests | REUSE compliance | Yes |
| `ai-trailer.yml` | Pull requests | Labels the pull request "AI assisted" when a commit carries `Assisted-by:`, and fails when a `Signed-off-by:` names a bot or an AI tool: only a person certifies the DCO | Yes |
| `pr-apk.yml` | Pull requests | A generic debug APK, linked in a comment, kept five days | No |
| `ui.yml` | Mondays, or by hand | Release builds and the Maestro smoke flow on an emulator, the benchmarks as a smoke test, and `scripts/ui-flows.sh` on a foldable emulator | No |
| `osv.yml` | Mondays, or by hand | OSV-Scanner over the dependencies | No |
| `supply-chain.yml` | Push to `main` | Submits the Gradle dependency graph, so dependency alerts cover it | No |
| `verification-metadata.yml` | By hand | Regenerates `gradle/verification-metadata.xml` and the keyring on Linux, for review | No |

Every artifact is kept five days.

### Static analysis

- **detekt** with zero findings (`config/detekt/detekt.yml`): no `!!`, no `runBlocking` outside tests, no `GlobalScope`, no `System.currentTimeMillis` (inject a `Clock`), no `allowMainThreadQueries`, no WebView, no `android.util.Log` or `println`, no Jetpack Security crypto (secrets go to the vault), no RxJava, Glide or `AsyncTask`; a `TODO` carries an issue link.
- **ktlint**, android_studio style, 120 columns (`.editorconfig`).
- **Android Lint** with warnings as errors, and Kotlin compiler warnings as errors. Version-drift checks are off: that is Renovate's job.
- **`alohaArchitectureCheck`**: the module graph ([01-architecture.md](01-architecture.md)). It also runs **`alohaStringsCheck`**: every translatable string and plural has a comment for translators on the line directly above it, and no string name is defined in two modules.
- **No baselines.** None exists today; `check-baseline-growth.sh` fails a pull request that grows one, and one that is unavoidable comes with an issue link.

### APK size

`scripts/check-apk-size.sh` fails the build when a release APK grows more than 2 % beyond its entry in `config/size-baseline.json`. A pull request that grows the app on purpose moves the baseline to what CI measured, in the same pull request.

### Dependencies

- Every dependency and plugin is in `gradle/libs.versions.toml`, verified against Maven Central or Google Maven before it is added.
- Gradle dependency verification checks the checksum and signature of every artifact (`gradle/verification-metadata.xml`); after a dependency change, regenerate it with the `verification-metadata` workflow.
- `gradle/security-floors.settings.gradle.kts` raises vulnerable versions of the build tools' own transitive dependencies to a fixed floor.
- Renovate proposes updates weekly, grouping the toolchain and the Compose BOM.

## Strings and locales

- Every user-facing string is in a module's `strings.xml`, with a comment for translators directly above it saying where it shows and what each placeholder holds. Apostrophes are typographic (’).
- English is the only translation so far; `app/src/main/res/xml/locales_config.xml` lists it, and gains each language as it arrives.
- Debug builds of every module carry the pseudolocales `en-XA` (accented and longer) and `ar-XB` (mirrored), for screenshots and for a look on a device.

## Logging

The app does not log. There is no logging framework, and detekt forbids `android.util.Log` and `println` in app code. A failure reaches the person through the screen's state (a `Trouble`, an error with Retry) and a developer through a test against the fixture corpus; nothing is written to logcat to be read later, and no crash reporter or analytics library is included in either flavour.

## Code conventions

- Kotlin only, with explicit API mode in `:core:*` and KDoc on every public declaration stating behaviour and constraints.
- Comments say what the code does or which server behaviour it works around, never how the code came to be. `ponytail:` marks a deliberate ceiling and names its upgrade path.
- Every interactive element has a 48 dp target and a label; every new screen has a heading and a `paneTitle`.
- Every source file starts with an SPDX header; Markdown, fixtures, screenshots and schemas are covered by `REUSE.toml`.
- Never two consecutive blank lines in any file.

## Commits and pull requests

- [Conventional Commits](https://www.conventionalcommits.org/): `feat(timeline): …`, `fix(network): …`, `build: …`, `ci: …`, `docs: …`; one concern per commit.
- Every commit is signed off and signed: `git commit -s -S`. The sign-off is the author's Developer Certificate of Origin, so it names a person.
- AI-assisted commits carry an `Assisted-by: <tool>:<model>` trailer, which labels the pull request; no `Co-Authored-By` trailer and no session links.
- One concern per pull request; split anything approaching a thousand changed lines. The pull request template's checklist is the definition of done: tests, green checks, intended screenshots, accessibility, translator comments, a line for every new exported component, permission or dependency, and `docs/` updated where behaviour changed.
