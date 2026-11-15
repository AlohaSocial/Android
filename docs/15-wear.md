# 15 — Wear OS

The watch app, `:wear`: a standalone Wear OS app (API 30, Wear OS 3 and later) under the phone app's application id, so Play lists the two together and the Data Layer pairs them. It is built on Wear's own Compose Material 3, never the phone's UI modules, and comes in the same `generic` and `gplay` flavours.

## Signing in

The watch holds one account, in the Keystore-encrypted vault (`VaultKey.WatchAccount`). Nothing on the watch is backed up or moved to a new device.

| Way | Flavour | How | Token | Signing out |
|---|---|---|---|---|
| On its own | both | The person enters the server through Wear's keyboard or voice input. The watch registers a client of its own, "Aloha Social for Wear OS" with `read write`, and `RemoteAuthClient` opens the server's sign-in page on the paired phone, through the Wear OS companion app. The code comes back through Wear's redirect address (`https://wear.googleapis.com/3p_auth/<application id>`, or its `-cn` twin; both are registered) and is exchanged with PKCE | the watch's own; Authorized apps lists it apart from the phone and can sign it out | revokes the watch's token |
| From the phone | `gplay` | With the Play build on both devices and the phone in reach, "Use your phone's account" asks the phone app for the account in use; it answers with the account and its token | the phone's, kept as *shared* | forgets the account; never revokes, which would sign the phone out too |

The handover is two Data Layer messages (`WatchHandover.REQUEST`, `WatchHandover.ACCOUNT`), not a data item: a message is sent once, on a best-effort basis with no retry, and kept by no one, where a data item would leave the token in Google Play services' own store on both devices. The Data Layer delivers messages only between the same app, signed with the same key, on the reader's own phone and watch. `generic` carries no Google library, so its watch app has no phone to ask.

When the phone signs an account out, it first sends a notice to every watch connected then, on a best-effort basis (`WatchHandover.SIGNED_OUT`, through the `AccountRemovalListener` set), and a watch holding that very session, found by the token's SHA-256 digest in the notice, forgets it: a notice that arrives late or comes from another phone never ends a newer handover. A watch out of reach then finds its token refused, since signing out also revokes it on the server, and forgets the account on that 401: whatever the watch asks with a refused token signs it out.

When the phone renews the token of an account it handed over (refresh tokens, Nextcloud Social 0.26.299, [03-auth-and-accounts.md](03-auth-and-accounts.md)), it sends the new access token to every watch connected then (`WatchHandover.RENEWED`, through the `TokenRenewalListener` set), with the old token's digest; a watch holding that very session takes it. Only accounts the phone handed over send one (`AccountSettings.handedToWatch`), and the refresh token never leaves the phone. The notice goes out in the background and is given up after 15 seconds, so it never holds up the renewal. A watch that missed it gets a 401 and asks the phone for its account again before forgetting it, naming the refused token by its digest in the request: the phone renews the account holding that token first (whichever account is in use there) and answers with its new token. A request naming no token the phone holds is answered as the first handover is. With no newer token from the phone, the watch forgets the account as before. The watch's own sign-in keeps no refresh token and does not refresh: its token lasts as long as the server gives a token never refreshed (365 days by default). The F-Droid build has no handover and none of this.

A limit of the shared token: if the phone signs out while the watch is out of reach, and the server cannot be reached to revoke the token either, the watch keeps a token that still works until it is revoked on the server (Authorized apps, or signing in and out again). The watch's own sign-in has no such limit: its token is its own, and signing the watch out forgets it before anything else.

## Screens

Navigation 3 destinations, dismissed by swiping as on every watch app:

- **Home:** the newest twenty posts of the home timeline, as plain text (`StatusHtmlParser.plainText`); a post behind a content warning shows the warning.
- **Notifications:** the newest twenty, who did what, and the post it is about.
- **A post:** its text, or its content warning with "Show post"; Favourite, Boost and Bookmark as switches; Reply through Wear's own answer screen, with a few ready answers next to the voice and the keyboard, addressed to the author and as visible as the post.
- **Account:** who the watch is signed in as, and Sign out.

The watch never polls. It asks the server when a screen opens or the person retries.

## Tile and complication

| Surface | Shows | Asks the server |
|---|---|---|
| "Latest mention" tile | the newest mention; a private message only says who sent it, since a tile sits next to the watch face; a tap opens the app; signed out, "Open Aloha Social to sign in." | when the tile is shown, and when the system refreshes it, at most every 30 minutes |
| "Unread notifications" complication (short text) | the unread count; a tap opens the app; signed out, an empty slot | when the watch face asks, every 30 minutes (`UPDATE_PERIOD_SECONDS`) |

Both use the watch's own account, so they work the same in both flavours. A server that does not answer leaves the complication's last count in place.

## Notifications from the phone

Wear OS shows the phone's notifications on the watch by itself, with their actions: Reply (with the system's suggested answers), Favourite, and Boost or Mute ([08-notifications-sync.md](08-notifications-sync.md)). They run on the phone. The watch app adds no notifications of its own.

## Tested, and not yet

- Unit tests: the watch's own sign-in against the mock server (its own client and token, a refusal on the phone, sign-out revoking), the phone's account kept without revoking it, the timeline and the favourite round trip, a refused token signing the watch out, signing out telling the listeners before the server.
- Screenshots: sign-in in every state, home, notifications and a post, on a large round watch, a square watch and at 200 % font.
- On a Wear OS 7 emulator: the sign-in screen and Wear's input, the tile in the tile carousel, the complication in a watch face's picker.
- Not yet: the sign-in through the phone and the handover end to end, which need a phone paired with the watch; the emulator pairing assistant did not complete.

## Play Wear listing

Before the watch app goes to the Wear listing:

- [ ] Wear screenshots (round), at least one, from the emulator or a watch, in the listing
- [ ] The Wear form factor opted in, in the Play Console's Advanced settings
- [ ] Standalone declared (`com.google.android.wearable.standalone` is `true`): the app signs in without the phone app
- [ ] Wear OS quality guidelines checked on a real watch: swipe to dismiss, rotary scrolling, no text at the rim, dark theme
- [ ] Tile and complication previews: a proper preview image for the tile picker in place of the launcher icon
- [ ] The release built and signed with the phone app's key
