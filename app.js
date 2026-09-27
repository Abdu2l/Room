/* Room — browser-only listening dashboard.
   Spotify scrobbling is handled by Last.fm. This page reads the user's public
   Last.fm listening history with the site's public API key. */

(() => {
  "use strict";

  const LASTFM_API_KEY_STORAGE = "listening-room-lastfm-api-key";
  const LASTFM_USERNAME_KEY = "listening-room-lastfm-username";
  const LASTFM_CACHE_KEY = "listening-room-lastfm-cache-v1";
  const THEME_KEY = "room-theme";
  const THEMES = {
    grey: "./styles.css?v=6",
    pink: "./themes/pink.css?v=6",
    green: "./themes/green.css?v=6",
    red: "./themes/red.css?v=6"
  };
  const RANGE_MS = {
    day: 24 * 60 * 60 * 1000,
    week: 7 * 24 * 60 * 60 * 1000,
    month: 30 * 24 * 60 * 60 * 1000,
    year: 365 * 24 * 60 * 60 * 1000,
    all_time: Number.MAX_SAFE_INTEGER
  };
  const RANGE_LABEL = {
    day: "Last 24 hours",
    week: "Last 7 days",
    month: "Last month",
    year: "Last year",
    all_time: "All Last.fm history"
  };
  const ARTIST_COLORS = ["#db76ac", "#c17d9e", "#dc986e", "#8975ab", "#72a19a"];
  const lastfmTrackArtwork = new Map();

  // These are deliberately fictional preview values, visibly marked as sample data.
  const SAMPLE = {
    profile: null,
    tracks: [
      { name: "Show Me How", artist: "Men I Trust", duration_ms: 221000, plays: 42 },
      { name: "A Different Age", artist: "Current Joys", duration_ms: 261000, plays: 38 },
      { name: "Borderline", artist: "Tame Impala", duration_ms: 237000, plays: 31 },
      { name: "Bags", artist: "Clairo", duration_ms: 263000, plays: 28 },
      { name: "Tadow", artist: "Masego, FKJ", duration_ms: 307000, plays: 24 },
      { name: "The Less I Know the Better", artist: "Tame Impala", duration_ms: 216000, plays: 22 },
      { name: "Archie, Marry Me", artist: "Alvvays", duration_ms: 203000, plays: 20 },
      { name: "Drew Barrymore", artist: "SZA", duration_ms: 231000, plays: 17 }
    ],
    artists: [
      { name: "Men I Trust", genres: ["indie pop"] },
      { name: "Tame Impala", genres: ["psychedelic pop"] },
      { name: "Clairo", genres: ["indie pop"] },
      { name: "Khruangbin", genres: ["psychedelic soul"] },
      { name: "The Marías", genres: ["indie"] }
    ],
    recent: [
      { trackName: "Show Me How", artistName: "Men I Trust", ts: Date.now() - 7 * 60000, msPlayed: 221000 },
      { trackName: "Bags", artistName: "Clairo", ts: Date.now() - 38 * 60000, msPlayed: 263000 },
      { trackName: "Borderline", artistName: "Tame Impala", ts: Date.now() - 70 * 60000, msPlayed: 237000 },
      { trackName: "Tadow", artistName: "Masego, FKJ", ts: Date.now() - 115 * 60000, msPlayed: 307000 }
    ],
    nowPlaying: null
  };
  const SAMPLE_DAYS = [31, 54, 42, 72, 49, 88, 66];

  const state = {
    mode: "demo",
    view: "overview",
    range: "week",
    profile: null,
    tracks: SAMPLE.tracks,
    artists: SAMPLE.artists,
    recent: SAMPLE.recent,
    nowPlaying: null,
    history: null,
    lastfmUsername: "",
    lastfmRows: [],
    lastfmTopTracks: [],
    lastfmTopArtists: [],
    lastfmTotalCount: 0,
    lastfmTruncated: false,
    genreKey: "",
    genreStatus: "idle",
    genres: [],
    shareFormat: "story",
    shareCanvas: null,
    noticeTimer: null,
    toastTimer: null
  };

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (character) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[character]);
  }

  function cssVar(name) {
    try {
      return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    } catch {
      return "";
    }
  }

  function hexToRgba(hex, alpha) {
    const clean = String(hex || "").trim().replace("#", "");
    const full = clean.length === 3 ? clean.split("").map((c) => c + c).join("") : clean;
    if (!/^[0-9a-f]{6}$/i.test(full)) return "rgb(40 40 40 / " + alpha + ")";
    const r = parseInt(full.slice(0, 2), 16);
    const g = parseInt(full.slice(2, 4), 16);
    const b = parseInt(full.slice(4, 6), 16);
    return "rgb(" + r + " " + g + " " + b + " / " + alpha + ")";
  }

  function iconSvg(name) {
    const paths = {
      "◷": '<circle cx="12" cy="12" r="8.5"></circle><path d="M12 7v5l3.5 2"></path>',
      "♫": '<path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle>',
      "✳": '<path d="M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9"></path><circle cx="12" cy="12" r="2"></circle>',
      "↗": '<path d="M5 18 18 5M8 5h10v10"></path>'
    };
    return '<svg class="line-icon" viewBox="0 0 24 24" aria-hidden="true">' +
      (paths[name] || paths["✳"]) + '</svg>';
  }

  function formatNumber(value) {
    return new Intl.NumberFormat().format(value || 0);
  }

  function formatDuration(milliseconds) {
    const totalMinutes = Math.round((Number(milliseconds) || 0) / 60000);
    if (totalMinutes < 60) return totalMinutes + " min";
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return minutes ? hours + " hr " + minutes + " min" : hours + " hr";
  }

  function relativeTime(timestamp) {
    const elapsed = Math.max(0, Date.now() - timestamp);
    const minutes = Math.floor(elapsed / 60000);
    if (minutes < 1) return "Just now";
    if (minutes < 60) return minutes + " min ago";
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return hours + (hours === 1 ? " hr ago" : " hrs ago");
    const days = Math.floor(hours / 24);
    return days + (days === 1 ? " day ago" : " days ago");
  }

  function setNotice(message, isError = false) {
    const notice = $("#notice");
    if (!message) {
      notice.hidden = true;
      notice.textContent = "";
      notice.classList.remove("is-error");
      return;
    }
    notice.textContent = message;
    notice.hidden = false;
    notice.classList.toggle("is-error", isError);
    window.clearTimeout(state.noticeTimer);
    state.noticeTimer = window.setTimeout(() => { notice.hidden = true; }, 9000);
  }

  function showToast(message) {
    const toast = $("#toast");
    toast.textContent = message;
    toast.classList.add("is-visible");
    window.clearTimeout(state.toastTimer);
    state.toastTimer = window.setTimeout(() => toast.classList.remove("is-visible"), 3400);
  }

  function getLastfmApiKey() {
    const configuredKey = String(window.LISTENING_ROOM_LASTFM_API_KEY || "").trim();
    return configuredKey || String(localStorage.getItem(LASTFM_API_KEY_STORAGE) || "").trim();
  }

  function secureArtworkUrl(value) {
    const url = String(value || "").trim();
    if (!url) return "";
    const secureUrl = url.startsWith("//") ? "https:" + url : url.replace(/^http:/i, "https:");
    if (!/^https:\/\//i.test(secureUrl) || secureUrl.includes("2a96cbd8b46e442fc41c2b86b821562f")) return "";
    return secureUrl;
  }

  function lastfmArtwork(images) {
    const entries = Array.isArray(images) ? images : images ? [images] : [];
    const sizes = ["extralarge", "mega", "large", "medium", "small"];
    for (const size of sizes) {
      for (const entry of entries.filter((item) => item?.size === size)) {
        const url = secureArtworkUrl(entry?.["#text"] || entry?.url || entry);
        if (url) return url;
      }
    }
    for (const entry of entries) {
      const url = secureArtworkUrl(entry?.["#text"] || entry?.url || entry);
      if (url) return url;
    }
    return "";
  }

  /* Fallback artwork: Last.fm chart endpoints now return only the placeholder
     star image for most artists/tracks, so fill gaps with free public art. */
  const itunesTrackArtworkCache = new Map();
  const artistArtworkCache = new Map();
  const artworkInflight = new Map();

  function upgradeItunesArtwork(url) {
    const clean = secureArtworkUrl(url);
    if (!clean) return "";
    // iTunes returns 100x100 by default; request a larger image for crisp covers.
    if (/\/\d+x\d+bb\.jpg$/i.test(clean)) return clean.replace(/\/\d+x\d+bb\.jpg$/i, "/600x600bb.jpg");
    return clean;
  }

  function fetchJsonTimeout(url, ms = 8000) {
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), ms);
    return fetch(url, { signal: controller.signal }).then((response) => {
      window.clearTimeout(timer);
      if (!response.ok) throw new Error("artwork request failed");
      return response.json();
    }).catch((error) => {
      window.clearTimeout(timer);
      throw error;
    });
  }

  function jsonpGet(url, timeoutMs = 9000) {
    return new Promise((resolve, reject) => {
      const callbackName = "__listeningRoomJsonp_" + Date.now().toString(36) +
        Math.random().toString(36).slice(2);
      const script = document.createElement("script");
      let settled = false;
      const timer = window.setTimeout(() => finish(new Error("artwork timeout")), timeoutMs);
      function finish(error, payload) {
        if (settled) return;
        settled = true;
        window.clearTimeout(timer);
        delete window[callbackName];
        script.remove();
        if (error) reject(error);
        else resolve(payload);
      }
      window[callbackName] = (payload) => finish(null, payload);
      script.onerror = () => finish(new Error("artwork unreachable"));
      const separator = url.includes("?") ? "&" : "?";
      script.src = url + separator + "output=jsonp&callback=" + callbackName;
      document.head.appendChild(script);
    });
  }

  function pickItunesTrackArtwork(results, artist, title) {
    const wantArtist = String(artist || "").trim().toLocaleLowerCase();
    const wantTitle = String(title || "").trim().toLocaleLowerCase();
    if (!Array.isArray(results) || !results.length) return "";
    const scored = results.map((item) => {
      const itemArtist = String(item?.artistName || "").toLocaleLowerCase();
      const itemTrack = String(item?.trackName || "").toLocaleLowerCase();
      let score = 0;
      if (itemTrack === wantTitle) score += 4;
      else if (wantTitle && itemTrack.includes(wantTitle.slice(0, Math.min(12, wantTitle.length)))) score += 2;
      if (wantArtist && (itemArtist.includes(wantArtist) || wantArtist.includes(itemArtist))) score += 3;
      if (item?.artworkUrl100) score += 1;
      return { item, score };
    }).sort((a, b) => b.score - a.score);
    const best = scored[0]?.item;
    return upgradeItunesArtwork(best?.artworkUrl100 || "");
  }

  function itunesTrackArtwork(artist, title) {
    const key = String(artist || "").trim().toLocaleLowerCase() + "\u0000" + String(title || "").trim().toLocaleLowerCase();
    if (itunesTrackArtworkCache.has(key)) return Promise.resolve(itunesTrackArtworkCache.get(key));
    if (artworkInflight.has("track:" + key)) return artworkInflight.get("track:" + key);
    const query = [artist, title].map((part) => String(part || "").trim()).filter(Boolean).join(" ");
    if (!query) return Promise.resolve("");
    const url = "https://itunes.apple.com/search?" + new URLSearchParams({
      term: query, media: "music", entity: "song", limit: "5"
    }).toString();
    const pending = fetchJsonTimeout(url).then((payload) => {
      const image = pickItunesTrackArtwork(payload?.results, artist, title);
      itunesTrackArtworkCache.set(key, image);
      return image;
    }).catch(() => {
      itunesTrackArtworkCache.set(key, "");
      return "";
    }).finally(() => {
      artworkInflight.delete("track:" + key);
    });
    artworkInflight.set("track:" + key, pending);
    return pending;
  }

  function itunesArtistArtwork(artist) {
    const key = String(artist || "").trim().toLocaleLowerCase();
    if (!key) return Promise.resolve("");
    const url = "https://itunes.apple.com/search?" + new URLSearchParams({
      term: String(artist || "").trim(), media: "music", entity: "album", limit: "3"
    }).toString();
    return fetchJsonTimeout(url).then((payload) => {
      const results = Array.isArray(payload?.results) ? payload.results : [];
      for (const item of results) {
        const image = upgradeItunesArtwork(item?.artworkUrl100 || "");
        if (image) return image;
      }
      return "";
    }).catch(() => "");
  }

  function deezerArtistArtwork(artist) {
    const key = String(artist || "").trim().toLocaleLowerCase();
    if (!key) return Promise.resolve("");
    if (artistArtworkCache.has(key)) return Promise.resolve(artistArtworkCache.get(key));
    if (artworkInflight.has("artist:" + key)) return artworkInflight.get("artist:" + key);
    const pending = jsonpGet("https://api.deezer.com/search/artist?" + new URLSearchParams({
      q: String(artist || "").trim(), limit: "5"
    }).toString()).then((payload) => {
      const entries = Array.isArray(payload?.data) ? payload.data : [];
      const exact = entries.find((entry) => String(entry?.name || "").trim().toLocaleLowerCase() === key);
      const candidates = exact ? [exact] : entries;
      for (const entry of candidates) {
        const image = secureArtworkUrl(entry?.picture_big || entry?.picture_medium || entry?.picture || "");
        // Deezer returns an empty artist path when it has no photo.
        if (image && !image.includes("/images/artist//")) return image;
      }
      return "";
    }).catch(() => "").then(async (image) => {
      // Deezer has real face photos; fall back to an album cover when it has none.
      if (image) return image;
      try {
        return await itunesArtistArtwork(artist);
      } catch {
        return "";
      }
    }).then((image) => {
      artistArtworkCache.set(key, image || "");
      return image || "";
    }).finally(() => {
      artworkInflight.delete("artist:" + key);
    });
    artworkInflight.set("artist:" + key, pending);
    return pending;
  }

  function getTrackArtworkFallback(artist, title) {
    return itunesTrackArtwork(artist, title);
  }

  function getArtistArtworkFallback(artist) {
    return deezerArtistArtwork(artist);
  }

  async function mapWithConcurrency(items, limit, fn) {
    const queue = items.slice();
    const workers = Array.from({ length: Math.min(Math.max(limit, 1), Math.max(queue.length, 1)) }, async () => {
      while (queue.length) {
        const item = queue.shift();
        try {
          await fn(item);
        } catch { /* Keep other artwork loading when one item fails. */ }
      }
    });
    await Promise.all(workers);
  }

  async function enrichMissingArtwork(tracks, artists, recent) {
    const missingTracks = (Array.isArray(tracks) ? tracks : []).filter((track) => !secureArtworkUrl(track?.image))
      .slice(0, 12);
    const missingArtists = (Array.isArray(artists) ? artists : []).filter((artist) => !secureArtworkUrl(artist?.image))
      .slice(0, 12);
    await Promise.all([
      mapWithConcurrency(missingTracks, 5, async (track) => {
        const artist = track.artist || track.artistName || "";
        const title = track.name || track.trackName || "";
        const image = await getTrackArtworkFallback(artist, title);
        if (image) track.image = image;
      }),
      mapWithConcurrency(missingArtists, 5, async (artist) => {
        const image = await getArtistArtworkFallback(artist.name || "");
        if (image) artist.image = image;
      })
    ]);
    // Reuse freshly found art for recent rows so the activity feed matches the lists.
    const trackArtByKey = new Map((Array.isArray(tracks) ? tracks : [])
      .filter((track) => secureArtworkUrl(track?.image))
      .map((track) => [trackArtworkKey(track), track.image]));
    const artistArtByName = new Map((Array.isArray(artists) ? artists : [])
      .filter((artist) => secureArtworkUrl(artist?.image))
      .map((artist) => [String(artist.name || "").trim().toLocaleLowerCase(), artist.image]));
    (Array.isArray(recent) ? recent : []).forEach((row) => {
      if (!secureArtworkUrl(row.image)) {
        const known = trackArtByKey.get(trackArtworkKey(row));
        if (known) row.image = known;
      }
      if (!secureArtworkUrl(row.artistImage)) {
        const knownArtist = artistArtByName.get(String(row.artistName || "").trim().toLocaleLowerCase());
        if (knownArtist) row.artistImage = knownArtist;
      }
    });
    const stillMissingRecent = (Array.isArray(recent) ? recent : []).filter((row) => !secureArtworkUrl(row.image)).slice(0, 6);
    await mapWithConcurrency(stillMissingRecent, 5, async (row) => {
      const image = await getTrackArtworkFallback(row.artistName || "", row.trackName || "");
      if (image) {
        row.image = image;
        const key = trackArtworkKey(row);
        if (!trackArtByKey.has(key)) trackArtByKey.set(key, image);
      }
    });
  }

  function hydrateArtworkImages() {
    const covers = Array.from(document.querySelectorAll(".cover-art.is-missing[data-artist][data-track]"))
      .filter((element) => !element.dataset.hydrating).slice(0, 30);
    const portraits = Array.from(document.querySelectorAll(".artist-art.is-missing[data-artist]"))
      .filter((element) => !element.dataset.hydrating).slice(0, 30);
    covers.forEach((element) => { element.dataset.hydrating = "1"; });
    portraits.forEach((element) => { element.dataset.hydrating = "1"; });
    mapWithConcurrency(covers, 6, async (element) => {
      const artist = element.getAttribute("data-artist") || "";
      const title = element.getAttribute("data-track") || "";
      const image = await getTrackArtworkFallback(artist, title);
      if (!image || !element.isConnected) return;
      const img = document.createElement("img");
      img.src = image;
      img.alt = "";
      img.loading = "lazy";
      img.referrerPolicy = "no-referrer";
      img.onerror = () => {
        img.remove();
        delete element.dataset.hydrating;
      };
      element.prepend(img);
      element.classList.remove("is-missing");
    });
    mapWithConcurrency(portraits, 6, async (element) => {
      const artist = element.getAttribute("data-artist") || "";
      const image = await getArtistArtworkFallback(artist);
      if (!image || !element.isConnected) return;
      const img = document.createElement("img");
      img.src = image;
      img.alt = "";
      img.loading = "lazy";
      img.referrerPolicy = "no-referrer";
      img.onerror = () => {
        img.remove();
        delete element.dataset.hydrating;
      };
      element.prepend(img);
      element.classList.remove("is-missing");
    });
  }

  function lastfmApi(method, username, apiKey, params = {}) {
    const url = new URL("https://ws.audioscrobbler.com/2.0/");
    Object.entries({ method, ...(username ? { user: username } : {}), api_key: apiKey, format: "json", ...params })
      .forEach(([key, value]) => url.searchParams.set(key, String(value)));
    return new Promise((resolve, reject) => {
      const callbackName = "__listeningRoomLastfm_" + Date.now().toString(36) +
        Math.random().toString(36).slice(2);
      const script = document.createElement("script");
      let settled = false;
      const timeout = window.setTimeout(() => {
        finish(new Error("Last.fm took too long to respond. Check your connection and try again."));
      }, 15000);

      function finish(error, payload) {
        if (settled) return;
        settled = true;
        window.clearTimeout(timeout);
        delete window[callbackName];
        script.remove();
        if (error) {
          reject(error);
          return;
        }
        if (payload?.error) {
          const code = Number(payload.error);
          if (code === 6) reject(new Error("That Last.fm username could not be found. Check the spelling and try again."));
          else if (code === 10) reject(new Error("The Last.fm API key is invalid. The website owner needs to add a valid public API key."));
          else if (code === 29) reject(new Error("Last.fm is rate-limiting this request. Wait a moment, then refresh."));
          else reject(new Error(payload.message || "Last.fm could not return this listening data."));
          return;
        }
        resolve(payload);
      }

      window[callbackName] = (payload) => finish(null, payload);
      script.onerror = () => finish(new Error("Could not reach Last.fm. Check your connection and try again."));
      url.searchParams.set("callback", callbackName);
      script.src = url.href;
      document.head.appendChild(script);
    });
  }

  function parseLastfmTracks(payload) {
    const entries = payload?.recenttracks?.track;
    const tracks = Array.isArray(entries) ? entries : entries ? [entries] : [];
    return tracks.map((track) => {
      const timestamp = Number(track.date?.uts) * 1000;
      const artist = track.artist?.["#text"] || track.artist?.name || track.artist || "";
      const image = lastfmArtwork(track.image) || lastfmArtwork(track.album?.image);
      if (!track.name || !artist || !Number.isFinite(timestamp)) return null;
      return {
        trackName: String(track.name),
        artistName: String(artist),
        album: String(track.album?.["#text"] || ""),
        ts: timestamp,
        msPlayed: 0,
        image,
        url: String(track.url || "")
      };
    }).filter(Boolean);
  }

  function parseLastfmTopTracks(payload) {
    const entries = payload?.toptracks?.track;
    const tracks = Array.isArray(entries) ? entries : entries ? [entries] : [];
    return tracks.map((track) => ({
      name: String(track.name || "Unknown track"),
      artist: String(track.artist?.name || track.artist?.["#text"] || "Unknown artist"),
      plays: Number(track.playcount) || 0,
      image: lastfmArtwork(track.image) || lastfmArtwork(track.album?.image),
      url: String(track.url || "")
    }));
  }

  function parseLastfmTopArtists(payload) {
    const entries = payload?.topartists?.artist;
    const artists = Array.isArray(entries) ? entries : entries ? [entries] : [];
    return artists.map((artist) => ({
      name: String(artist.name || "Unknown artist"),
      plays: Number(artist.playcount) || 0,
      image: lastfmArtwork(artist.image),
      url: String(artist.url || "")
    }));
  }

  function trackArtworkKey(track) {
    return [track.artistName || track.artist || "", track.trackName || track.name || ""]
      .map((part) => String(part).trim().toLocaleLowerCase())
      .join("\u0000");
  }

  async function fillRecentArtwork(recentTracks, topTracks, topArtists, apiKey) {
    const topArtwork = new Map(topTracks.map((track) => [trackArtworkKey(track), track.image]).filter(([, image]) => image));
    const artistArtwork = new Map(topArtists.map((artist) => [artist.name.trim().toLocaleLowerCase(), artist.image]).filter(([, image]) => image));
    recentTracks.forEach((track) => {
      const knownArtwork = topArtwork.get(trackArtworkKey(track));
      if (!track.image && knownArtwork) track.image = knownArtwork;
      const knownArtistArtwork = artistArtwork.get(track.artistName.trim().toLocaleLowerCase());
      if (!track.artistImage && knownArtistArtwork) track.artistImage = knownArtistArtwork;
    });

    const candidates = new Map();
    recentTracks.slice().sort((a, b) => b.ts - a.ts).forEach((track) => {
      const key = trackArtworkKey(track);
      if (!track.image && !candidates.has(key)) candidates.set(key, track);
    });
    const newestMissing = Array.from(candidates.entries()).slice(0, 3);
    await Promise.all(newestMissing.map(async ([key, track]) => {
      let image = lastfmTrackArtwork.get(key) || "";
      if (!lastfmTrackArtwork.has(key)) {
        try {
          const payload = await lastfmApi("track.getInfo", "", apiKey, {
            artist: track.artistName,
            track: track.trackName
          });
          image = lastfmArtwork(payload?.track?.album?.image) || lastfmArtwork(payload?.track?.image);
        } catch {
          image = "";
        }
        lastfmTrackArtwork.set(key, image);
      }
      if (!image) return;
      recentTracks.forEach((recent) => {
        if (!recent.image && trackArtworkKey(recent) === key) recent.image = image;
      });
      topTracks.forEach((top) => {
        if (!top.image && trackArtworkKey(top) === key) top.image = image;
      });
    }));
  }

  async function fetchLastfmData(username, apiKey) {
    const now = Date.now();
    const from = state.range === "all_time" ? 1 : Math.floor((now - RANGE_MS[state.range]) / 1000);
    const to = Math.floor(now / 1000);
    const recentParams = { limit: 200, from, to };
    const recentPromise = lastfmApi("user.getrecenttracks", username, apiKey, recentParams);
    const periods = { day: "7day", week: "7day", month: "1month", year: "12month", all_time: "overall" };
    const topPromise = Promise.all([
      lastfmApi("user.gettoptracks", username, apiKey, { period: periods[state.range], limit: 50 }),
      lastfmApi("user.gettopartists", username, apiKey, { period: periods[state.range], limit: 50 })
    ]);
    const [recentPayload, topResults] = await Promise.all([recentPromise, topPromise]);
    const recentTracks = parseLastfmTracks(recentPayload);
    const tracks = parseLastfmTopTracks(topResults[0]);
    const artists = parseLastfmTopArtists(topResults[1]);
    await fillRecentArtwork(recentTracks, tracks, artists, apiKey);
    const unique = new Map();
    recentTracks.forEach((row) => {
      const key = [row.ts, row.trackName.toLowerCase(), row.artistName.toLowerCase()].join("\u0000");
      unique.set(key, row);
    });
    const rows = Array.from(unique.values()).sort((a, b) => a.ts - b.ts);
    const totalCount = Number(recentPayload.recenttracks?.["@attr"]?.total) || rows.length;
    const recentRankings = historyRankings(rows);
    return {
      rows,
      totalCount,
      truncated: totalCount > rows.length,
      tracks: state.range === "day" ? recentRankings.tracks : tracks,
      artists: state.range === "day" ? recentRankings.artists : artists
    };
  }

  async function connectLastfm() {
    const usernameInput = $("#lastfmUsername");
    const username = usernameInput.value.trim();
    const apiKeyInput = $("#lastfmApiKey");
    const suppliedKey = apiKeyInput.value.trim();
    if (!username) {
      usernameInput.focus();
      showToast("Enter your Last.fm username to load your scrobbles.");
      return;
    }
    if (suppliedKey) localStorage.setItem(LASTFM_API_KEY_STORAGE, suppliedKey);
    const apiKey = suppliedKey || getLastfmApiKey();
    if (!apiKey) {
      $("#ownerSetup").open = true;
      apiKeyInput.focus();
      showToast("The website owner needs to add the free Last.fm API key first.");
      return;
    }

    await loadLastfmData(username);
  }

  async function loadLastfmData(requestedUsername) {
    const refresh = $("#refreshButton");
    const username = String(requestedUsername || state.lastfmUsername ||
      localStorage.getItem(LASTFM_USERNAME_KEY) || "").trim();
    const apiKey = getLastfmApiKey();
    if (!username || !apiKey) {
      setNotice("Add your Last.fm username and the site owner's API key to load listening stats.", true);
      return;
    }
    refresh.classList.add("is-loading");
    refresh.disabled = true;
    setNotice("");
    try {
      const result = await fetchLastfmData(username, apiKey);
      state.lastfmRows = result.rows;
      state.lastfmTotalCount = result.totalCount;
      state.lastfmTopTracks = result.tracks;
      state.lastfmTopArtists = result.artists;
      state.lastfmTruncated = result.truncated;
      state.lastfmUsername = username;
      state.profile = { display_name: username };
      state.mode = "lastfm";
      state.nowPlaying = null;
      try { localStorage.setItem(LASTFM_USERNAME_KEY, username); }
      catch { /* Keep this session connected if browser storage is unavailable. */ }
      if ($("#connectDialog").open) $("#connectDialog").close();
      try {
        localStorage.setItem(LASTFM_CACHE_KEY, JSON.stringify({
          username, range: state.range, rows: state.lastfmRows,
          totalCount: state.lastfmTotalCount, tracks: state.lastfmTopTracks,
          artists: state.lastfmTopArtists, truncated: state.lastfmTruncated,
          savedAt: Date.now()
        }));
      } catch { /* Keep using the live response if browser storage is full. */ }
      render();
      // Fill missing covers and faces in the background so the stats show instantly.
      enrichMissingArtwork(state.lastfmTopTracks, state.lastfmTopArtists, state.lastfmRows).then(() => {
        try {
          localStorage.setItem(LASTFM_CACHE_KEY, JSON.stringify({
            username, range: state.range, rows: state.lastfmRows,
            totalCount: state.lastfmTotalCount, tracks: state.lastfmTopTracks,
            artists: state.lastfmTopArtists, truncated: state.lastfmTruncated,
            savedAt: Date.now()
          }));
        } catch { /* Keep using the live response if browser storage is full. */ }
        hydrateArtworkImages();
      });
      if (state.lastfmTruncated) {
        const cappedNote = state.range === "day"
          ? "The 24-hour rankings and activity use up to the latest 200 scrobbles."
          : "The activity breakdown uses the latest 200; the top lists use Last.fm's period charts.";
        setNotice("Last.fm reports " + formatNumber(state.lastfmTotalCount) + " scrobbles for this period. " + cappedNote);
      }
      if (!state.lastfmRows.length) {
        showToast("No scrobbles found in this period yet. Last.fm will add them after Spotify is linked there.");
      } else {
        showToast("Loaded " + formatNumber(state.lastfmRows.length) + " Last.fm scrobbles.");
      }
    } catch (error) {
      setNotice(error.message || "Could not load Last.fm data.", true);
    } finally {
      refresh.classList.remove("is-loading");
      refresh.disabled = false;
    }
  }

  function estimateLastfmTime() {
    if (!state.lastfmTotalCount) return "≈ 0 MIN";
    return "≈ " + formatDuration(state.lastfmTotalCount * 3.5 * 60000).toUpperCase();
  }

  function historyTime() {
    const rows = currentHistoryRows() || [];
    return formatDuration(rows.reduce((sum, row) => sum + (row.msPlayed || 0), 0)).toUpperCase();
  }

  function currentHistoryRows() {
    if (!state.history) return null;
    const cutoff = state.range === "all_time" ? -Infinity : Date.now() - RANGE_MS[state.range];
    return state.history.filter((row) => row.ts >= cutoff);
  }

  function historyRankings(rows) {
    const trackCounts = new Map();
    const artistCounts = new Map();
    rows.forEach((row) => {
      const trackKey = row.trackName.toLowerCase() + "\u0000" + row.artistName.toLowerCase();
      const track = trackCounts.get(trackKey) || {
        name: row.trackName, artist: row.artistName, plays: 0,
        duration_ms: 0, image: row.image || "", url: row.url || ""
      };
      if (!track.image && row.image) track.image = row.image;
      track.plays += 1;
      track.duration_ms += row.msPlayed;
      trackCounts.set(trackKey, track);

      const artistKey = row.artistName.toLowerCase();
      const artist = artistCounts.get(artistKey) || {
        name: row.artistName, plays: 0, image: row.artistImage || ""
      };
      if (!artist.image && row.artistImage) artist.image = row.artistImage;
      artist.plays += 1;
      artistCounts.set(artistKey, artist);
    });
    return {
      tracks: Array.from(trackCounts.values()).sort((a, b) => b.plays - a.plays),
      artists: Array.from(artistCounts.values()).sort((a, b) => b.plays - a.plays)
    };
  }

  function insightData() {
    if (state.mode === "lastfm") {
      return {
        tracks: state.lastfmTopTracks,
        artists: state.lastfmTopArtists,
        recent: state.lastfmRows.slice().sort((a, b) => b.ts - a.ts),
        historyRows: state.lastfmRows,
        trackingRows: state.lastfmRows,
        activityRows: state.lastfmRows,
        allHistory: true,
        source: "lastfm"
      };
    }
    if (state.history) {
      const rows = currentHistoryRows();
      const ranked = historyRankings(rows);
      return {
        tracks: ranked.tracks,
        artists: ranked.artists,
        recent: rows.slice().sort((a, b) => b.ts - a.ts),
        historyRows: rows,
        trackingRows: rows,
        activityRows: rows,
        allHistory: true
      };
    }
    return {
      tracks: state.tracks,
      artists: state.artists,
      recent: state.recent,
      trackingRows: [],
      activityRows: state.recent,
      historyRows: null,
      allHistory: false
    };
  }

  function statCard(label, value, note, icon, tone = "") {
    const text = String(value ?? "");
    const countMatch = text.match(/^([\d,]+)((?:\s+[a-z]+)?)$/i);
    const countAttrs = countMatch
      ? ' data-count="' + countMatch[1].replace(/,/g, "") + '" data-suffix="' + escapeHtml(countMatch[2] ? " " + countMatch[2].trim() : "") + '"'
      : "";
    return '<article class="stat-card ' + tone + '">' +
      '<span class="stat-label"><span class="stat-icon">' + iconSvg(icon) + '</span>' + escapeHtml(label) + '</span>' +
      '<strong class="stat-value" title="' + escapeHtml(value) + '"' + countAttrs + '>' + escapeHtml(value) + '</strong>' +
      '<span class="stat-note">' + escapeHtml(note) + '</span></article>';
  }

  function getStats(data) {
    if (data.source === "lastfm") {
      return [
        statCard("Scrobbles", formatNumber(state.lastfmTotalCount), RANGE_LABEL[state.range] + " · Last.fm total", "♫", "is-pink"),
        statCard("Top tracks", formatNumber(data.tracks.length), "Track rankings returned by Last.fm", "✳"),
        statCard("Top artists", formatNumber(data.artists.length), "Artist rankings returned by Last.fm", "◷"),
        statCard("Listening time", "≈ " + formatDuration(state.lastfmTotalCount * 3.5 * 60000), RANGE_LABEL[state.range] + " · estimated", "◷", "is-dark")
      ].join("");
    }

    if (data.allHistory) {
      const rows = data.historyRows;
      const totalMs = rows.reduce((sum, row) => sum + row.msPlayed, 0);
      const distinctTracks = new Set(rows.map((row) => row.trackName + "\u0000" + row.artistName)).size;
      const peakHour = getPeakHour(rows);
      return [
        statCard("Time in music", formatDuration(totalMs), "Sum of msPlayed in this export", "◷", "is-pink"),
        statCard("Play entries", formatNumber(rows.length), "Rows in your selected date range", "♫"),
        statCard("Tracks explored", formatNumber(distinctTracks), "Distinct track and artist pairs", "✳"),
        statCard("Peak hour", peakHour || "No plays yet", "Your busiest listening hour", "↗", "is-dark")
      ].join("");
    }

    if (state.mode === "demo") {
      const flavors = new Set((data.artists || []).flatMap((artist) => artist.genres || [])).size;
      return [
        statCard("Time in music", "86 hr", "Illustrative sample · 6 month view", "◷", "is-pink"),
        statCard("Play entries", "1,284", "Illustrative sample listening log", "♫"),
        statCard("Tracks explored", "472", "Illustrative sample collection", "✳"),
        statCard("Top flavors", formatNumber(flavors), "Distinct genre tags in this sample", "✳", "is-dark")
      ].join("");
    }

    return "";
  }

  function buildRecentSeries(data) {
    const now = new Date();
    const days = [];
    for (let offset = 6; offset >= 0; offset -= 1) {
      const date = new Date(now);
      date.setHours(0, 0, 0, 0);
      date.setDate(date.getDate() - offset);
      days.push({ date, amount: 0, count: 0 });
    }
    if (state.mode === "demo") {
      return days.map((day, index) => ({ ...day, amount: SAMPLE_DAYS[index] }));
    }
    const seriesRows = data.trackingRows?.length ? data.trackingRows : data.recent;
    seriesRows.forEach((row) => {
      const stamp = new Date(row.ts);
      const match = days.find((day) => day.date.toDateString() === stamp.toDateString());
      if (match) {
        match.count += 1;
        // Last.fm returns scrobble timestamps, not listen duration. Count each
        // row as one scrobble rather than inventing a duration.
        match.amount += (state.history && state.mode !== "lastfm" && row.msPlayed) ? row.msPlayed : 1;
      }
    });
    return days;
  }

  function chartMarkup(data) {
    const days = buildRecentSeries(data);
    const max = Math.max(1, ...days.map((day) => day.amount));
    const hasData = days.some((day) => day.amount > 0);
    const bars = days.map((day) => {
      const height = hasData ? Math.max(5, Math.round((day.amount / max) * 100)) : 4;
      const dayName = new Intl.DateTimeFormat(undefined, { weekday: "short" }).format(day.date);
      const title = state.mode === "demo"
        ? dayName + ": sample listening time"
        : state.mode === "lastfm"
          ? dayName + ": " + day.count + " scrobbles"
          : dayName + ": " + day.count + " recent plays";
      return '<div class="chart-column" title="' + escapeHtml(title) + '">' +
        '<i class="chart-bar" style="height:' + height + '%"></i>' +
        '<span class="chart-day">' + escapeHtml(dayName.slice(0, 1)) + '</span></div>';
    }).join("");
    const caption = state.mode === "demo"
      ? "PREVIEW RHYTHM · SAMPLE DATA"
      : state.mode === "lastfm"
        ? "SCROBBLES · LAST 7 DAYS"
        : state.history
        ? "LISTENING TIME · LAST 7 DAYS"
        : "SCROBBLES · CONNECT SPOTIFY TO BEGIN";
    return '<div class="activity-chart" role="img" aria-label="Listening activity over the last seven days">' +
      (hasData || state.mode === "demo" ? bars : '<span class="chart-empty">No recent activity in the available data.</span>') +
      '</div><div class="chart-caption"><span>' + caption + '</span><strong>' +
      (state.history && state.mode !== "lastfm" ? "time listened" : state.mode === "demo" ? "sample hours" : "scrobbles") +
      '</strong></div>';
  }

  function currentFavoriteText(data) {
    if (state.nowPlaying?.item) {
      const item = state.nowPlaying.item;
      return {
        kicker: state.nowPlaying.is_playing ? "ON THE TURNTABLE NOW" : "LAST ON THE TURNTABLE",
        title: item.name || "Currently playing",
        detail: (item.artists || []).map((artist) => artist.name).join(", ") || "Spotify"
      };
    }
    if (data.artists[0]) {
      return {
        kicker: "YOUR MOST PLAYED ARTIST",
        title: data.artists[0].name,
        detail: state.mode === "lastfm"
          ? formatNumber(data.artists[0].plays) + " scrobbles in this date range"
          : state.history
          ? formatNumber(data.artists[0].plays) + " play entries in this date range"
          : state.mode === "demo" ? "A fictional preview favorite" : RANGE_LABEL[state.range]
      };
    }
    return { kicker: "YOUR NEXT FAVORITE", title: "Waiting for a little music", detail: "Connect Spotify or import a history export." };
  }

  function trackRows(tracks, limit = 5, recentMode = false) {
    if (!tracks.length) return '<p class="empty-copy">No track rows in this view yet. Try another time range or wait for Last.fm to record a listen.</p>';
    return '<div class="' + (recentMode ? "recent-list" : "track-list") + '">' +
      tracks.slice(0, limit).map((track, index) => {
        const title = track.name || track.trackName || "Unknown track";
        const artist = track.artist || track.artistName || "Unknown artist";
        const image = secureArtworkUrl(track.image || track.album?.images?.[0]?.url || "");
        const initial = escapeHtml(title.slice(0, 1).toUpperCase());
        const coverImage = image
          ? '<img src="' + escapeHtml(image) + '" alt="" loading="lazy" referrerpolicy="no-referrer" onerror="this.parentElement.classList.add(\'is-missing\');this.remove()" />'
          : "";
        const cover = '<span class="cover-art' + (image ? "" : " is-missing") + '" data-artist="' + escapeHtml(artist) + '" data-track="' + escapeHtml(title) + '">' + coverImage +
          '<span class="cover-fallback" aria-hidden="true">' + initial + '</span></span>';
        if (recentMode) {
          return '<div class="recent-row">' + cover +
            '<span class="recent-meta"><strong>' + escapeHtml(title) + '</strong><span>' + escapeHtml(artist) + '</span></span>' +
            '<span class="recent-time">' + escapeHtml(relativeTime(track.ts)) + '</span></div>';
        }
        const duration = state.history || state.mode === "lastfm"
          ? (track.plays || 0) + (state.mode === "lastfm" ? " scrobbles" : (track.plays || 0) === 1 ? " play" : " plays")
          : track.duration_ms ? formatDuration(track.duration_ms) : (track.plays || "");
        return '<div class="track-row"><span class="track-rank"><span>' + String(index + 1).padStart(2, "0") + '</span>' +
          '</span>' +
          cover + '<span class="track-copy"><span class="track-name">' + escapeHtml(title) +
          '</span><span class="track-artist">' + escapeHtml(artist) + '</span></span>' +
          '<span class="track-duration">' + escapeHtml(duration) + '</span></div>';
      }).join("") + '</div>';
  }

  function artistCards(artists, limit = 5) {
    if (!artists.length) return '<p class="empty-copy">No artist data for this range yet. Try another range or wait for Last.fm to record a listen.</p>';
    return '<div class="artist-grid">' + artists.slice(0, limit).map((artist, index) => {
      const image = secureArtworkUrl(artist.image || artist.images?.[0]?.url || "");
      const initial = escapeHtml((artist.name || "?").slice(0, 1).toUpperCase());
      const artImage = image
        ? '<img src="' + escapeHtml(image) + '" alt="" loading="lazy" referrerpolicy="no-referrer" onerror="this.parentElement.classList.add(\'is-missing\');this.remove()" />'
        : "";
      const colorStyle = image ? "" : ' style="--artist-a:' + ARTIST_COLORS[index % ARTIST_COLORS.length] + '"';
      const art = '<span class="artist-art' + (image ? "" : " is-missing") + '"' + colorStyle + ' data-artist="' + escapeHtml(artist.name || "") + '">' + artImage +
        '<span class="artist-fallback" aria-hidden="true">' + initial + '</span></span>';
      const subtitle = state.history || state.mode === "lastfm"
        ? formatNumber(artist.plays || 0) + (state.mode === "lastfm" ? " scrobbles" : " play entries")
        : (artist.genres || []).slice(0, 1).join("") || "Top artist";
      return '<article class="artist-card">' + art +
        '<span class="artist-name" title="' + escapeHtml(artist.name) + '">' + escapeHtml(artist.name) + '</span>' +
        '<span class="artist-rank">' + escapeHtml(subtitle) + '</span></article>';
    }).join("") + '</div>';
  }

  function overviewMarkup(data) {
    const favorite = currentFavoriteText(data);
    return '<div class="stats-grid">' + getStats(data) + '</div>' +
      '<div class="insight-grid">' +
        '<section class="panel"><div class="panel-heading"><div><h3>Seven little days</h3><p>Recent listening, one day at a time</p></div>' +
        '<button class="subtle-link" type="button" data-view="activity">See activity ↗</button></div>' +
        chartMarkup(data) + '</section>' +
        '<section class="panel listening-note"><span class="note-top">' + escapeHtml(favorite.kicker) + '</span>' +
        '<strong class="note-artist" title="' + escapeHtml(favorite.title) + '">' + escapeHtml(favorite.title) + '</strong>' +
        '<span class="note-bottom"><span>' + escapeHtml(favorite.detail) + '</span><i class="note-disc" aria-hidden="true">♫</i></span></section>' +
      '</div>' +
      '<div class="lower-grid">' +
        '<section class="panel"><div class="panel-heading"><div><h3>On repeat</h3><p>Your most-played tracks in this view</p></div>' +
        '<button class="subtle-link" type="button" data-view="tracks">All tracks ↗</button></div>' +
        trackRows(data.tracks, 5) + '</section>' +
        '<section class="panel"><div class="panel-heading"><div><h3>Familiar faces</h3><p>Artists showing up in your listening</p></div>' +
        '<button class="subtle-link" type="button" data-view="artists">All artists ↗</button></div>' +
        artistCards(data.artists, 5) + '</section>' +
      '</div>';
  }

  function tracksMarkup(data) {
    const range = state.range;
    const subtitle = data.source === "lastfm"
      ? "Ranked by Last.fm scrobbles for " + RANGE_LABEL[state.range]
      : state.history ? "Ranked by play entries in the imported history" : "Illustrative preview rankings for " + RANGE_LABEL[range];
    return '<section class="panel"><div class="panel-heading"><div><h3>Your tracks, in order</h3>' +
      '<p>' + subtitle + '</p></div>' +
      '<span class="eyebrow">TOP ' + Math.min(data.tracks.length, 50) + '</span></div>' +
      trackRows(data.tracks, 50) + '</section>';
  }

  function artistsMarkup(data) {
    const range = state.range;
    const subtitle = data.source === "lastfm"
      ? "Ranked by Last.fm scrobbles for " + RANGE_LABEL[state.range]
      : state.history ? "Ranked by play entries in the imported history" : "Illustrative preview rankings for " + RANGE_LABEL[range];
    return '<section class="panel"><div class="panel-heading"><div><h3>Your familiar faces</h3>' +
      '<p>' + subtitle + '</p></div>' +
      '<span class="eyebrow">TOP ' + Math.min(data.artists.length, 50) + '</span></div>' +
      artistCards(data.artists, 50) + '</section>';
  }

  const artistTagCache = new Map();
  const JUNK_TAGS = new Set([
    "seen live", "favorites", "favourites", "favorite", "favourite", "love", "loved",
    "awesome", "amazing", "best", "good", "great", "underrated", "guilty pleasure",
    "guilty pleasures", "00s", "10s", "20s", "30s", "40s", "50s", "60s", "70s", "80s", "90s"
  ]);

  async function fetchArtistTags(name) {
    const key = String(name || "").trim().toLocaleLowerCase();
    if (!key) return [];
    if (artistTagCache.has(key)) return artistTagCache.get(key);
    const apiKey = getLastfmApiKey();
    if (!apiKey) return [];
    const payload = await lastfmApi("artist.getinfo", "", apiKey, { artist: String(name), autocorrect: 1 });
    const tags = payload?.artist?.tags?.tag;
    const list = (Array.isArray(tags) ? tags : tags ? [tags] : [])
      .map((tag) => String(tag?.name || "").trim().toLocaleLowerCase())
      .filter(Boolean);
    artistTagCache.set(key, list);
    return list;
  }

  async function loadGenres() {
    const key = state.lastfmUsername + "|" + state.range;
    if (state.genreKey === key && (state.genreStatus === "loading" || state.genreStatus === "ready")) return;
    state.genreKey = key;
    state.genreStatus = "loading";
    state.genres = [];
    try {
      const artists = state.lastfmTopArtists.slice(0, 14);
      const weights = new Map();
      await mapWithConcurrency(artists, 4, async (artist) => {
        let tags = [];
        try {
          tags = await fetchArtistTags(artist.name);
        } catch {
          tags = [];
        }
        tags.slice(0, 4).filter((tag) => !JUNK_TAGS.has(tag)).slice(0, 3).forEach((tag) => {
          weights.set(tag, (weights.get(tag) || 0) + (artist.plays || 1));
        });
      });
      const bubbles = Array.from(weights.entries())
        .map(([name, value]) => ({ name, value }))
        .sort((a, b) => b.value - a.value)
        .slice(0, 14);
      if (state.genreKey !== key) return;
      state.genres = bubbles;
      state.genreStatus = bubbles.length ? "ready" : "empty";
    } catch {
      if (state.genreKey !== key) return;
      state.genreStatus = "error";
    }
    if (state.view === "galaxy") render();
  }

  function packBubbles(items, width, height) {
    const total = items.reduce((sum, item) => sum + item.value, 0) || 1;
    const factor = Math.sqrt((0.5 * width * height) / (Math.PI * total));
    const placed = [];
    items.forEach((item, index) => {
      const fullRadius = Math.min(160, Math.max(44, Math.sqrt(item.value) * factor));
      if (!index) {
        placed.push({ ...item, x: width / 2, y: height / 2, r: fullRadius });
        return;
      }
      for (const scale of [1, 0.7, 0.5]) {
        const radius = fullRadius * scale;
        let angle = index * 2.39996;
        let dist = placed[0].r + radius + 10;
        for (let step = 0; step < 500; step++) {
          const x = width / 2 + Math.cos(angle) * dist;
          const y = height / 2 + Math.sin(angle) * dist * 0.72;
          const fits = x - radius > 4 && x + radius < width - 4 && y - radius > 4 && y + radius < height - 4 &&
            placed.every((other) => Math.hypot(other.x - x, other.y - y) > other.r + radius + 8);
          if (fits) {
            placed.push({ ...item, x, y, r: radius });
            return;
          }
          angle += 0.35;
          dist += 2.4;
        }
      }
    });
    return placed;
  }

  function bubbleColor(index) {
    const accent = cssVar("--pink") || "#939393";
    const ink = cssVar("--ink") || "#1c1c1c";
    if (!index) return hexToRgba(accent, 0.92);
    const alphas = [0.82, 0.72, 0.64, 0.58, 0.52, 0.48, 0.44];
    return hexToRgba(ink, alphas[Math.min(index, alphas.length) - 1] ?? 0.42);
  }

  function galaxyBubblesMarkup(bubbles, note) {
    const placed = packBubbles(bubbles, 1000, 625);
    const max = Math.max(1, ...bubbles.map((item) => item.value));
    const dots = placed.map((item, index) => {
      const left = ((item.x - item.r) / 1000) * 100;
      const top = ((item.y - item.r) / 625) * 100;
      const size = (item.r * 2 / 1000) * 100;
      return '<div class="bubble" data-name="' + escapeHtml(item.name) + '" data-value="' + item.value + '" style="left:' + left.toFixed(2) + '%;top:' + top.toFixed(2) +
        '%;width:' + size.toFixed(2) + '%;aspect-ratio:1;background:' + bubbleColor(index) +
        ';animation-delay:' + (index * 70) + 'ms"><span><strong>' + escapeHtml(item.name) +
        '</strong><small>' + formatNumber(item.value) + (max > 20 ? " plays" : "") + '</small></span></div>';
    }).join("");
    return '<section class="panel"><div class="panel-heading"><div><h3>A galaxy of genres</h3>' +
      '<p>' + escapeHtml(note) + '</p></div>' +
      '<span class="eyebrow">TOP ' + bubbles.length + '</span></div>' +
      '<p class="galaxy-caption">Tap a flavor to spotlight it.</p>' +
      '<div class="galaxy-wrap">' + dots + '</div></section>';
  }

  function galaxyMarkup(data) {
    if (state.mode === "demo" && !state.history) {
      const demo = (data.artists || []).slice(0, 5).map((artist, index) => ({
        name: (artist.genres || ["top artist"])[0],
        value: 5 - index
      }));
      return galaxyBubblesMarkup(demo, "Illustrative preview flavors · fictional sample");
    }
    if (state.mode !== "lastfm") {
      return '<section class="panel"><div class="panel-heading"><div><h3>A galaxy of genres</h3>' +
        '<p>Genre flavors come from Last.fm tags on your top artists.</p></div></div>' +
        '<p class="empty-copy">Connect Spotify through Last.fm to map your genre universe.</p></section>';
    }
    const key = state.lastfmUsername + "|" + state.range;
    if (state.genreKey !== key || state.genreStatus === "idle") {
      loadGenres();
      return '<section class="panel"><div class="panel-heading"><div><h3>A galaxy of genres</h3>' +
        '<p>Reading tags for your top artists…</p></div></div>' +
        '<p class="empty-copy">Mapping your universe. This takes a few seconds the first time.</p></section>';
    }
    if (state.genreStatus === "loading") {
      return '<section class="panel"><div class="panel-heading"><div><h3>A galaxy of genres</h3>' +
        '<p>Reading tags for your top artists…</p></div></div>' +
        '<p class="empty-copy">Mapping your universe. This takes a few seconds the first time.</p></section>';
    }
    if (state.genreStatus === "ready" && state.genres.length) {
      return galaxyBubblesMarkup(state.genres, "Sized by scrobbles behind each flavor · " + RANGE_LABEL[state.range]);
    }
    return '<section class="panel"><div class="panel-heading"><div><h3>A galaxy of genres</h3>' +
      '<p>No genre tags found for this range yet.</p></div></div>' +
      '<p class="empty-copy">Try another time range. Obscure artists sometimes have no tags on Last.fm.</p></section>';
  }

  function getPeakHour(rows) {
    if (!rows.length) return "";
    const counts = new Array(24).fill(0);
    rows.forEach((row) => { counts[new Date(row.ts).getHours()] += row.msPlayed || 1; });
    const hour = counts.indexOf(Math.max(...counts));
    return new Intl.DateTimeFormat(undefined, { hour: "numeric" }).format(new Date(2020, 0, 1, hour));
  }

  function weekdayBreakdown(rows) {
    const counts = new Array(7).fill(0);
    rows.forEach((row) => { counts[new Date(row.ts).getDay()] += state.mode === "lastfm" ? 1 : row.msPlayed || 1; });
    const max = Math.max(1, ...counts);
    const labels = ["S", "M", "T", "W", "T", "F", "S"];
    return '<div class="weekday-grid">' + labels.map((label, index) => {
      const level = counts[index] ? Math.min(4, Math.ceil((counts[index] / max) * 4)) : 0;
      const fullName = new Intl.DateTimeFormat(undefined, { weekday: "long" }).format(new Date(2020, 0, 5 + index));
      const unit = state.mode === "lastfm" ? " scrobbles" : " ms of listening";
      return '<div class="weekday-cell" title="' + fullName + ': ' + formatNumber(counts[index]) + unit + '">' +
        '<span>' + label + '</span><i data-level="' + level + '"></i></div>';
    }).join("") + '</div>';
  }

  function hourlyBreakdown(rows) {
    const counts = new Array(24).fill(0);
    rows.forEach((row) => { counts[new Date(row.ts).getHours()] += state.mode === "lastfm" ? 1 : row.msPlayed || 1; });
    const max = Math.max(1, ...counts);
    return '<div class="hourly-bars" role="img" aria-label="Listening by hour of day">' +
      counts.map((value, index) => '<i title="' + index + ':00 · ' + formatNumber(value) +
        (state.history && state.mode !== "lastfm" ? ' ms played' : ' scrobbles') + '" style="height:' +
        Math.max(value ? 5 : 2, Math.round((value / max) * 100)) + '%"></i>').join("") + '</div>';
  }

  function activityMarkup(data) {
    const rows = data.activityRows || data.recent;
    const sourceNote = state.mode === "lastfm"
      ? formatNumber(state.lastfmRows.length) + " recent scrobbles shown · " + formatNumber(state.lastfmTotalCount) + " in " + RANGE_LABEL[state.range].toLowerCase()
      : state.history
      ? formatNumber(rows.length) + " entries from your imported export · " + RANGE_LABEL[state.range].toLowerCase()
      : state.mode === "demo"
        ? "Illustrative preview activity · fictional timestamps"
        : "Connect Spotify through Last.fm to see your listening activity";
    const weekRows = state.mode === "demo" ? SAMPLE.recent.map((row, index) => ({
      ...row, ts: Date.now() - index * 47 * 60000, msPlayed: row.msPlayed
    })) : rows.slice().sort((a, b) => b.ts - a.ts);
    return '<div class="activity-layout">' +
      '<section class="panel"><div class="panel-heading"><div><h3>When the needle drops</h3><p>' + escapeHtml(sourceNote) + '</p></div></div>' +
      '<p class="eyebrow">LISTENING BY DAY OF THE WEEK</p>' +
      (state.history || data.trackingRows?.length || state.mode === "lastfm" ? weekdayBreakdown(rows) : '<div class="weekday-grid">' +
        ["S", "M", "T", "W", "T", "F", "S"].map((day, i) => '<div class="weekday-cell"><span>' + day +
          '</span><i data-level="' + ((i * 3 + 1) % 5) + '"></i></div>').join("") + '</div>') +
      '<p class="eyebrow">LISTENING BY HOUR</p>' +
      (state.history || data.trackingRows?.length || state.mode === "lastfm" ? hourlyBreakdown(rows) : state.mode === "demo"
        ? '<div class="hourly-bars">' + Array.from({ length: 24 }, (_, i) => '<i style="height:' +
          (12 + ((i * 37) % 88)) + '%"></i>').join("") + '</div>'
        : '<p class="empty-copy">Import your listening-history export to see your full hour-by-hour pattern.</p>') +
      '</section>' +
      '<section class="panel"><div class="panel-heading"><div><h3>The last few songs</h3><p>Most recent items we can see</p></div></div>' +
      trackRows(weekRows, 8, true) + '</section>' +
      '</div>';
  }

  function render() {
    const data = insightData();
    const content = $("#screenContent");
    const titles = {
      overview: ["A portrait in plays", "YOUR LISTENING, IN FULL COLOR"],
      tracks: ["The tracks you return to", "THE SONGS THAT STAY"],
      artists: ["The artists in your orbit", "THE VOICES IN YOUR ROOM"],
      galaxy: ["Your universe of sound", "A GALAXY OF GENRES"],
      activity: ["Your listening, over time", "A DIARY IN LITTLE MOMENTS"]
    };
    $("#sectionTitle").textContent = titles[state.view][0];
    $("#heroKicker").textContent = titles[state.view][1];
    $$(".nav-link").forEach((button) => button.classList.toggle("is-active", button.dataset.view === state.view));

    const isPreview = state.mode === "demo" && !state.history;
    $("#demoRibbon").hidden = !isPreview;
    const isConnected = state.mode === "lastfm";
    $("#connectionLabel").classList.toggle("is-connected", isConnected);
    $("#disconnectButton").hidden = !isConnected;
    $("#disconnectButton").textContent = "Forget username";
    $("#connectionLabel").innerHTML = state.mode === "lastfm"
      ? '<i></i> Last.fm · ' + escapeHtml(state.lastfmUsername)
      : state.history ? '<i></i> History loaded' : '<i></i> Preview mode';
    $("#connectButton").innerHTML = isConnected
      ? 'Spotify via Last.fm <span aria-hidden="true">✓</span>'
      : 'Log in with Spotify <span aria-hidden="true">↗</span>';
    $("#heroDescription").textContent = state.profile?.display_name
      ? "A little room for the songs you keep coming back to, " + state.profile.display_name + "."
        : state.mode === "lastfm"
          ? "A little room for the songs you have listened to, " + state.lastfmUsername + "."
        : state.history
        ? "A little room for the songs you have kept close over time."
        : "A little room for the songs you keep coming back to.";
    $("#dataStamp").innerHTML = state.mode === "lastfm"
      ? "LAST.FM<br />" + formatNumber(state.lastfmTotalCount) + " SCROBBLES<br />" + estimateLastfmTime()
      : state.history ? "HISTORY<br />" + formatNumber(state.history.length) + " ENTRIES<br />" + historyTime() : "SAMPLE<br />LISTENING DATA";
    $("#clearHistoryButton").hidden = !state.history;
    $$("[data-range]").forEach((button) => {
      button.hidden = button.dataset.range === "all_time" && !state.history && state.mode !== "lastfm";
      if (button.dataset.range === "all_time") button.textContent = state.history ? "All history" : state.mode === "lastfm" ? "All time" : "All tracked";
      button.classList.toggle("is-selected", button.dataset.range === state.range);
    });

    const mini = $("#miniNowPlaying");
    const miniState = $("#miniTrackState");
    const miniName = $("#miniTrackName");
    const miniArtist = $("#miniTrackArtist");
    const playing = state.nowPlaying?.item;
    mini.classList.toggle("is-live", Boolean(state.nowPlaying?.is_playing && playing));
    if (playing) {
      miniState.textContent = state.nowPlaying.is_playing ? "NOW PLAYING ON SPOTIFY" : "ON YOUR SPOTIFY";
      miniName.textContent = playing.name || "Untitled track";
      miniArtist.textContent = (playing.artists || []).map((artist) => artist.name).join(", ") || "Spotify";
    } else if (state.mode === "lastfm") {
      const latest = state.lastfmRows.slice().sort((a, b) => b.ts - a.ts)[0];
      miniState.textContent = "LAST.FM IS TRACKING";
      miniName.textContent = latest?.trackName || "Waiting for your first scrobble";
      miniArtist.textContent = latest ? latest.artistName + " · " + relativeTime(latest.ts) : "Spotify listens appear after Last.fm records them";
    } else if (state.history) {
      miniState.textContent = "YOUR ARCHIVE IS IN";
      miniName.textContent = formatNumber(state.history.length) + " play entries";
      miniArtist.textContent = "A little history, all in one room";
    } else {
      miniState.textContent = "YOUR ROOM IS READY";
      miniName.textContent = "A little more you";
      miniArtist.textContent = "Connect Spotify to tune in";
    }

    if (state.view === "overview") content.innerHTML = overviewMarkup(data);
    if (state.view === "tracks") content.innerHTML = tracksMarkup(data);
    if (state.view === "artists") content.innerHTML = artistsMarkup(data);
    if (state.view === "galaxy") content.innerHTML = galaxyMarkup(data);
    if (state.view === "activity") content.innerHTML = activityMarkup(data);
    bindDynamicActions();
    hydrateArtworkImages();
    moveNavIndicator();
    moveRangeIndicator();
    animateStatCounts();
  }

  function syncThemeColor() {
    const meta = document.querySelector('meta[name="theme-color"]');
    const accent = getComputedStyle(document.documentElement).getPropertyValue("--pink").trim();
    if (meta && accent) meta.content = accent;
  }

  function setTheme(name) {
    if (!THEMES[name]) name = "grey";
    document.documentElement.dataset.theme = name;
    const link = $("#themeStylesheet");
    if (link) link.href = THEMES[name];
    $$(".theme-dot").forEach((button) => button.classList.toggle("is-active", button.dataset.theme === name));
    try { localStorage.setItem(THEME_KEY, name); }
    catch { /* Keep the theme for this session if browser storage is unavailable. */ }
    syncThemeColor();
  }

  function closeSettingsMenu() {
    const menu = $("#settingsMenu");
    const button = $("#settingsButton");
    if (menu && !menu.hidden) menu.hidden = true;
    if (button) button.setAttribute("aria-expanded", "false");
  }

  function shareTheme() {
    return {
      ink: cssVar("--ink") || "#1c1c1c",
      paper: cssVar("--paper") || "#e3e3e3",
      paperDeep: cssVar("--paper-deep") || "#d7d7d7",
      accent: cssVar("--pink") || "#939393",
      muted: cssVar("--muted") || "#818181",
      white: cssVar("--white") || "#fefefe"
    };
  }

  function loadArtwork(url, ms = 9000) {
    return new Promise((resolve) => {
      if (!secureArtworkUrl(url)) return resolve(null);
      const img = new Image();
      img.crossOrigin = "anonymous";
      const timer = window.setTimeout(() => { img.src = ""; resolve(null); }, ms);
      img.onload = () => { window.clearTimeout(timer); resolve(img); };
      img.onerror = () => { window.clearTimeout(timer); resolve(null); };
      img.src = url;
    });
  }

  function drawCircleImage(ctx, img, letter, x, y, radius, fallbackFill) {
    ctx.save();
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, Math.PI * 2);
    ctx.clip();
    if (img) {
      const side = Math.min(img.naturalWidth, img.naturalHeight);
      ctx.drawImage(img, (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side,
        x - radius, y - radius, radius * 2, radius * 2);
    } else {
      ctx.fillStyle = fallbackFill;
      ctx.fillRect(x - radius, y - radius, radius * 2, radius * 2);
      ctx.fillStyle = "rgb(255 255 255 / 92%)";
      ctx.font = "400 " + Math.round(radius) + "px Georgia, serif";
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText(String(letter || "?").slice(0, 1).toUpperCase(), x, y + radius * 0.06);
    }
    ctx.restore();
  }

  function drawRoundImage(ctx, img, letter, x, y, size, radius, fallbackFill) {
    ctx.save();
    ctx.beginPath();
    if (ctx.roundRect) ctx.roundRect(x, y, size, size, radius);
    else ctx.rect(x, y, size, size);
    ctx.clip();
    if (img) {
      const side = Math.min(img.naturalWidth, img.naturalHeight);
      ctx.drawImage(img, (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side, x, y, size, size);
    } else {
      ctx.fillStyle = fallbackFill;
      ctx.fillRect(x, y, size, size);
      ctx.fillStyle = "rgb(255 255 255 / 92%)";
      ctx.font = "400 " + Math.round(size * 0.5) + "px Georgia, serif";
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText(String(letter || "?").slice(0, 1).toUpperCase(), x + size / 2, y + size / 2 + 2);
    }
    ctx.restore();
  }

  function fitText(ctx, text, maxWidth, baseSize, family) {
    let size = baseSize;
    ctx.font = "400 " + size + "px " + family;
    while (size > 18 && ctx.measureText(text).width > maxWidth) {
      size -= 4;
      ctx.font = "400 " + size + "px " + family;
    }
    return size;
  }

  async function drawShareCard(format) {
    const story = format !== "square";
    const W = 1080;
    const H = story ? 1920 : 1080;
    const theme = shareTheme();
    const data = insightData();
    const artists = (data.artists || []).slice(0, 5);
    const tracks = (data.tracks || []).slice(0, 5);
    const username = state.mode === "lastfm"
      ? state.lastfmUsername
      : state.profile?.display_name || "my";
    try { await document.fonts.ready; } catch { /* Fall back to system fonts on the card. */ }
    const display = '"Shelf Display Study", "DejaVu Sans Condensed", Impact, sans-serif';
    const body = '"DejaVu Sans", Arial, sans-serif';

    const canvas = document.createElement("canvas");
    canvas.width = W;
    canvas.height = H;
    const ctx = canvas.getContext("2d");
    const bg = ctx.createLinearGradient(0, 0, 0, H);
    bg.addColorStop(0, theme.paper);
    bg.addColorStop(1, theme.paperDeep);
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, W, H);

    const pad = 90;
    ctx.fillStyle = theme.ink;
    ctx.beginPath();
    ctx.arc(pad + 34, pad + 10, 34, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = theme.accent;
    ctx.beginPath();
    ctx.arc(pad + 34, pad + 10, 13, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = theme.ink;
    ctx.font = "700 44px " + body;
    ctx.textAlign = "left";
    ctx.textBaseline = "alphabetic";
    ctx.fillText("ROOM", pad + 88, pad + 26);
    ctx.fillStyle = theme.muted;
    ctx.font = "600 30px " + body;
    ctx.textAlign = "right";
    ctx.fillText(RANGE_LABEL[state.range].toUpperCase(), W - pad, pad + 22);

    ctx.fillStyle = theme.ink;
    ctx.textAlign = "left";
    fitText(ctx, username + "'s sound", W - pad * 2, story ? 120 : 104, display);
    ctx.fillText(username + "'s sound", pad, pad + (story ? 210 : 190));

    const artPhotos = await Promise.all(artists.map((artist) => loadArtwork(artist.image)));
    const top = pad + (story ? 300 : 270);
    ctx.fillStyle = theme.muted;
    ctx.font = "600 32px " + body;
    ctx.fillText("TOP ARTISTS", pad, top);
    const cols = 5;
    const gap = story ? 30 : 24;
    const radius = Math.min(120, ((W - pad * 2 - gap * (cols - 1)) / cols) / 2);
    artists.forEach((artist, index) => {
      const cx = pad + radius + index * (radius * 2 + gap);
      const cy = top + 60 + radius;
      drawCircleImage(ctx, artPhotos[index], artist.name, cx, cy, radius, theme.ink);
      ctx.fillStyle = theme.ink;
      ctx.font = "600 30px " + body;
      ctx.textAlign = "center";
      let label = artist.name || "Unknown";
      fitText(ctx, label, radius * 2 + 20, 30, body);
      if (ctx.measureText(label).width > radius * 2 + 20 && label.length > 14) label = label.slice(0, 13) + "…";
      ctx.fillText(label, cx, cy + radius + 44);
    });

    const trackPhotos = await Promise.all(tracks.map((track) => loadArtwork(track.image)));
    let listTop = top + 60 + radius * 2 + (story ? 150 : 130);
    ctx.textAlign = "left";
    ctx.fillStyle = theme.muted;
    ctx.font = "600 32px " + body;
    ctx.fillText("TOP TRACKS", pad, listTop);
    listTop += story ? 50 : 44;
    const rowH = story ? 172 : 118;
    const cover = story ? 132 : 92;
    tracks.forEach((track, index) => {
      const y = listTop + index * rowH;
      if (y + rowH > H - (story ? 150 : 110)) return;
      ctx.fillStyle = theme.muted;
      ctx.font = "700 34px " + body;
      ctx.textAlign = "left";
      ctx.fillText(String(index + 1).padStart(2, "0"), pad, y + rowH / 2 + 12);
      drawRoundImage(ctx, trackPhotos[index], track.name || track.trackName, pad + 80, y + (rowH - cover) / 2, cover, 22, theme.ink);
      const title = track.name || track.trackName || "Unknown track";
      const artist = track.artist || track.artistName || "";
      ctx.fillStyle = theme.ink;
      fitText(ctx, title, W - pad * 2 - 320, story ? 40 : 36, body);
      ctx.fillText(title, pad + 80 + cover + 28, y + rowH / 2 - 8);
      ctx.fillStyle = theme.muted;
      ctx.font = (story ? 32 : 30) + "px " + body;
      ctx.fillText(String(artist).slice(0, 34), pad + 80 + cover + 28, y + rowH / 2 + (story ? 40 : 36));
    });

    ctx.fillStyle = theme.muted;
    ctx.font = "600 28px " + body;
    ctx.textAlign = "center";
    ctx.fillText("listening-room-stats.netlify.app", W / 2, H - 64);
    return canvas;
  }

  async function refreshSharePreview() {
    const preview = $("#sharePreview");
    const loading = $("#shareLoading");
    preview.hidden = true;
    if (loading) loading.hidden = false;
    $$("[data-format]", $("#shareDialog")).forEach((button) => {
      const active = button.dataset.format === state.shareFormat;
      button.classList.toggle("pill-dark", active);
      button.classList.toggle("pill-outline", !active);
    });
    try {
      state.shareCanvas = await drawShareCard(state.shareFormat);
      preview.src = state.shareCanvas.toDataURL("image/png");
      preview.hidden = false;
    } catch {
      showToast("That card could not be painted. Try again.");
    } finally {
      if (loading) loading.hidden = true;
    }
  }

  function openShareDialog() {
    const dialog = $("#shareDialog");
    if (!dialog) return;
    if (!dialog.open) dialog.showModal();
    refreshSharePreview();
  }

  function shareCanvasBlob() {
    return new Promise((resolve) => {
      if (!state.shareCanvas) return resolve(null);
      state.shareCanvas.toBlob((blob) => resolve(blob), "image/png");
    });
  }

  function animateStatCounts() {
    const reduceMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    $$(".stat-value[data-count]").forEach((element) => {
      if (element.dataset.counted) return;
      element.dataset.counted = "1";
      const target = Number(element.dataset.count) || 0;
      const suffix = element.dataset.suffix || "";
      if (reduceMotion || !target) {
        element.textContent = formatNumber(target) + suffix;
        return;
      }
      const started = performance.now();
      const duration = 900;
      const tick = (now) => {
        const progress = Math.min(1, (now - started) / duration);
        const eased = 1 - Math.pow(1 - progress, 3);
        element.textContent = formatNumber(Math.round(target * eased)) + suffix;
        if (progress < 1 && element.isConnected) requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    });
  }

  function moveRangeIndicator() {
    const group = $(".range-switch");
    if (!group) return;
    const active = $("[data-range].is-selected", group);
    const pill = $(".range-indicator", group);
    if (!active || !pill) return;
    pill.style.width = active.offsetWidth + "px";
    pill.style.transform = "translateX(" + active.offsetLeft + "px)";
  }

  function moveNavIndicator() {
    const nav = $(".main-nav");
    if (!nav) return;
    const active = $(".nav-link.is-active", nav);
    const pill = $(".nav-indicator", nav);
    if (!active || !pill) return;
    pill.style.width = active.offsetWidth + "px";
    pill.style.transform = "translateX(" + active.offsetLeft + "px)";
  }

  function updateGalaxyCaption(wrap, bubble) {
    const caption = wrap.closest(".panel")?.querySelector(".galaxy-caption");
    if (!caption) return;
    if (!bubble) {
      caption.textContent = "Tap a flavor to spotlight it.";
      return;
    }
    caption.textContent = (bubble.dataset.name || "Flavor") + " · " + formatNumber(Number(bubble.dataset.value) || 0) + " plays behind it";
  }

  function bindDynamicActions() {
    $$("[data-view]", $("#screenContent")).forEach((button) => {
      button.addEventListener("click", () => {
        state.view = button.dataset.view;
        render();
        window.scrollTo({ top: 0, behavior: "smooth" });
      });
    });
  }

  function readHistoryDate(value) {
    if (typeof value === "number") {
      return value < 1000000000000 ? value * 1000 : value;
    }
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : NaN;
  }

  function normalizeHistoryRow(row) {
    const trackName = row.master_metadata_track_name || row.trackName || row.track_name || row.track || "";
    const artistName = row.master_metadata_album_artist_name || row.artistName || row.artist_name || row.artist || "";
    const timestamp = readHistoryDate(row.ts || row.endTime || row.played_at || row.timestamp);
    if (!trackName || !artistName || !Number.isFinite(timestamp)) return null;
    const msPlayed = Number(row.ms_played ?? row.msPlayed ?? row.duration_ms ?? row.ms_played_total ?? 0);
    const album = row.master_metadata_album_album_name || row.albumName || "";
    return {
      trackName: String(trackName),
      artistName: String(artistName),
      album: String(album),
      ts: timestamp,
      msPlayed: Number.isFinite(msPlayed) ? Math.max(0, msPlayed) : 0,
      url: row.spotify_track_uri || row.spotifyTrackUri || ""
    };
  }

  function rowsFromJson(value) {
    if (Array.isArray(value)) return value;
    if (Array.isArray(value?.data)) return value.data;
    if (Array.isArray(value?.items)) return value.items;
    return [];
  }

  async function importHistory(files) {
    if (!files.length) return;
    setNotice("");
    try {
      const parsedGroups = await Promise.all(Array.from(files).map(async (file) => {
        if (!file.name.toLowerCase().endsWith(".json")) return [];
        const parsed = JSON.parse(await file.text());
        return rowsFromJson(parsed);
      }));
      const normalized = parsedGroups.flat().map(normalizeHistoryRow).filter(Boolean);
      if (!normalized.length) {
        throw new Error("I couldn't find Spotify track rows in those files. Choose the JSON files from your Streaming History export.");
      }
      const unique = new Map();
      normalized.forEach((row) => {
        const key = row.ts + "\u0000" + row.trackName + "\u0000" + row.artistName;
        unique.set(key, row);
      });
      state.history = Array.from(unique.values()).sort((a, b) => a.ts - b.ts);
      state.range = "all_time";
      state.mode = "demo";
      state.profile = null;
      render();
      showToast("Loaded " + formatNumber(state.history.length) + " listening-history entries in this browser.");
      $("#historyFiles").value = "";
    } catch (error) {
      setNotice(error.message || "That history file could not be read.", true);
    }
  }

  function clearHistory() {
    state.history = null;
    state.range = "week";
    if (state.lastfmUsername && getLastfmApiKey()) loadLastfmData();
    else render();
    showToast("Imported history cleared from this page.");
  }

  function forgetLastfmUsername() {
    localStorage.removeItem(LASTFM_USERNAME_KEY);
    localStorage.removeItem(LASTFM_CACHE_KEY);
    state.lastfmUsername = "";
    state.lastfmRows = [];
    state.lastfmTopTracks = [];
    state.lastfmTopArtists = [];
    state.lastfmTotalCount = 0;
    state.lastfmTruncated = false;
    state.mode = "demo";
    state.profile = null;
    state.tracks = SAMPLE.tracks;
    state.artists = SAMPLE.artists;
    state.recent = SAMPLE.recent;
    state.nowPlaying = null;
    if (!state.history && state.range === "all_time") state.range = "week";
    render();
    showToast("Last.fm username removed from this browser.");
  }

  function openConnectDialog() {
    $("#lastfmUsername").value = state.lastfmUsername || localStorage.getItem(LASTFM_USERNAME_KEY) || "";
    $("#lastfmApiKey").value = getLastfmApiKey();
    const hasKey = Boolean(getLastfmApiKey());
    $("#spotifyReady").classList.toggle("is-configured", hasKey);
    $("#spotifyReadyText").textContent = hasKey
      ? "The Last.fm reader is ready. Link Spotify on Last.fm, then enter your Last.fm username here."
      : "First connect Spotify on Last.fm. The website owner also needs to add a free Last.fm API key.";
    $("#ownerSetup").hidden = hasKey;
    $("#connectDialog").showModal();
  }

  function wireEvents() {
    $("#connectButton").addEventListener("click", () => {
      closeSettingsMenu();
      if (state.mode !== "lastfm") {
        window.open("https://www.last.fm/settings/applications", "_blank", "noopener,noreferrer");
      }
      openConnectDialog();
    });
    $("#ribbonConnect").addEventListener("click", openConnectDialog);
    $("#loadLastfmButton").addEventListener("click", connectLastfm);
    $("#refreshButton").addEventListener("click", () => {
      if (state.mode === "lastfm") loadLastfmData();
      else showToast("Log in with Spotify to refresh your personal data.");
    });
    $("#importButton").addEventListener("click", () => $("#historyFiles").click());
    $("#historyFiles").addEventListener("change", (event) => importHistory(event.target.files));
    $("#clearHistoryButton").addEventListener("click", clearHistory);
    $("#disconnectButton").addEventListener("click", () => {
      closeSettingsMenu();
      forgetLastfmUsername();
    });
    $("#lastfmUsername").addEventListener("keydown", (event) => {
      if (event.key === "Enter") connectLastfm();
    });
    $("#connectDialog").addEventListener("click", (event) => {
      if (event.target === $("#connectDialog")) $("#connectDialog").close();
    });
    $$(".nav-link").forEach((button) => button.addEventListener("click", () => {
      state.view = button.dataset.view;
      render();
      window.scrollTo({ top: 0, behavior: "smooth" });
    }));
    $$(".theme-dot").forEach((button) => button.addEventListener("click", () => setTheme(button.dataset.theme)));
    const shareButton = $("#shareButton");
    if (shareButton) shareButton.addEventListener("click", openShareDialog);
    $$("[data-format]", $("#shareDialog")).forEach((button) => button.addEventListener("click", () => {
      if (state.shareFormat === button.dataset.format) return;
      state.shareFormat = button.dataset.format;
      refreshSharePreview();
    }));
    const shareDownload = $("#shareDownload");
    if (shareDownload) shareDownload.addEventListener("click", async () => {
      const blob = await shareCanvasBlob();
      if (!blob) {
        showToast("Nothing to download yet. Wait for the preview.");
        return;
      }
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = "room-" + state.shareFormat + "-" + state.range + ".png";
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(link.href), 4000);
    });
    const shareNative = $("#shareNative");
    if (shareNative) shareNative.addEventListener("click", async () => {
      const blob = await shareCanvasBlob();
      if (!blob) {
        showToast("Nothing to share yet. Wait for the preview.");
        return;
      }
      const file = new File([blob], "room-" + state.shareFormat + ".png", { type: "image/png" });
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        try {
          await navigator.share({ files: [file], title: "My Room card" });
        } catch {
          /* The user dismissed the share sheet. */
        }
      } else {
        showToast("This browser cannot share files. Use Download instead.");
      }
    });
    const shareDialog = $("#shareDialog");
    if (shareDialog) shareDialog.addEventListener("click", (event) => {
      if (event.target === shareDialog) shareDialog.close();
    });
    const screen = $("#screenContent");
    if (screen) screen.addEventListener("click", (event) => {
      const wrap = event.target.closest(".galaxy-wrap");
      if (!wrap) {
        document.querySelectorAll(".galaxy-wrap.has-focus").forEach((focused) => {
          focused.classList.remove("has-focus");
          focused.querySelectorAll(".bubble.is-focus").forEach((node) => node.classList.remove("is-focus"));
          updateGalaxyCaption(focused, null);
        });
        return;
      }
      const bubble = event.target.closest(".bubble");
      if (!bubble) return;
      const was = bubble.classList.contains("is-focus");
      wrap.querySelectorAll(".bubble.is-focus").forEach((node) => node.classList.remove("is-focus"));
      if (was) {
        wrap.classList.remove("has-focus");
        updateGalaxyCaption(wrap, null);
      } else {
        wrap.classList.add("has-focus");
        bubble.classList.add("is-focus");
        updateGalaxyCaption(wrap, bubble);
      }
    });
    const themeLink = $("#themeStylesheet");
    if (themeLink) themeLink.addEventListener("load", syncThemeColor);
    let navResizeTimer = 0;
    window.addEventListener("resize", () => {
      window.clearTimeout(navResizeTimer);
      navResizeTimer = window.setTimeout(() => { moveNavIndicator(); moveRangeIndicator(); }, 120);
    });
    if (document.fonts?.ready) document.fonts.ready.then(() => { moveNavIndicator(); moveRangeIndicator(); }).catch(() => {});
    const settingsButton = $("#settingsButton");
    const settingsMenu = $("#settingsMenu");
    if (settingsButton && settingsMenu) {
      settingsButton.addEventListener("click", (event) => {
        event.stopPropagation();
        const willOpen = settingsMenu.hidden;
        settingsMenu.hidden = !willOpen;
        settingsButton.setAttribute("aria-expanded", String(willOpen));
      });
      document.addEventListener("click", (event) => {
        if (!settingsMenu.hidden && !event.target.closest(".settings-wrap")) closeSettingsMenu();
      });
      document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !settingsMenu.hidden) {
          closeSettingsMenu();
          settingsButton.focus();
        }
      });
    }
    $$("[data-range]").forEach((button) => button.addEventListener("click", () => {
      if (button.dataset.range === state.range) return;
      state.range = button.dataset.range;
      $$("[data-range]").forEach((rangeButton) => rangeButton.classList.toggle("is-selected", rangeButton === button));
      moveRangeIndicator();
      if (state.mode === "lastfm") loadLastfmData();
      else render();
    }));
    const importZone = $(".import-strip");
    ["dragenter", "dragover"].forEach((eventName) => importZone.addEventListener(eventName, (event) => {
      event.preventDefault();
      importZone.classList.add("is-dragging");
    }));
    ["dragleave", "drop"].forEach((eventName) => importZone.addEventListener(eventName, (event) => {
      event.preventDefault();
      importZone.classList.remove("is-dragging");
    }));
    importZone.addEventListener("drop", (event) => importHistory(event.dataTransfer.files));
  }

  async function init() {
    state.lastfmUsername = localStorage.getItem(LASTFM_USERNAME_KEY) || "";
    if (state.lastfmUsername) {
      state.mode = "lastfm";
      state.profile = { display_name: state.lastfmUsername };
      try {
        const cached = JSON.parse(localStorage.getItem(LASTFM_CACHE_KEY) || "null");
        if (cached?.username === state.lastfmUsername && Object.hasOwn(RANGE_MS, cached.range)) {
          state.range = cached.range;
          state.lastfmRows = Array.isArray(cached.rows) ? cached.rows : [];
          state.lastfmTotalCount = Number(cached.totalCount) || state.lastfmRows.length;
          state.lastfmTopTracks = Array.isArray(cached.tracks) ? cached.tracks : [];
          state.lastfmTopArtists = Array.isArray(cached.artists) ? cached.artists : [];
          state.lastfmTruncated = Boolean(cached.truncated);
        }
      } catch { /* The user can refresh from Last.fm if local cache data is invalid. */ }
    }
    wireEvents();
    try {
      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        document.querySelectorAll(".wordmark-logo animateMotion").forEach((node) => node.remove());
      }
    } catch { /* Keep the logo animation when motion settings cannot be read. */ }
    let savedTheme = "grey";
    try { savedTheme = localStorage.getItem(THEME_KEY) || "grey"; }
    catch { /* Fall back to the default grey theme. */ }
    setTheme(savedTheme);
    render();
  }

  document.addEventListener("DOMContentLoaded", init, { once: true });
})();
