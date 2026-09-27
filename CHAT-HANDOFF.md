# Listening Room — project handoff

Updated 2026-09-27

## Goal and design

Listening Room is a Shelf.im-inspired listening stats dashboard. The app should use Last.fm to collect Spotify listening and provide day, week, month, year, and all-time views. Spotify sign-in happens on Last.fm; this site reads the listener's public Last.fm profile.

## Current connection flow

- The **Log in with Spotify** button opens Last.fm's applications settings in another tab.
- Last.fm does not support a custom redirect back to Listening Room. The listener returns manually and enters their Last.fm username.
- The site uses one public Last.fm API key owned by the website. Set it in `config.js` for a shared deployment, or paste it in the local owner setup panel.
- Visitors need a Last.fm account with a public profile, but do not need Spotify Premium or a developer account.
- Spotify's current token policy means users need to reconnect the Last.fm Spotify integration about every six months or scrobbling stops.
- The app requests top track/artist charts for 7 days, 1 month, 12 months, and all time. It requests up to 200 recent scrobbles for activity, reports Last.fm's total for the period, and caches the response locally. There is no background polling or automatic API request on page load.
- Imported Spotify history remains a local browser-only option.

## Current preview

- Local app: http://127.0.0.1:4174/
- Add the owner's free API key through the dialog or in `config.js` before relying on real Last.fm data.

## Main files

- `index.html` — app structure and Last.fm handoff dialog
- `styles.css` — visual design, typography, responsive layout, and controls
- `app.js` — Last.fm API integration, local cache, history import, and rendering
- `config.js` — public Last.fm API key configuration
- `README.md` — setup and data-source details
- `research/spotify-tracker-research.md` — earlier Spotify integration research
