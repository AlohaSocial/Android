# 01 — Architecture

How the Android app is put together: its modules and the rules between them, the build conventions, where state lives and how it reaches the screen, and how errors and the offline case are handled. Written from the code; class and file names are the ones in the repository.

## Modules

`settings.gradle.kts` includes three single modules, `:app`, `:widget` and `:benchmark`, plus a list of `:core:*` and `:feature:*` modules.

| Module | Holds |
|---|---|
| `:app` | `MainActivity`, `WindowActivity`, `OAuthRedirectActivity`, the application class, the navigation graph (`AlohaApp.kt`), the share target and shortcuts, the app lock, device translation; wires every feature together |
| `:widget` | The four Glance home-screen widgets and their configure activity |
| `:benchmark` | Macrobenchmarks and the baseline profile generator; see [benchmarking.md](benchmarking.md) |
| `:core:model` | Pure Kotlin entities and value types (`Status`, `Account`, `ServerCapabilities`, `FeedMode`), no Android SDK |
| `:core:html` | Pure Kotlin: post HTML to annotated text |
| `:core:network` | OkHttp and kotlinx.serialization: `ApiClient`, the endpoint objects, DTOs, server probing, OAuth, TLS trust |
| `:core:database` | The three Room databases; see [04-data-model.md](04-data-model.md) |
| `:core:datastore` | DataStore preferences, the per-account settings file and the encrypted `TokenVault` |
| `:core:data` | Repositories over network, database and datastore: accounts, sign-in, timelines, compose and the outbox, notifications, moderation, the Nextcloud connection |
| `:core:sync` | WorkManager workers, polling (`SyncEngine`), push, raised notifications, upload notifications |
| `:core:media` | Coil image loading and the shared Media3 player session service |
| `:core:designsystem` | Theme, colours, type, `@AlohaPreviews` |
| `:core:ui` | Shared composables: the status card, `TroubleStrip`, settings sections |
| `:core:navigation` | Navigation 3 keys (`TopLevelKeys.kt`, `DetailKeys.kt`), link routing, app intents |
| `:core:testing` | `MockSocialServer` and the fixture corpus, for every module's tests; see [12-conventions-quality.md](12-conventions-quality.md) |
| `:feature:*` | One screen family each: signin, timeline, thread, profile, notifications, composer, search, explore, lists, settings, photos, video, shorts, stories, mediaviewer, audio, safety, moderation, hashtags, saved, conversations |

### The module rules

`build-logic/convention/src/main/kotlin/social/aloha/buildlogic/ModuleRules.kt` is the allowed graph, and `./gradlew alohaArchitectureCheck` fails on any project dependency it does not allow:

- `:app` may depend on anything; `:benchmark` only on `:app`.
- Nothing depends on a `:feature:*` module, and a feature depends only on `:core:*`. Two features that need the same thing get it from a core module.
- `:widget` may use `:core:data`, `:core:navigation`, `:core:designsystem` and `:core:model`.
- Each core module has a row naming what it may use. `:core:model` and `:core:designsystem` use nothing; `:core:data` uses network, database, datastore, html and model; `:core:sync` uses data, navigation and model. A module without a row fails the check, so adding a module means adding its row.
- Any module's tests may use `:core:testing`.

### Placeholder modules

Four modules hold nothing but a `build.gradle.kts`: `:core:nextcloud`, `:core:platform`, `:feature:news` and `:feature:share`. They come from the first module plan and were never filled, because the code found a home elsewhere:

- The Nextcloud connection is in `:core:data` (`core/data/.../nextcloud/NextcloudConnection.kt`) and its routes in `:core:network` (`NextcloudEndpoints.kt`).
- News is a mode of `:feature:timeline` (`FeedMode.News`, mapped in `AlohaApp.kt`), and Explore's News tab is in `:feature:explore`.
- The share target, Direct Share and "Open in Aloha" are in `:app` (`SharedContent.kt`, `OutsideRequest.kt`, `AccountShortcuts.kt`).
- `:core:platform` is the one library module with the product flavours applied, the place flavour-specific code would go; nothing is flavour-specific yet. `:app` depends on it.

`ModuleRules.kt` keeps rows for `:core:nextcloud` and `:core:platform`. Removing the four is a build change of its own.

## Build conventions

The convention plugins in `build-logic/convention` keep every module's build file a few lines long:

| Plugin | Sets up |
|---|---|
| `aloha.android.application` | `:app`: SDK levels, flavours, R8 with resource shrinking for release, static analysis, `alohaUnitTests` |
| `aloha.android.library` | Android library modules: SDK levels, JUnit 5 with the vintage engine for Robolectric, static analysis, `alohaUnitTests` |
| `aloha.android.compose` | Compose with the BOM and Material 3 |
| `aloha.android.hilt` | KSP and Hilt |
| `aloha.android.feature` | library + compose + hilt, plus `:core:designsystem`, `:core:ui`, `:core:navigation` and the Hilt and lifecycle Navigation 3 integrations |
| `aloha.android.room` | Room with KSP and the schema directory `schemas/` |
| `aloha.jvm.library` | Pure Kotlin modules (`:core:model`, `:core:html`), Java 17, JUnit 5 |
| `aloha.screenshot` | Roborazzi on Robolectric with the accessibility checks; registers `alohaScreenshotTests` |
| `aloha.architecture` | Root project only: `alohaArchitectureCheck` and `alohaStringsCheck` |

