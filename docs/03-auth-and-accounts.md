# 03 — Authentication and accounts

How the app finds a server's API, signs in, keeps the credentials, holds many accounts, asks again when a token stops working, connects the Nextcloud underneath, decides which certificates to trust, and signs out. Written from the code; the server facts were verified against Nextcloud Social 0.26.97.

## Finding the API base

Mastodon apps build `https://host/api/v1/…`. Nextcloud Social serves its routes under the app path, and at the domain root only where the administrator installed the rewrite rules. A native client can target the app path directly, so `ServerProbe` (`core/network/.../probe/ServerProbe.kt`) tries every shape at once:

| Rank | Candidate | Accepted when |
|---|---|---|
| 0 | the `issuer` of `/.well-known/oauth-authorization-server` at the domain root | the issuer is on the host the person typed |
| 1 | the domain root | |
| 2 | `/index.php/apps/social/` | |
| 3 | `/apps/social/` (pretty URLs) | |
| 4, 5 | a path the person typed, and the app path beneath it | a path was typed |

A candidate qualifies when `api/v2/instance`, or `api/v1/instance`, answers with a non-empty `domain`, so a Nextcloud login page answering 200 does not. The lowest rank that qualifies wins, however fast a worse one answered (`RankRace`). Each request has five seconds and the whole probe ten. NodeInfo is read from the domain root's `/.well-known/nodeinfo` directory, with 2.1 then 2.0 as fallbacks, as the cross-check of the software.

A hand-typed API address is probed on its own, keeping only scheme, host, port and path: credentials, a query or a fragment typed into it would otherwise be shown back and sent with every request. When no candidate gets any HTTP answer (the name does not resolve, the connection fails or times out), the server is reported unreachable, not as missing its API, so a typo never reads as a missing web-server rule. An untrusted certificate stops the probe with the chain, for the person to decide. A base that stopped answering is probed for anew at most once an hour per host (`ReprobeGate`).

`ServerFinder` (`core/data`) is what the sign-in screen calls: it takes a host, a URL, a path or a handle, and answers `Found`, `NothingAnswered`, `Unreachable`, `UntrustedCertificate`, `InvalidAddress` or `InsecureAddress`. Release builds refuse `http://`; debug builds allow it for the development hosts only.

## Signing in

OAuth 2 with PKCE: `OAuthFlow` builds the requests and parses the callback, `OAuthClient` talks to the server, and `SignInCoordinator` (`core/data`) runs the attempt.

- The app registers once per server (`POST /api/v1/apps`) with both redirect URIs, newline-separated, and the scopes `read write follow push`. A registration the token endpoint refuses with 401 is forgotten, so the next attempt registers again.
- The redirect is the verified App Link `https://aloha.social/oauth/callback`; the custom scheme `alohasocial://oauth-callback` is used only where link verification failed on the device (and before Android 12, which cannot tell). Nextcloud Social adds a `/` to the scheme redirect, so it arrives as `alohasocial://oauth-callback/?code=…`.
- The authorisation page opens in a Custom Tab, so Nextcloud's own single sign-on and second factor work as they do in the browser. `OAuthRedirectActivity` receives the callback, hands it on and closes.
- PKCE is S256 only, with a 64-byte verifier. Verified on 0.26.97: `code_challenge_method=plain` answers 400 `unsupported code_challenge_method`, a wrong verifier answers 401 `invalid code_verifier`, and a code is single-use.
- The authorisation, token and userinfo endpoints come from the server's metadata only when they are on the API base's origin, and are fixed when the attempt begins; the exchange never fetches metadata again. OAuth requests never follow a redirect, which would carry the code, the secret or the verifier to wherever it points.
- The attempt in flight (state, verifier, endpoints, NodeInfo) is kept in the encrypted vault, not in saved state, so it survives the process dying while the browser tab is open and never reaches a Bundle.
- A callback is matched by its `state` before anything else in it is read: one for another attempt, a denial included, is dropped, and the sign-in on screen keeps waiting.
- A new account that `verify_credentials` answers 500 for is created from `/oauth/userinfo` with its profile pending.
- After the exchange the server's capabilities are detected ([02-server-api.md](02-server-api.md)) and the account is stored.

### The admin scopes

