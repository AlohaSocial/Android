# 14 — Status

What is built, what is in progress, and what is not built or deliberately different from the specification. Kept by hand: a pull request that builds one of the items below takes it off this page.

## Built

Each phase was one pull request into `main`.

| Phase | Pull request | Merged | What it built |
|---|---|---|---|
| 1 | [#1](https://github.com/AlohaSocial/Android/pull/1) | 2026-09-29 | Finding the API base wherever Nextcloud Social sits, OAuth sign-in with PKCE, the token vault, capability detection |
| 2 | [#2](https://github.com/AlohaSocial/Android/pull/2) | 2026-09-30 | Home from a local cache, threads, profiles, hashtags, account switching, side-by-side panes and the drawer on wide windows, the post HTML parser |
| 3 | [#9](https://github.com/AlohaSocial/Android/pull/9) | 2026-09-30 | The composer: replies, threads, media, polls, scheduled posts, drafts, the offline outbox, edit and redraft; reactions, reports, profile editing, the share target |
| 4 | [#11](https://github.com/AlohaSocial/Android/pull/11) | 2026-09-30 | Polling and background refresh, notifications, UnifiedPush from Mastodon and from the connected Nextcloud, the widgets |
| 5 | [#12](https://github.com/AlohaSocial/Android/pull/12) | 2026-10-01 | Photos, Video, Shorts, Audio and News, the media viewer, stories on Photos, video links opening in their own app |
| 6 | [#14](https://github.com/AlohaSocial/Android/pull/14) | 2026-10-01 | Search, Explore, lists, followed hashtags and tag groups, bookmarks, favourites, direct messages, the archive, filters, announcements, interests |
| 7 | [#15](https://github.com/AlohaSocial/Android/pull/15) | 2026-10-01 | Launcher shortcuts and Direct Share, "Open in Aloha", the keyboard, drag and drop, windows, the foldable watch page, push status and account deletion |
| 8 | [#16](https://github.com/AlohaSocial/Android/pull/16) | 2026-10-02 | Translation by the server, else on the device |
| 9 | [#17](https://github.com/AlohaSocial/Android/pull/17) | 2026-10-04 | Onboarding and terms, Settings completed, safety management, the app lock, localisation and accessibility passes, store material, performance, the moderation console, UI flow tests |
| 10 | [#18](https://github.com/AlohaSocial/Android/pull/18) | 2026-10-04 | Notification digests at chosen times, a "You're caught up" line on Home and in Notifications, Mute conversation from a mention's notification, Reading's Show numbers switch covering every popularity number |
| 11 | [#22](https://github.com/AlohaSocial/Android/pull/22) | 2026-10-04 | A swipe on a post that ticks once where it counts, tabs that swipe, tab and chip rows that stay pinned |

Between the phases: CI artifact retention ([#3](https://github.com/AlohaSocial/Android/pull/3)), the device benchmark and baseline profiles ([#4](https://github.com/AlohaSocial/Android/pull/4)), looking an account up by its id ([#10](https://github.com/AlohaSocial/Android/pull/10)) and who favourited and boosted a post ([#13](https://github.com/AlohaSocial/Android/pull/13)).

## In progress

Phase 12, logging and diagnostics: Timber with area tags, logcat in debug builds and a redacted in-memory buffer of the last 500 lines in release builds, request lines and dropped rows from the one request executor, every silent caught failure logged, Settings, About, Share diagnostics, and the R8 mappings attached to each GitHub release. Not merged yet.

## Not built

| What | Where the specification asks for it | Notes |
|---|---|---|
| A first-frame poster for a video the server did not describe | [06-media-modes.md](06-media-modes.md) §4 | Without ffmpeg on the server, video `meta` is empty; the client does not draw a frame itself |
| Story stickers | [07-composer.md](07-composer.md) | A story is a picture, a video or a text card |
| The story player from a profile | [06-media-modes.md](06-media-modes.md) | A profile's Stories tab lists the stories and opens each on the web; the player is reachable from Photos only |
| Push through the Nextcloud push proxy (FCM) | [08-notifications-sync.md](08-notifications-sync.md) §2 | Push is UnifiedPush in both flavours. FCM would be `gplay` only and needs the push proxy to allow the app |
| Encrypted direct messages bound to the app lock | [11-safety-privacy-appstore.md](11-safety-privacy-appstore.md) | The app lock guards the screen, not the data |
| Streaming | [08-notifications-sync.md](08-notifications-sync.md) §6 | The streaming address is detected and unused; polling and push cover it |
| Rewrite, alt-text drafts, summaries | [10-intelligence.md](10-intelligence.md) | Translation is the only AI-related feature |
| A cross-account merged timeline | [00-overview.md](00-overview.md) §6 | Deferred past 1.0 by the specification itself |
| Handoff and Spotlight counterparts | [00-overview.md](00-overview.md) §5 | No Android equivalent is planned yet |
| A WebDAV browser for Nextcloud files | [07-composer.md](07-composer.md) §6 | The picker is a path field with recent paths |
| The reproducible-build check | [12-store.md](12-store.md) | Two clean builds of the first release tag, compared, when it is cut; nothing in CI checks it today |
| Translations of the app | [12-conventions-quality.md](12-conventions-quality.md) | English only; every string is ready for translators |
| Android TV and Wear OS | [00-overview.md](00-overview.md) | Later increments |

## Deliberately different

| What | Specification | Android |
|---|---|---|
| Autoplay | Always on, on every network, with one *Autoplay video* toggle and no Wi-Fi-only setting | *Play shorts on mobile data*, *Loop shorts* and *Start shorts muted*; no app-wide autoplay toggle ([06-media-modes.md](06-media-modes.md)) |
| Startup budget | 400 ms to the first row on an iPhone 15 | 1.2 s to the first row, 800 ms to the first frame, on a Pixel 6a class phone ([12-conventions-quality.md](12-conventions-quality.md)) |
| Runtime dependencies | None outside Apple's frameworks | Jetpack and a short list of libraries ([00-overview.md](00-overview.md)) |
| Themes | Seven named themes and a custom accent | Light, dark or system, with contrast and a black option; the server's colour or dynamic colour ([05-core-ui.md](05-core-ui.md)) |
| Settings, Intelligence and Your year | An Intelligence section; Your year under the account | No Intelligence section; Your year is a Settings section of its own |
