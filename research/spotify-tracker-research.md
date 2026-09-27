# Spotify tracker research

Research checked 27 September 2026. Spotify's developer rules and Web API change over time; the live official documentation linked below remains authoritative.

## Spotify sign-in and data

- [Authorization Code with PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow) is Spotify's browser-app flow when a client secret cannot be kept private. Listening Room creates a verifier and SHA-256 challenge, validates the returned state, exchanges the authorization code in the browser, and keeps bearer/refresh tokens in session storage. It never asks for or sends a client secret.
- [Redirect URI requirements](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri) require an exact registered value. Spotify permits HTTP for loopback IPs and explicitly disallows the hostname localhost. The running preview displays and uses http://127.0.0.1:4174/; the dialog calculates this from the current local address.
- [Top items](https://developer.spotify.com/documentation/web-api/reference/get-users-top-artists-and-tracks) requires user-top-read and supports artists or tracks, up to 50 items per request, across short_term (about four weeks), medium_term (about six months), and long_term (about one year).
- [Recently played](https://developer.spotify.com/documentation/web-api/reference/get-recently-played) requires user-read-recently-played and caps each request at 50 tracks. Its rows have play timestamps, not a complete lifetime listening ledger or actual duration listened. The app labels this as a recent page and does not turn it into lifetime totals.
- [Currently playing](https://developer.spotify.com/documentation/web-api/reference/get-the-users-currently-playing-track) requires user-read-currently-playing. The profile request uses user-read-private.
- [Quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes) currently say that development-mode apps require the app owner to have Spotify Premium and allow up to five authenticated users, each added to the app allowlist. Spotify's [July 2026 quota update](https://developer.spotify.com/blog/2026-07-23-web-api-quota-updates) says development-mode quota is shared across the developer account's Client IDs and quota-exceeded 429 responses include `reason: QUOTA_EXCEEDED`; the app now distinguishes this from ordinary rate limiting.
- [Rate limits](https://developer.spotify.com/documentation/web-api/concepts/rate-limits) can return HTTP 429; the app reports Spotify's Retry-After value when supplied.
- Spotify's API offers no all-time personal stream endpoint. Spotify's [data guide](https://support.spotify.com/us/article/understanding-your-data/) says the standard streaming history covers the past year, while Extended Streaming History covers the lifetime of the account and includes each item's played milliseconds. The tracker therefore supports parsing the account owner's exported JSON locally. It accepts both the extended-history field names (ts, master_metadata_track_name, master_metadata_album_artist_name, ms_played) and the older standard export names (endTime, trackName, artistName, msPlayed). Exact totals are limited to what Spotify included in the selected export.

## Reference-site check: Stats for Spotify

Inspected the public [Stats for Spotify app bundle](https://www.statsforspotify.com/assets/index-6rb07Me_.js) and its [ranking client bundle](https://www.statsforspotify.com/assets/client-BQvc_OhK.js) on 27 September 2026.

- It uses Spotify OAuth Authorization Code with PKCE, a Client ID registered to its own app, and Spotify consent. Its requested scopes include `user-top-read`, `user-read-recently-played`, and `user-read-private`; it also requests playlist-write scopes for its playlist feature.
- It loads top items and recently played data through Spotify's user-authorized Web API. Its public frontend includes its own Client ID because browser apps cannot keep a client secret private.
- It also calls `ranking-api.statsforspotify.com/v4/` with the user's bearer token for ranking snapshot list/history/exchange operations. This is how the site supports comparing rankings from previous visits; the public frontend alone does not establish the backend's retention policy.
- Listening Room already uses the same supported PKCE pattern and top/recent Spotify endpoints. It now stores changed top-track and artist rankings in this browser, per Spotify account and time range, and shows movement on a later refresh. It does not send the access token or ranking snapshots to Stats for Spotify or another Listening Room server. We do not reuse Stats for Spotify's Client ID or private ranking service; its OAuth redirect is registered to its own site.

## Open-source projects reviewed

### Spotify Web API Examples

[spotify/web-api-examples](https://github.com/spotify/web-api-examples) is Spotify's Apache-2.0 repository of auth and profile examples. Its PKCE example informed the authorization parameters and code exchange sequence implemented in app.js. No client secret is embedded.

### Listening Stats

[HeadshotInteractive/listening-stats](https://github.com/HeadshotInteractive/listening-stats) is MIT-licensed and runs as a Spicetify custom app. Its README documents top tracks/artists, hourly and weekday activity, local history, and export-oriented dashboards. Those categories informed the page navigation and locally derived activity view. Its Spicetify runtime and provider integrations are not portable to this static page, so its files are not bundled here.

### YourSpotify

[Yooooomi/your_spotify](https://github.com/Yooooomi/your_spotify) is GPL-3.0 and documents server-side polling, persisted history, and importing Spotify privacy exports. It needs a backend and database; the page here is browser-only. Its useful product pattern is to combine recent API collection with an explicit export import for older history. No code from its repository is copied into this project.

## Implemented data boundaries

- Connected account: profile name, Spotify top tracks/artists for the selected Spotify time range, latest returned recently-played page (up to 50), and the current track when Spotify returns one.
- While the page is open, the app checks the current track and recently-played endpoint every 90 seconds. Recent items are deduplicated and stored in browser local storage, keyed by Spotify user ID, so the play-event log survives a reload on that device. The API does not return how long an item was actually played; the UI counts plays and does not label full track duration as listening time.
- Top-track and artist rank snapshots are stored in browser local storage, keyed by Spotify user ID, endpoint period, and item type. Rank movement is computed against the previous changed snapshot. The local-tracking clear control removes these snapshots along with the play-event log.
- Imported export: play-entry counts, summed msPlayed duration, unique track/artist pairs, period rankings, day-of-week and hour-of-day patterns. The importer deduplicates repeated rows by timestamp + track + artist so overlapping export files do not double-count identical events.
- Preview mode: all names, counts, timestamps, and listening patterns are illustrative sample values. The dashboard labels them as sample data.
- Data handling: no Listening Room backend, analytics, or network upload is used. OAuth tokens stay in tab session storage; the optional local play log stays in browser local storage and can be cleared from the dashboard. Imported files exist in page memory until cleared or the tab is reloaded. Spotify API and artwork requests, plus the remotely hosted display font, are external requests.
