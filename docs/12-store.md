# 12 — Store listing, Data safety and content rating

What the Play Console and F-Droid are told, and why each answer is true of the build. The listing text itself is in [`fastlane/metadata/android/en-US`](../fastlane/metadata/android/en-US): F-Droid reads it from there, and `fastlane supply` uploads the same text to Play.

## Flavours

`generic` (F-Droid) and `gplay` (Google Play) are the same code today: neither carries a library the other does not. Every answer below holds for both. The day `gplay` gains Google Play services (a push distributor, say), its Data safety answers change with it.

## Data safety (Play Console)

| Question | Answer | Why |
|---|---|---|
| Does the app collect or share any of the required user data types? | Yes | Posts, messages and pictures the reader writes go to the server they signed in to. |
| Is all collected data encrypted in transit? | Yes | Release builds refuse `http://`; every server is reached over TLS. |
| Can users request that their data be deleted? | Yes | Settings, Delete account, which deletes the account on Nextcloud Social and opens the server’s own page on Mastodon. |
| Data collected: Personal info, name, email, user IDs | Yes: user IDs (the account handle) | Shown to and kept by the reader’s own server; required to sign in. |
| Data collected: Messages, other in-app messages | Yes | Direct messages go to the reader’s server. |
| Data collected: Photos and videos | Yes | Attachments go to the reader’s server. |
| Data collected: App activity, other user-generated content | Yes | Posts, replies, favourites and boosts go to the reader’s server. |
| Data shared with third parties | No | The developer receives nothing. The reader’s server is the service they chose and sign in to, not a third party of the developer’s; a translation goes to the server, which hands it to the translation service it uses. |
| Is it processed ephemerally? | No | The server keeps what the reader posts. |
| Is collection required or optional? | Required | A fediverse client cannot post without sending the post. |
| Purposes | App functionality, account management | Nothing else: no analytics, advertising or personalisation by the developer. |
| Location, contacts, calendar, health, financial info, device or other IDs, app info and performance (crash logs, diagnostics) | Not collected | No such permission, no crash reporting or analytics library. |

## Content rating (IARC questionnaire)

- Category: Social or communication.
- The app lets users interact and exchange content (posts, messages, pictures, video): **yes**.
- It shares the user’s location with others: **no**; the app has no location permission. A post may carry a place the writer chose, as text.
- Digital purchases: **no**. Gambling: **no**.
- The content comes from other users across the fediverse, unfiltered by the developer: answer the user-generated content questions accordingly, which places the app at the rating for unrestricted web content.

## User-generated content policy

Each requirement, and where the app meets it:

| Requirement | Where |
|---|---|
| Terms that forbid objectionable content and abuse, accepted before any content shows | The terms on first launch (with Decline, which leaves the app), again in Settings, About |
| Reporting content and users | A post’s menu, Report; a profile’s menu, Report; the report goes to the server’s moderators with the posts chosen |
| Blocking users | A profile’s menu, Block; Settings, Muted and blocked lists them and takes a block back |
| Blocking whole servers | A profile’s menu, block the server; Settings, Muted and blocked, Servers |
| Filtering objectionable content | Filters (account sheet and Settings): keywords, where, warn or hide, expiry; media marked sensitive shown as the account chose |
| Moderation of the service | The reader’s server moderates; About this server shows its rules and moderated servers |
| Contact | Settings, About, Report a problem (the issue tracker). The developer e-mail is the Play Console account’s. |

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Reaching the server; checking whether a network is up and whether it is mobile data |
| `POST_NOTIFICATIONS` | Notifications, asked once an account exists |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Sending the outbox and uploads while the app is closed |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Audio and video that keep playing in the background |
| `USE_BIOMETRIC` | The app lock |
| `WRITE_EXTERNAL_STORAGE` (Android 9 and older only) | Saving a picture to the shared Pictures folder |

## F-Droid

- `generic` is the F-Droid build: no Google library, no proprietary dependency.
- Reproducible builds are what F-Droid checks, building from the tag and comparing with the signed APK; whether this build is byte for byte reproducible is checked when the first release is cut (two clean builds of the tag, compared).
- Metadata: `fastlane/metadata/android/en-US` (title, short and full description, a changelog per `versionCode`). Screenshots and the icon go under `images/` when the release is cut.
