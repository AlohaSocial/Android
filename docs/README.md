# Documentation

Two kinds of document. The **ported** ones are the Apple app's product specification, carried over as the contract for Android, each with an "On Android" table at the top that says where Android differs; the table wins over the text below it. The **Android** ones describe this app as built, from its code.

| Document | Kind | What it settles |
|---|---|---|
| [00-overview.md](00-overview.md) | Ported | Product scope, platforms, non-goals, vocabulary |
| [01-architecture.md](01-architecture.md) | Android | Modules and their rules, build conventions, state, data flow, navigation, errors and offline |
| [02-server-api.md](02-server-api.md) | Ported | The server contract |
| [03-auth-and-accounts.md](03-auth-and-accounts.md) | Android | Finding the API base, signing in, the vault, many accounts, the Nextcloud connection, certificates, signing out |
| [04-data-model.md](04-data-model.md) | Android | What is stored where, gaps, sweeping, backups, what is never stored |
| [05-core-ui.md](05-core-ui.md) | Ported | Timelines, threads, profiles, notifications, search, settings |
| [06-media-modes.md](06-media-modes.md) | Ported | Photos, video, shorts, audio, news, stories, the media viewer |
| [07-composer.md](07-composer.md) | Ported | Posting |
| [08-notifications-sync.md](08-notifications-sync.md) | Ported | Polling, background refresh, push |
| [09-platform-integrations.md](09-platform-integrations.md) | Android | Widgets, shortcuts, sharing, links, windows, keyboard, media sessions, the app lock |
| [10-intelligence.md](10-intelligence.md) | Android | Translation, and the AI features not built |
| [11-safety-privacy-appstore.md](11-safety-privacy-appstore.md) | Ported | Moderation, privacy, store requirements |
| [12-store.md](12-store.md) | Android | Store listing, Data safety, content rating, permissions |
| [12-conventions-quality.md](12-conventions-quality.md) | Android | Testing, CI, checks, strings, logging, commits and pull requests |
| [14-status.md](14-status.md) | Android | What is built, in progress, not built, and deliberately different |
| [benchmarking.md](benchmarking.md) | Android | How the performance budgets are measured |
