# 04 — Data model

What the app keeps on the device, where, for how long, and what it never keeps. Written from the code: the databases are in `:core:database`, the preferences and the vault in `:core:datastore`, and the repositories that write them in `:core:data`.

## Room databases

Three databases, three files, because they are treated differently: one is never thrown away, one is disposable, and one must never leave the device.

| Database | File | Entities | Treated as |
|---|---|---|---|
| `AccountsDatabase` | `accounts.db` | `AccountEntity` (handle, API base, capabilities, order, the re-authentication and Nextcloud flags; never a secret), `ClientRegistrationEntity` (client id and scopes per host; the secret is in the vault), `RaisedNotificationEntity` (which notifications have been shown) | Durable: versioned, migrated, never destroyed |
| `CacheDatabase` | `cache.db` | `CachedStatusEntity` (the post as the server sent it, with its kind and plain text for filtering), `TimelineEntryEntity` (a timeline's rows, gaps included), `FilterEntity`, `TimelinePositionEntity` (where each timeline was left), `WatchPositionEntity` | Disposable: everything in it can be fetched again |
| `OutboxDatabase` | `outbox.db` | `OutboxEntity` (a draft or a post waiting to be sent, with its state and last error) | Durable, but kept out of backups: drafts and direct messages are private |

Every database exports its schema to `core/database/schemas/`, one JSON file per version. A schema change bumps the version and adds an `AutoMigration`; there are no hand-written migrations yet. `AccountsDatabase` and `CacheDatabase` are at version 2, `OutboxDatabase` at 1. `CacheDatabase` alone is also allowed to fall back to a destructive migration, because losing it costs a refresh and nothing else.

## Preferences and files

| Store | File | Holds | Scope |
|---|---|---|---|
| `AppPreferences`, `ReadingPreferences`, `ModePreferences`, `AppLockPreferences` | `files/datastore/app.preferences_pb`, one Preferences DataStore read through four classes | The active account, swipe actions, composer defaults, theme, reading and media choices, modes, quiet hours, sync on Wi-Fi only, which accounts push, the app lock and its timeout, the accepted terms version | App-wide |
| `AccountSettingsStore` | `files/datastore/account_settings.json` | Per account: boosts and replies shown, the timeline sources per mode, recent hashtags, searches and file paths, tag groups, the "accounts I don't follow" switch, poll frequency | Per account; removed with the account |
| `WidgetFeedStore` | `noBackupFilesDir/widget_feed.json` | What the widgets show: unread counts and the newest mentions | Per account |
| `TokenVault` | `files/vault/secrets.bin`, encrypted by `KeystoreCipher` | Tokens, app passwords, client secrets, the sign-in in flight; see [03-auth-and-accounts.md](03-auth-and-accounts.md) | Per account and host |
| `UserTrustStore`, client certificate aliases | `noBackupFilesDir/tls/` | Certificates trusted for one host, and the KeyChain alias chosen per host | Per host |
| Upload copies | `files/uploads/` | The copy of each picture or video a waiting post attaches | Per outbox entry |
| Image and HTTP caches | `cacheDir/images` (Coil, 512 MB), `cacheDir/http` (OkHttp, 64 MB) | Pictures and responses | Disposable; the system may clear them |

A corrupt `account_settings.json` is replaced with defaults, and an undecryptable vault starts empty and marks every account as needing to sign in again.

## Timelines and gaps

A timeline is the rows of `timeline_entry` for one account and one timeline key, ordered by a descending position; each row points at a `status`. A **gap** is a row with `isGap` set, standing where the server may hold posts the cache does not.

`TimelineMerge` (`core/data/.../timeline/TimelineMerge.kt`) decides every change, as pure logic with its own tests:

- A refresh asks with `since_id` from the newest cached row ([02-server-api.md](02-server-api.md) explains why). A full page leaves a gap under the new rows, because more may lie between them and the cache; a short page joins.
- Scrolling down asks with `max_id` from the oldest row and appends.
- A cold load replaces the timeline and closes every gap.
- Filling a gap asks for what is older than the row above it. The gap closes when the page is empty, short, or reaches a row already cached below it; a full page moves the gap down under what it brought.
- A gap's id is `gap:` plus the id of the row above it, so filling the same gap twice is harmless.

On screen a gap is a "Load more" row (`GapRow`) in the timeline, the photo and video grids, and a profile's posts.

## Sweeping

Nothing grows without bound:

- Writing a timeline keeps its newest 500 rows for Home and 200 for every other timeline (`CachePolicy`).
- At each launch, `CacheSweeper` deletes cached posts that no timeline row has pointed at for seven days, at most 2,000 per run, and the outbox sweep deletes upload copies no entry refers to that have been idle for a day. An extra window opened beside the app skips both.
- Signing out an account deletes its cache rows, settings, outbox entries, upload copies, widget content and raised notifications; see [03-auth-and-accounts.md](03-auth-and-accounts.md).

**Settings, Storage** shows the size of the image and HTTP caches and clears them (`DeviceCaches`). It does not touch `cache.db`, which the sweep keeps small; the same two caches are also cleared when the last account signs out.

## Backups

`android:allowBackup` is on, with `dataExtractionRules` (Android 12 and later) and `fullBackupContent` (before) excluding the same set from cloud backup and device transfer:

- `files/vault`: the secrets;
- `cache.db`: disposable;
- `outbox.db` and `files/uploads`: drafts, waiting posts and direct messages, and what they attach;
- WorkManager's database, which holds push keys and tokens while their work waits.

Everything else in `files/` and `databases/` is backed up: `accounts.db` and the two DataStore files, which include the recent searches and the push endpoints per instance. A restored device therefore has the accounts list and the settings, and every account asks to sign in again, because the vault did not travel. Files in `noBackupFilesDir` (widget content, trusted certificates) never leave the device.

## Never persisted

- A translation, by the server or the device; asking again is cheap.
- The decrypted vault, which lives in memory for the life of the process.
- In-memory lookups that a refresh rebuilds: the accounts seen on recently opened posts, which authors the reader follows (for the "accounts I don't follow" switch), the announcements read on this device, and which timelines are on screen or in flight.
- An edit of a sent post is never kept as a draft: leaving it discards it.
