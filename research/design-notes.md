# Shelf.im design notes

Research captured on 2026-09-26 from the public Shelf site. The static response and its styles were parsed with Scrapling's adaptive `Selector`; the Framer-rendered page variants were loaded in headless Chromium and then parsed with Scrapling because their visible copy is hydrated in the browser.

## Visual system observed

- **Home hero:** one full-viewport pink canvas with subtle film grain. A compact white `SHELF` wordmark sits above a centered, two-line uppercase headline: “ORGANIZE YOUR TASTE / ALL IN ONE PLACE.” The current desktop capture has a single thin white outlined pill, “Get the app,” a 240-pixel QR block pinned to the lower-left corner, and a compact footer on the lower right.
- **Typography:** the live page declares `OT Neue Montreal Medium Extra Squeezed` at weight 500 for the headline. Its wordmark and small controls use heavier and more neutral sans-serif styles. The local page loads the public display-font URL when online and has a condensed local fallback.
- **Color:** the visible hero center samples near `#eb76ba`; the corners are slightly lighter. The wider stylesheet also contains violet `#6149f3`, lime `#dbf349`, blue `#09f`, and warm white `#fefffa` tokens, although those are not the dominant colors in the current home hero.
- **Controls:** the home action is a 204 × 64 px transparent pill with a 2 px white border and centered white type. The privacy/data-rights links and four social icons share the same small white footer style.
- **Motion:** the rendered markup is Framer-generated. The static HTML includes the Framer runtime; this capture did not expose named CSS keyframes for the hero. The local study adds short text entrances, drifting grain, button hover/press states, and an app-details dialog to make those controls usable.

## Other public page types

- The [media kit](https://www.shelf.im/media-kit) describes Shelf as one digital shelf for what someone reads, watches, and listens to. Its page uses the same brand typography and link treatment for editorial copy.
- The [“What’s on my Shelf?” index](https://www.shelf.im/woms) presents creator shelves as image-led profile cards.
- The [data-rights page](https://www.shelf.im/data-rights) uses a plain explanatory layout and links to privacy and terms pages.
- A representative public profile uses an avatar, profile name, platform connection prompt, “Discover your taste” statement, and app-download action.

The render report covers these five page types; it does not download every creator's profile artwork or crawl the complete user directory. Individual profiles share a repeated profile-page template.

## Sources

- [Shelf homepage](https://www.shelf.im/)
- [Shelf media kit](https://www.shelf.im/media-kit)
- [Shelf creator-shelf index](https://www.shelf.im/woms)
- [Scrapling repository and documentation](https://github.com/D4Vinci/Scrapling)
- [Scrapling fetcher and selector documentation](https://scrapling.readthedocs.io/en/latest/fetching/choosing.html)
