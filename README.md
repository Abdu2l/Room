# Room

🎵 **[Live demo → abdu2l.github.io/Room](https://abdu2l.github.io/Room/)**

A local-first listening dashboard for Spotify listeners who use Last.fm to scrobble their music. Last.fm records listening; Listening Room reads public Last.fm scrobbles and turns them into period rankings and activity views.

## Run it

    python3 -m http.server 4174 --bind 127.0.0.1

Open http://127.0.0.1:4174. The preview dashboard works without Spotify or Last.fm credentials.

## Set up Last.fm

1. Create one free Last.fm API key for the website from [Last.fm's API account page](https://www.last.fm/api/account/create). The site owner does this once. Visitors do not need developer accounts, API keys, or Spotify Premium.
2. For this local copy, open the **Log in with Spotify** dialog, expand **Website owner setup**, and paste the public API key. For a shared build, put it in `config.js` as `LISTENING_ROOM_LASTFM_API_KEY` before publishing. Do not put an API secret in the browser.
3. Each listener needs a Last.fm account and must link Spotify in [Last.fm's applications settings](https://www.last.fm/settings/applications). The setup link opens Last.fm in a separate tab. Last.fm does not redirect to this site's custom callback, so return to Listening Room and enter the Last.fm username manually.
4. Choose **Show my listening stats**. The username and returned stats are cached in this browser. Use the refresh icon or a time-range button to fetch updated data.

Last.fm scrobbling works with Spotify Free and captures qualifying plays after the Spotify account is linked. Spotify now requires the Last.fm connection to be renewed about every six months; if scrobbles stop appearing, reconnect Spotify in Last.fm's Applications settings. Listening Room uses public Last.fm API reads; it never receives Spotify passwords, cookies, or tokens. A listener's Last.fm profile must be public. The public API key is shared by the site, not supplied separately by visitors.

## What the dashboard reads

- Last.fm's top track and artist charts provide the 7-day, 1-month, 12-month, and all-time rankings.
- The 24-hour view ranks the recent scrobbles returned by Last.fm.
- Activity charts use up to the latest 200 scrobbles in the selected range. The dashboard reports the full period scrobble total separately and explains when the activity rows are capped.
- Last.fm provides scrobble timestamps, not listening duration, so the dashboard counts scrobbles and does not invent minutes listened.
- Imported Spotify JSON history is still supported. It is read in the browser and stays in page memory until reload or **Clear import**.

Last.fm's API documentation asks applications to avoid excessive requests. Listening Room requests data after a user connects, refreshes, or changes the range; it does not poll Spotify or make an API request on each page load. The browser caches the last result locally. Last.fm scrobbling continues on Last.fm while this dashboard is closed; use refresh or change the date range to read newer stats.

Last.fm asks applications planning commercial API use to contact them first.

## Files

- [index.html](./index.html) — dashboard and Last.fm connection dialog.
- [styles.css](./styles.css) — Shelf-inspired layout, typography, animations, and responsive controls.
- [app.js](./app.js) — Last.fm API reader, local cache, Spotify history parser, and rendering.
- [config.js](./config.js) — public Last.fm API key configuration for a shared build.
- [research/spotify-tracker-research.md](./research/spotify-tracker-research.md) — earlier Spotify integration and tracker research.
- [research/](./research/) — Shelf.im design study and Scrapling crawl notes.
