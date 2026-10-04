# 09 — Platform integrations

Where the app meets the rest of Android: home-screen widgets, launcher shortcuts and sharing, links, windows and foldables, the keyboard, media sessions and the app lock. Written from the code. Android TV and Wear OS are later increments; nothing for them is built.

## Widgets

Four Glance widgets in `:widget` (`widget/src/main/kotlin/social/aloha/widget/`). Each is placed for one account, chosen in `WidgetConfigureActivity`, and can be reconfigured later.

| Widget | Shows | Default size | Behind the app lock |
|---|---|---|---|
| `UnreadWidget` | The account's unread notification count | 2×1 | Shown: a number says nothing private |
| `MentionsWidget` | The newest mentions | 3×2 | Hidden |
| `LatestPostsWidget` | The ten newest cached posts of Home, Local or Federated, picked when it is placed | 4×3 | Hidden |
| `ComposeWidget` | One tap to a new post as the account | 2×1 | Shown |

The widgets have no update period of their own. `WidgetUpdates.redraw()` (`core/data/.../sync/WidgetUpdates.kt`) redraws them when what they show changes: a poll finds a new unread count or new mentions, the app leaves the foreground, an account signs out, or a widget is configured. What they show is kept in `WidgetFeedStore`, outside backups.

## Launcher shortcuts and Direct Share

`AccountShortcuts` (`app/.../AccountShortcuts.kt`) publishes dynamic shortcuts only, because the system starts static ones with `FLAG_ACTIVITY_CLEAR_TASK`, which would throw away what the person was doing.

- Long-pressing the icon offers New post, Search and Notifications for the account in use, and with more than one account "Post as" each.
- Each account is also a long-lived sharing shortcut, so the system share sheet offers it as a Direct Share target; `res/xml/shortcuts.xml` declares the share target for text, pictures and video.
- Shortcuts follow the account order chosen in Settings, and go when the account does.

## The share target and "Open in Aloha"

- Text, a picture or a video, or several pictures and videos, shared from another app open the composer with them (`SharedContent.kt`, which also drops the app's own content addresses). A Direct Share target picks the account; otherwise the active one posts.
- "Open in Aloha" is a second entry in the share sheet (the activity alias `.OpenInAloha`). It takes the first web address in the shared text and asks the reader's server for it as a post, whatever the address looks like: a post the server finds opens in the app, anything else opens as any link would.
- Nextcloud Social's own links, under `/index.php/apps/social/`, open in the app like any other.

Everything that arrives from outside, from a share, a shortcut, a notification or a link, becomes an `OutsideRequest` that `MainActivity` turns into navigation.

## Links

- `web+ap://` links and `alohasocial://open?url=…` open the post, profile or hashtag they name (`LinkOpener`), else the browser. A post elsewhere is looked up through the reader's server; only addresses shaped like a post, profile or hashtag are ever sent to it.
- The one verified App Link is the OAuth redirect, `https://aloha.social/oauth/callback`, received by `OAuthRedirectActivity`; the scheme `alohasocial://oauth-callback` is its fallback ([03-auth-and-accounts.md](03-auth-and-accounts.md)). The app claims no other web addresses: it has no host of its own whose posts it could show.

## Windows and foldables

- Once the window is medium width or wider, or beside another app, a post's and a profile's menu offer "Open in new window". `WindowActivity` opens it as a task of its own, next to the current one in split screen or as a desktop window, and each such window is removed from Recents when closed.
- Pictures and videos dragged in from another app attach to the post being written.
- Half open like a laptop, a foldable keeps the video on the upper half of the watch page (`WatchRoute.kt`, from the window's tabletop posture).
- The navigation becomes a rail or a drawer, and threads and profiles open beside the list, as the window grows ([01-architecture.md](01-architecture.md)).

## Keyboard

With a hardware keyboard the timeline takes focus and the same keys as the web work: `J`/`K` select a post, which is highlighted and scrolled to, `L` or `F` favourites it, `B` boosts, `R` replies, `O` or Enter opens; `N` starts a new post (`TimelineCommand.kt`). `?` and Meta+`/` open the system's keyboard shortcuts list, which the app fills (`MainActivity.onProvideKeyboardShortcuts`), instead of a list of its own. A key held with Ctrl, Alt or Meta is left to the system.

## Media sessions and picture-in-picture

- Video and audio each have a Media3 `MediaSessionService` (`VideoPlaybackService`, `AudioPlaybackService`, on the shared `PlayerSessionService`), so playback shows on the lock screen and in the notification and answers headphones.
- Both are exported, as a media session must be, and play-only: other apps may play, pause, seek and skip, never set what plays.
- The watch page goes to picture-in-picture as the reader leaves: entered by the system from Android 12, by the app before.

## App lock

Settings, Privacy turns on a lock (`app/.../AppLock.kt`, `AppLockSettings`):

- Opening the app asks for the fingerprint, face or screen lock: the platform `BiometricPrompt` from Android 10, which also accepts the device credential, and the device-credential screen on Android 8 and 9. A device with no screen lock opens unlocked.
- *Lock again after* chooses when it asks again: on leaving the app, or after 1, 5 or 15 minutes or an hour away.
- While the lock is on, the window is `FLAG_SECURE`: the app is hidden in Recents and from screenshots and screen recording. The Mentions and Latest posts widgets show nothing, and notifications carry no actions.
- The lock guards the screen. It does not encrypt anything with the person's authentication: background work must still read the tokens.