`social/aloha/buildlogic/Android.kt` applies what every Android module shares: namespace `social.aloha.<path>`, `compileSdk` 37 and `minSdk` 26 from the version catalog, Lint with warnings as errors, Kotlin warnings as errors, explicit API mode for `:core:*`, and pseudolocales in debug builds. `targetSdk` is 36.

## Flavours

`Flavors.kt` defines one dimension, `distribution`, with `generic` (F-Droid, no Google library) and `gplay` (Google Play). The two are the same code today: no flavour source set, no flavour-only dependency, no check of the flavour at run time. Push is UnifiedPush and device translation uses the platform API in both. A Google-only feature, such as an FCM push distributor, would live in `gplay` alone, which would change its Data safety answers ([12-store.md](12-store.md)).

## Dependency injection

Hilt throughout. `AlohaApplication` is the `@HiltAndroidApp`, and it also provides Coil's image loader and WorkManager's `HiltWorkerFactory`. The activities, the notification action receiver and both playback services are `@AndroidEntryPoint`; the workers are `@HiltWorker`; the widgets reach the graph through `WidgetEntryPoint`. Every module installs into `SingletonComponent`. Settings sections are contributed by the features that own them as a multibound `Set<SettingsSection>`, so `:feature:settings` does not depend on them.

## State

Room is the source of truth for what the server said, and DataStore for what the person chose:

- **Room**: `AccountsDatabase` (`accounts.db`: accounts, client registrations, raised notifications), `CacheDatabase` (`cache.db`: statuses, timeline rows and gaps, filters, positions) and `OutboxDatabase` (`outbox.db`: drafts and posts not yet sent).
- **DataStore**: one preferences file read through `AppPreferences`, `ReadingPreferences`, `ModePreferences` and `AppLockPreferences`; `AccountSettingsStore` for settings per account; `TokenVault`, encrypted with `KeystoreCipher`, for every secret.

What is kept where, and what never is, is in [04-data-model.md](04-data-model.md).

## Data flow

Unidirectional. A repository in `:core:data` exposes a `Flow` over Room and DataStore and writes what the network answers back into them; a `@HiltViewModel` combines those flows into one immutable UI state and exposes it as a `StateFlow`, typically with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)`; the screen collects it and sends events back as plain function calls. `TimelineViewModel` is the typical case: assisted injection with the feed it shows, and a `TimelineUiState` holding rows, the refreshing and end flags, and a `Trouble`. The screen never waits for the network to show something: it paints the cache, and the refresh updates it.

## Navigation

Navigation 3 (`androidx.navigation3`) in `AlohaApp.kt`: one back stack of serializable keys from `:core:navigation` (`HomeKey`, the modes, `NotificationsKey`, `ProfileKey` at the top; some three dozen detail keys such as `ThreadKey`). Choosing a destination in the navigation clears the stack and pushes its key.

- `NavigationSuiteScaffold` turns the destinations into a bar, rail or drawer by window size.
- `NavDisplay` uses the list-detail scene strategy from the Material 3 adaptive library: top-level screens are list panes, and threads, profiles, hashtags and settings sections are detail panes, so a wide window shows both side by side and a phone one at a time.
- Each entry keeps its own saved state and `ViewModelStore`.

Deep links, shortcuts and shares arrive as an `OutsideRequest` that `MainActivity` turns into keys; see [09-platform-integrations.md](09-platform-integrations.md).

## Errors and offline

A repository call answers an `Answer<T>`: `Got(value)` or `Missed(error)`, where the error is the network layer's `ApiError` (described in [02-server-api.md](02-server-api.md)), so an expected failure is a value, not an exception.

For the screen, an error collapses to a `Trouble`: `Offline` (no transport), `RateLimited` (429) or `Server` (everything else). A list that has cached content keeps showing it and adds a `TroubleStrip` (`core/ui/.../TroubleStrip.kt`): an inline strip announced politely to a screen reader, never a blocking overlay. Each feature words the strip for what it was doing. A 401 never deletes anything: the account is marked as needing to sign in again and keeps its cache; see [03-auth-and-accounts.md](03-auth-and-accounts.md).

Writes made offline wait in the outbox and are sent by a WorkManager worker once a network is back; a favourite, boost, bookmark or pin is written to the cache first and put back if the server refuses (`StatusInteractions`).

## Logging

`AlohaApplication` plants one Timber tree: `DebugTree`, to logcat, in a debug build; `LogBuffer` (`:core:data`, `diagnostics`) in a release build, a singleton ring buffer of the last 500 lines that redacts each line before keeping it. `Diagnostics` reads it for Settings, About, Share diagnostics, together with the build (`AppBuild`, provided by `:app` from `BuildConfig`), the device, each account's server software and the last process exits.

Every Android module can log; `:core:model` and `:core:html` are pure Kotlin and do not. Tags come from `LogArea` in `:core:model`. The request lines and dropped rows come from `RequestExecutor`, which every request goes through, so the OAuth flow, the server probe and each account's client log alike. Image and media loads share the HTTP client but not the executor, and are not logged: their addresses carry names. The rules for what a line may carry are in [12-conventions-quality.md](12-conventions-quality.md).
