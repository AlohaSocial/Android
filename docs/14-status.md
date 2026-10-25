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
| 12 | [#27](https://github.com/AlohaSocial/Android/pull/27) | 2026-10-04 | Logging with area tags, a redacted in-memory log in release builds, Share diagnostics, the R8 mappings on each release |
| 13 | [#28](https://github.com/AlohaSocial/Android/pull/28) | 2026-10-05 | A post card with a two-line header, hidden content that says what it hides, motion that Reduce motion stills, a thread with connectors and a reply bar, the profile header and tabs, notification cards, Black without tints and a colour per account, a viewer tinted by its picture, text size, times instead of ages, folded boosts, screens that slide, one skeleton and one empty state |
| 14 | [#30](https://github.com/AlohaSocial/Android/pull/30) | 2026-10-05 | Home as the reader's pinned feeds with a switcher and Edit feeds, another server's own posts as a feed, quoting, boosting for a chosen audience and acting as another account, pausing notifications and choosing whose arrive, a richer composer (completion strip, detected language, attachment cards, alt-text page, draft resume and preview), search intents, hashtag curves, a profile's QR code and post search, the sign-in server preview, the catch-up page, focus mode, the picture-in-picture window's buttons |
| 15 | [#56](https://github.com/AlohaSocial/Android/pull/56) | 2026-10-16 | The Android TV app, [16-tv.md](16-tv.md): sign-in by a code from the phone through a QR code, shelves of the videos and shorts of those followed, channels, everyone's and trending, with muted previews; the watch page on the player's own controls and Shorts as a pager, all on the D-pad; sensitive media covered by the reader's policy, reporting from the watch page |
| 16 | [#55](https://github.com/AlohaSocial/Android/pull/55) | 2026-10-15 | The Wear OS app, [15-wear.md](15-wear.md): its own sign-in through the phone's browser, or the Play build's handover of the phone's account; home, notifications and a post with favourite, boost, bookmark and reply; the latest mention as a tile, the unread count as a complication |
| 17 | [#32](https://github.com/AlohaSocial/Android/pull/32) | 2026-10-06 | Alt text drafted on the device, marked as AI generated until edited and checked before posting; a draft rewritten on the device with its mentions, hashtags and links kept; a thread summarised in a sheet and translated on one tap; an Intelligence section in Settings with each feature off until turned on and a privacy note per build. Alt-text drafts come in both builds, by open software; rewriting and summaries need Gemini Nano and are in the Play build only |
| 18 (in part) | [#33](https://github.com/AlohaSocial/Android/pull/33) | 2026-10-06 | The active account's stream where its server has one (Mastodon): edits, deletions, notifications and filter changes as they happen, polling slowed to a safety net meanwhile; uploads sent once per file, joined again by a reopened draft, cancellable from their notification, not cut off by a call timeout. Push through the Nextcloud push proxy and the sync etiquette review are still open |
| 19a | [#34](https://github.com/AlohaSocial/Android/pull/34) | 2026-10-06 | What Nextcloud Social 0.26.126 fixed, taken up by the app: a profile save answered with a server error is a failure (Social no longer answers 500 after storing part of a save, and answers 200 with what it stored when a late step fails); a profile's posts are searchable on Social too; the edit history lists each version's pictures by description; a placeholder header is no header (Social's `header_default`, Mastodon's `missing.png`) and a stand-in avatar is not offered for removal; a new account answers at once. Uploads meet the limit PHP really enforces, which Social now advertises |
| 19b (in part) | [#35](https://github.com/AlohaSocial/Android/pull/35) | 2026-10-10 | What the Nextcloud connection opens on Nextcloud Social, as pages in Settings: Looking back (the weekly recap and this day in earlier years), Statistics (also in the account sheet), Video channels (also in Video mode), Authorized apps, and Export your account, whole or a list at a time, saved where the reader picks. The Nextcloud Files browser is still open |
| 19b (Files) | [#36](https://github.com/AlohaSocial/Android/pull/36) | 2026-10-11 | Nextcloud Files in the composer, through the Nextcloud connection: folders to browse with the server's previews, files picked up to the post's room and attached by path so their bytes stay on the server, each one the post cannot take shown with why, and a device file uploaded into the folder shown (chunked above 10 MiB, carried on after a lost connection, never overwriting) to be picked from there |

Between the phases: CI artifact retention ([#3](https://github.com/AlohaSocial/Android/pull/3)), the device benchmark and baseline profiles ([#4](https://github.com/AlohaSocial/Android/pull/4)), looking an account up by its id ([#10](https://github.com/AlohaSocial/Android/pull/10)) and who favourited and boosted a post ([#13](https://github.com/AlohaSocial/Android/pull/13)).

## In progress

| What | Notes |
|---|---|
| A merged timeline of every account, the default view | Like a mail client's unified inbox, with each account still selectable on its own; this was a non-goal for 1.0 in [00-overview.md](00-overview.md) §6 until 2026-10-11. The data layer ([04-data-model.md](04-data-model.md)), "All accounts" in the account sheet, and Home's Following of every account together are built ([03-auth-and-accounts.md](03-auth-and-accounts.md)); Notifications, Photos, Video, News, Shorts and Audio of every account together are built too |
| Phase 20: what Nextcloud Social 0.26.222 to 0.26.278 and the builds after it changed | Taken up as stacked pull requests, each taking its row off this table; the server facts are in [02-server-api.md](02-server-api.md). Bluesky is left out for now |
| 20a: Continue watching and video cards | Continue watching is empty against Social: its `videos/continue` carries the place in `watch_progress`, which the app does not read, so every entry counts as unstarted and is dropped. Read `watch_progress` there and on a single post (the place then follows the reader across devices), take views, poster and date from `video_card`, and count a view with `POST /statuses/{id}/view` after five seconds of playback |
| 20b: Streaming on Social | Every Social from 0.26.251 announces a streaming address without a stream behind it unless its admin runs the daemon, so the app reconnects five times on each return to the front. Ask `/api/v1/streaming/health` first and stream only when it answers; take `conversation` (Direct messages) and `notifications_merged` (Notifications) from the stream too |
| 20c: Web Push from Social | Ask for `quote` and `quoted_update` alerts, which Social pushes; when the server refuses the endpoint as not public (422), register with the Nextcloud push where the account has its Nextcloud connected and keep polling otherwise, and never keep both |
| 20d: Refusals that say why | Carry the server's `code` and its fields in `ApiError` and say in the reader's language the one refusal the app meets today, a full story limit (`shorts-full`); the featured-tag, banner, handle and migration codes come from routes the app does not call yet and get their words with those features |
| 20e: Profiles and counts | A featured hashtag opens the account's own posts with it (`tagged`); the Posts tab filters replies on the device below 0.26.237; Reading's *Show numbers* also sets the server's `/api/v1/social/counts`, so numbers the server zeroes come back; a page shorter than asked for is not the end while `Link` names a next one |
| 20f: Starter packs and statistics | A pack shows its curator and a link to its source; following a pack that followed nobody is a failure; Statistics adds a quotes count beside likes, boosts and replies, and a list of the servers the reader's likes, boosts and replies came from, with their counts and the share from the reader's own server (`engagement_sources`) |
| Not taken up from Phase 20 | `only_short` (0.26.222) is already what Shorts asks Social for. The device authorization grant (after 0.26.278) belongs with the TV sign-in. A reply-hiding preference (after 0.26.278) is the web's; the paging fix in 20e covers its short pages |

## Not built

| What | Where the specification asks for it | Notes |
|---|---|---|
| A first-frame poster for a video the server did not describe | [06-media-modes.md](06-media-modes.md) §4 | Without ffmpeg on the server, video `meta` is empty; the client does not draw a frame itself |
| Story stickers | [07-composer.md](07-composer.md) | A story is a picture, a video or a text card |
| The story player from a profile | [06-media-modes.md](06-media-modes.md) | A profile's Stories tab lists the stories and opens each on the web; the player is reachable from Photos only |
| Push through the Nextcloud push proxy (FCM) | [08-notifications-sync.md](08-notifications-sync.md) §2 | Push is UnifiedPush in both flavours. FCM would be `gplay` only and needs the push proxy to allow the app |
| Encrypted direct messages bound to the app lock | [11-safety-privacy-appstore.md](11-safety-privacy-appstore.md) | The app lock guards the screen, not the data |
| Handoff and Spotlight counterparts | [00-overview.md](00-overview.md) §5 | No Android equivalent is planned yet |
| The reproducible-build check | [12-store.md](12-store.md) | Two clean builds of the first release tag, compared, when it is cut; nothing in CI checks it today |
| Translations of the app | [12-conventions-quality.md](12-conventions-quality.md) | English only; every string is ready for translators |

## Deliberately different

| What | Specification | Android |
|---|---|---|
| Autoplay | Always on, on every network, with one *Autoplay video* toggle and no Wi-Fi-only setting | *Play shorts on mobile data*, *Loop shorts* and *Start shorts muted*; no app-wide autoplay toggle ([06-media-modes.md](06-media-modes.md)) |
| Startup budget | 400 ms to the first row on an iPhone 15 | 1.2 s to the first row, 800 ms to the first frame, on a Pixel 6a class phone ([12-conventions-quality.md](12-conventions-quality.md)) |
| Runtime dependencies | None outside Apple's frameworks | Jetpack and a short list of libraries ([00-overview.md](00-overview.md)) |
| Themes | Seven named themes and a custom accent | Light, dark or system, with contrast and a black option; the server's colour or dynamic colour ([05-core-ui.md](05-core-ui.md)) |
| Your year | Under the account | A Settings section of its own |
| Post header | One line: name, handle with host where it differs, age | Two lines: name and marks, then age and the handle in full; open for review ([05-core-ui.md](05-core-ui.md)) |
| Reduce motion | Slides and scales become cross-fades | Every animation jumps to its end, with Reduce motion in Reading or the system animation scale at 0 ([05-core-ui.md](05-core-ui.md)) |
