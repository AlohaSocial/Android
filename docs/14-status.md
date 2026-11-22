# Aloha Social — 14 — Status

What is built, what is in progress, and what is not built or deliberately different from the specification. Kept by hand: a pull request that builds one of the items below takes it off this page.

## Progress

Phase 22, what Aloha Social changed after 0.26.299 (server `aloha/main` at `6fb80deee`, 2026-11-15, not yet released; read from its code and `docs/API.md`, not from a live instance), taken up in one pull request, with what Phase 21 left out (21k to 21m) stacked on it. Legend: ⬜ not started · 🔄 in progress · ✅ done · ⚠️ blocked · 🚫 dropped.

| Item | Status | Note |
|---|---|---|
| 22a: Create an account from sign-in, and come back signed in | ✅ | [#141](https://github.com/AlohaSocial/Android/pull/141) |
| 22b: Sharp pictures on dense screens | ✅ | [#141](https://github.com/AlohaSocial/Android/pull/141) |
| 22c: A post of one to three emoji drawn large | ✅ | [#141](https://github.com/AlohaSocial/Android/pull/141) |
| 21k: Long-form titles | ✅ | [#142](https://github.com/AlohaSocial/Android/pull/142) |
| 21l: One request for the badges | 🚫 | Saves the app no request; see Not built |
| 21m: For You v4, the activity-learning consent | ✅ | [#146](https://github.com/AlohaSocial/Android/pull/146) |
| The web app's post frames and action row, Photos and Shorts layouts, recap and search fixes | 🚫 | Web only; see Not built |

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
| 20 | [#64](https://github.com/AlohaSocial/Android/pull/64)–[#70](https://github.com/AlohaSocial/Android/pull/70) | 2026-10-24 | What Nextcloud Social 0.26.222 to 0.26.278 changed, read from the server's code at `origin/master` on 2026-10-21, not from a live instance: Continue watching from `watch_progress`, video cards and counted views; a stream on Social only where its daemon answers, with conversations and merged notifications; Social's Web Push with quote alerts and the Nextcloud push where Social refuses the endpoint; refusal codes; featured hashtags as the account's own posts, the server's count setting and paging past short pages; starter-pack curators and quotes and engagement by server in Statistics |
| 21 | [#125](https://github.com/AlohaSocial/Android/pull/125)–[#134](https://github.com/AlohaSocial/Android/pull/134) | 2026-11-15 | What Nextcloud Social 0.26.279 to 0.26.299 changed, read from the server's code at `aloha/main` `2eb023b11` on 2026-11-14, not from a live instance: refresh tokens with the watch kept in step, push subscribed again after a forced sign-in, edits of media an older server damaged, hidden followers and following, interests learning off until chosen, replies and counts the server is still reading, posting an event, posts held for review, small avatars and hidden instance counts |

Between the phases: CI artifact retention ([#3](https://github.com/AlohaSocial/Android/pull/3)), the device benchmark and baseline profiles ([#4](https://github.com/AlohaSocial/Android/pull/4)), looking an account up by its id ([#10](https://github.com/AlohaSocial/Android/pull/10)) and who favourited and boosted a post ([#13](https://github.com/AlohaSocial/Android/pull/13)).

## In progress

| What | Notes |
|---|---|
| A merged timeline of every account, the default view | Like a mail client's unified inbox, with each account still selectable on its own; this was a non-goal for 1.0 in [00-overview.md](00-overview.md) §6 until 2026-10-11. The data layer ([04-data-model.md](04-data-model.md)), "All accounts" in the account sheet, and Home's Following of every account together are built ([03-auth-and-accounts.md](03-auth-and-accounts.md)); Notifications, Photos, Video, News, Shorts and Audio of every account together are built too |
| Phase 22: what Aloha Social changed after 0.26.299 | Taken up as pull requests, each taking its row off this table and its line in Progress to done; the server facts are in [02-server-api.md](02-server-api.md). Bluesky is left out for now |
| 22a: Create an account from sign-in, and come back signed in | After 0.26.299 `/api/v1/instance` and `/api/v2/instance` report the sign-up as it stands (they said closed before), v2 names the sign-up page as `registrations.url`, and `/signup` takes `return`, the app's own authorization request, and signs the new account in and continues it. The server card offers Create an account while the sign-up is open: it starts the authorization as Sign in does, keeping the pending authorization in the vault, and opens `registrations.url` with `return` (and `invite`, from an invite link the sign-in screen picked up) in the same sign-in tab, so the redirect back to the app finishes the sign-in. With `approval_required` the card says an administrator approves new accounts first, and the welcome email's Log in continues the same authorization later. `return` goes only to Aloha Social, told apart by NodeInfo's software name: Mastodon and any other server that names a sign-up page get it opened without `return`, and the person signs in afterwards; a server that names none offers nothing |
| 22b: Sharp pictures on dense screens | Aloha Social scales a picture's preview to at most 800 px a side, and the web app now draws the original where the screen needs more. A picture drawn wider than its preview, in pixels, loads `url` instead of `preview_url` where `meta.original` gives both widths, the original is the wider one and at most 2560 px wide; a GIF keeps its still preview. Elsewhere, and on Mastodon, which serves a preview wide enough, nothing changes |
| 22c: A post of one to three emoji drawn large | A post whose text is nothing but one to three emoji (flags, keycaps, skin tones, ZWJ and tag sequences, and the post's own custom emoji) is drawn at 2.5 times the text size in the timeline, as Aloha Social's web app and messengers draw a lone emoji. Done on the client, for posts from every server |
| 21k: Long-form titles | From 0.26.280 a status for an imported ActivityStreams `Article` or `Page` with a name carries `article: {title, type}`, the title plain text of at most 500 characters, which also stays at the top of `content` for clients that do not read the field. Show the title as the post's heading and leave the copy at the top of the content out |

## Not built

| What | Where the specification asks for it | Notes |
|---|---|---|
| A first-frame poster for a video the server did not describe | [06-media-modes.md](06-media-modes.md) §4 | Without ffmpeg on the server, video `meta` is empty; the client does not draw a frame itself |
| Story stickers | [07-composer.md](07-composer.md) | A story is a picture, a video or a text card |
| Push through the Nextcloud push proxy (FCM) | [08-notifications-sync.md](08-notifications-sync.md) §2 | Push is UnifiedPush in both flavours. FCM would be `gplay` only and needs the push proxy to allow the app |
| Encrypted direct messages bound to the app lock | [11-safety-privacy-appstore.md](11-safety-privacy-appstore.md) | The app lock guards the screen, not the data |
| Not taken up from Phase 21 | [02-server-api.md](02-server-api.md) | Posting through `POST /api/v1/post` was removed in 0.26.294; the app never used it |
| The badges route | [02-server-api.md](02-server-api.md) | `GET /api/v1/social/badges` (0.26.299) counts unread notifications like v1, the pending follow requests and their notice, and unread conversations in one request. The app's poll asks one count, v2's grouped unread count on servers that group notifications, which the route cannot replace, and nothing in the app keeps a conversations or follow-requests badge; it would swap one request for another. Worth taking up with such badges |
| For You v4 beyond the consent | [02-server-api.md](02-server-api.md) | The app has no For You feed: the ranked timeline (`/api/v1/recommendations/timeline`), its feedback with Undo, reading signals, reset and export have nothing to attach to. A For You feed is a product decision of its own. The consent the server keeps is built ([#146](https://github.com/AlohaSocial/Android/pull/146)) |
| Not taken up from Phase 22 | [02-server-api.md](02-server-api.md) | The rest changed only Aloha Social's web app: one frame for link previews, quoted posts and events, a quieter action row on touch screens, Shorts, Photos, Videos and Discover without a visible title, posting from the sidebar only, the weekly recap naming the instance (the app's text says "your server" already), and search results kept readable while asked again (the app does not dim them). A link preview's image already keeps its shape before it loads |
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
