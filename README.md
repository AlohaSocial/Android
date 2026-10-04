# Aloha Social for Android

A fediverse client for Android phones, tablets and foldables. It speaks the
Mastodon client API, so it works with Mastodon, GoToSocial, Akkoma and anything
else that serves that protocol, and its primary target is
[Nextcloud Social](https://github.com/AlohaSocial/social), whose photo, video,
short-video, news and story extensions it uses where the server offers them.

The Android sibling of [Aloha Social for Apple platforms](https://github.com/AlohaSocial/Apple),
built to the same product contract and to Material Design.

**Status: in development, not released yet.** What it does today:

- Signs in to any server with the Mastodon client API, Nextcloud Social first,
  with as many accounts as wanted, and connects the Nextcloud underneath for
  push, files and account deletion.
- Reads Home, local and federated timelines, threads, profiles, hashtags, lists,
  bookmarks, favourites, direct messages and Your year, from a local cache that
  works offline.
- Six modes: Home, Photos, Video, Shorts, and News and Audio on request, with
  stories, picture-in-picture and background playback.
- Writes posts, threads, polls, scheduled posts, drafts and stories, with media,
  descriptions and edits, through an outbox that waits for a network.
- Notifies by polling and by UnifiedPush, from Mastodon's Web Push or from the
  Nextcloud's.
- Translates through the server, or on the device where the server cannot.
- Filters, mutes, blocks and reports, a moderation console for a server's
  moderators, and an optional app lock.
- Widgets, launcher shortcuts, Direct Share, "Open in Aloha", keyboard use,
  multi-window and the foldable tabletop posture.

[docs/](docs/README.md) holds the product specification and how the Android app
is built; [docs/14-status.md](docs/14-status.md) says what is not built yet.

## Building

JDK 21 and an Android SDK with platform 37. Point `local.properties` at the SDK:

```properties
sdk.dir=/path/to/Android/Sdk
```

```sh
./gradlew assembleGenericDebug          # F-Droid flavor, no Google libraries
./gradlew assembleGplayDebug            # Google Play flavor
```

## Checks

The checks CI gates on, locally:

```sh
./gradlew detekt ktlintCheck lint alohaArchitectureCheck alohaUnitTests alohaScreenshotTests
```

`alohaUnitTests` and `alohaScreenshotTests` run every module's unit tests and
screenshot comparisons; `:app` is the only module with flavours, so its own
tasks alone would leave the rest out. Screenshots are re-recorded when a change
to the UI is intended:

```sh
./gradlew recordRoborazziDebug recordRoborazziGenericDebug
```

CI also builds every variant and checks the APK size; see
[docs/12-conventions-quality.md](docs/12-conventions-quality.md).

## Licence

MIT, see [LICENSE](LICENSE). Third-party code keeps its own licence; see
[ACKNOWLEDGEMENTS.md](ACKNOWLEDGEMENTS.md) and [REUSE.toml](REUSE.toml).