Sign-in never asks for `admin:read admin:write`, so nobody who cannot moderate sees moderation permissions on the consent page. Whether an account may moderate comes from the `role` in `verify_credentials`. When the moderation console's first call answers 403 for lack of the scope, the console offers to allow moderation: a second authorisation of the same account with the admin scopes added (`SignInCoordinator.beginModeration`). A server refuses an authorisation for more than the app registered with, so the app first registers again with the admin scopes when the stored registration lacks them. Each account keeps the client its token was issued to, so signing out still revokes the token with the right client. The console itself is described in [11-safety-privacy-appstore.md](11-safety-privacy-appstore.md) §7.

## Where the credentials live

`TokenVault` (`core/datastore`) is a single encrypted map in `files/vault/secrets.bin`. It holds every secret and nothing else: each account's access token and Nextcloud app password, the client secret per host, the client each token was issued to, and the sign-in in flight. Room rows never hold a secret.

`KeystoreCipher` encrypts it with AES-256-GCM under the Android Keystore key `aloha-vault`, in StrongBox where the device has one. The key needs no user authentication, so background work can read tokens; the app lock guards the screen, not the vault. The vault is decrypted once per process and kept in memory.

The vault is excluded from backups and device transfers. When it cannot be decrypted (a restored backup, a reset Keystore), it starts empty, says so once, and every account is marked as needing to sign in again; the accounts and their cache stay.

## Many accounts

`AccountRepository` (`core/data`) holds the accounts: their rows are in `accounts.db`, their secrets in the vault. There is no limit on the number. An account is identified by its host and the server's account id, so signing in to an account that is already there updates it rather than adding a second.

- The avatar in the top bar opens the account sheet (`AccountSwitcher.kt`, `AccountSheet`): switch, own profile, add an account, sign out, and the accounts that need to sign in again.
- Settings, Accounts reorders them (`AccountOrder`), and the order is the one the sheet, the launcher shortcuts and Direct Share use.
- The active account is a preference; when it is removed, the next one becomes active.

## Signing in again

Any 401 on an account's requests marks it as needing to sign in again (`ClientFactory` reports it, `AccountRepository.markNeedsReauth`). Its polling stops, its cache and unsent posts stay, and a banner offers "Sign in again". The banner, or the same row in Settings, raises `ReauthRequest`; the sign-in flow runs for the same server, and the account row it writes is the same one, which clears the mark and resumes the outbox.

## The Nextcloud underneath

On Nextcloud Social, Settings, Nextcloud connects the Nextcloud itself (`NextcloudConnection`, `core/data/.../nextcloud`):

- Login Flow v2: `status.php` confirms a Nextcloud, `POST index.php/login/v2` starts the flow, the person approves in the browser, and the app polls on the same origin (from one second, backing off to five, for at most five minutes).
- The app password it grants is kept in the vault and sent over HTTP Basic with `OCS-APIRequest: true`.
- It is what reaches the routes Social refuses a token for (authorised apps, memories, review, channels, migration and account deletion) and the notifications app's Web Push, which is how Nextcloud Social pushes ([08-notifications-sync.md](08-notifications-sync.md)).
- Disconnecting revokes the app password on the server (`DELETE ocs/v2.php/core/apppassword`).

Attaching a file from Nextcloud Files does not need the connection: the composer sends a path to `POST /api/v1/media/from-file` with the account's token, and there is no WebDAV browser.

## Certificates

- Release builds trust the system's certificate authorities only; the network security configuration adds no user-installed CA. Debug builds also allow cleartext to the development hosts.
- A server whose certificate the system does not trust can be trusted for its own host: the sign-in screen shows the certificate, and once the person accepts it, `UserTrustStore` keeps that leaf certificate in `noBackupFilesDir/tls/`, for that host only and only while it is valid. `AlohaTrustManager` asks the system first and the store second.
- A server that asks for a client certificate gets one the person picks from the system KeyChain (`KeyChain.choosePrivateKeyAlias`); the alias is remembered per host, and `ClientCertificateKeyManager` presents it to that host only.

## Signing out and deleting

Signing out (`AccountSignOut`, then `AccountRemoval.signOut`) removes everything on the device first, so a server that cannot be reached never keeps an account on the phone:

1. The push registration is withdrawn, with five seconds to do it.
2. The token, the app password and the account row go, and the next account becomes active.
3. The account's cache, settings, unsent posts and their copies, widget content and raised notifications go.
4. Then the Nextcloud app password is revoked, and the OAuth token with the client it was issued to (`POST /oauth/revoke`). The app's registration with the server is kept for the next sign-in.

Deleting the account on the server is separate: Settings, Delete account. On Nextcloud Social, once the Nextcloud is connected, the handle is typed out and `POST /api/v1/account/delete` is sent with the app password; elsewhere the server's own website does it. The account is then signed out as above.
