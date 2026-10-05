# AGENTS.md

Guidance for coding agents working in this repository. Humans: see
[CONTRIBUTING.md](CONTRIBUTING.md); everything there applies to agents too.

## Before changing code

- Read the issue the change belongs to and its acceptance criteria, and the
  checklist of the pull request template.
- Run `./gradlew detekt ktlintCheck lint alohaArchitectureCheck alohaUnitTests alohaScreenshotTests`
  before and after; a change is finished when it is green.
- After changing a constructor or any non-private signature, compile every test
  source set (`./gradlew compileDebugUnitTestKotlin :app:compileGenericDebugUnitTestKotlin`),
  not only the module's own: tests elsewhere build the class by hand.
- [docs/12-conventions-quality.md](docs/12-conventions-quality.md) describes the
  test layers and every CI job.

## Rules

- Follow the module graph (`ModuleRules.kt`); add a row there in the same change
  when adding a module.
- Verify every new dependency and version against Maven Central or Google Maven
  before using it, and add it to `gradle/libs.versions.toml`, never inline.
- Never add a baseline entry to make a check pass.
- Server behaviour comes from the fixture corpus and the live dev instance, not
  from assumptions; name the server behaviour in a comment when code works around it.
- Commits use Conventional Commits, `git commit -s -S`, and an
  `Assisted-by: <tool>:<model>` trailer. No `Co-Authored-By` trailer, no session links.
- Never leave two consecutive blank lines in a file.

## Code that tends to go wrong

- Data from anywhere but the reader's own server keeps that server's ids apart:
  every id a post carries (its own, its account's, its poll's, the one it
  answers, its quote) is namespaced or dropped, never sent to the reader's server.
- Acting for another account, or on another server's copy of a post, sets the
  state wanted (boosted, favourited, bookmarked) and skips it when it holds; it
  never flips a state the app cannot see.
- A request that failed answers `null`, not an empty list or set. Nothing is
  marked done, dropped or filtered on an answer that may have been a failure.
- A change to stored settings is made inside the store's `update`, from what is
  stored then, not from a `StateFlow` snapshot; a change across several stores
  holds a `Mutex`.
- An address found by discovery (host-meta, redirects, links) passes the same
  checks as one typed: https, a host name, no IP address or `localhost`.
- Status rows are built in a view model through `StatusRowCache`, never with a
  `StatusRowMapper` or `RichTextCache` made in a composable; a row the screen
  shows is one its actions can find.
- `remember` and `pointerInput` are keyed on stable ids, not on data objects
  that change while in use; per-item state (scroll positions, drags) survives
  the list around it changing.
- An object put into a `CompositionLocal` is made once and reads its values
  through `State`, so a value changing recomposes only what reads it.
- Every animation goes through `motion()` or checks `rememberReducedMotion()`.
- Every long press, drag and swipe has a custom accessibility action that does
  the same; a handle carries no label of its own; a live region says a result,
  not every keystroke; a card shown only for its looks has its semantics cleared.
- A screen that replaces another removes the code paths only the old one used.
