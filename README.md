# Aloha Social for Android

A fediverse client for Android phones, tablets and foldables. It speaks the
Mastodon client API, so it works with Mastodon, GoToSocial, Akkoma and anything
else that serves that protocol, and its primary target is
[Nextcloud Social](https://github.com/AlohaSocial/social), whose photo, video,
short-video, news and story extensions it uses where the server offers them.

The Android sibling of [Aloha Social for Apple platforms](https://github.com/AlohaSocial/Apple),
built to the same product contract and to Material Design.

**Status: foundation.** The project builds, every quality gate runs, and the app
is an empty navigation shell. `docs/` holds the product specification.

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

Everything CI runs, locally:

```sh
./gradlew detekt ktlintCheck lint alohaArchitectureCheck testGenericDebugUnitTest verifyRoborazziGenericDebug
```

Screenshots are re-recorded with `./gradlew recordRoborazziGenericDebug` when a
change to the UI is intended.

## Licence

MIT, see [LICENSE](LICENSE). Third-party code keeps its own licence; see
[ACKNOWLEDGEMENTS.md](ACKNOWLEDGEMENTS.md) and [REUSE.toml](REUSE.toml).
