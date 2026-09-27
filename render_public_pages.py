"""Render Shelf's public page types, then extract their design cues with Scrapling.

Shelf is a Framer site whose visible copy is hydrated in the browser. Chromium
renders that public markup; Scrapling's adaptive Selector then extracts the
same headings, controls, links, imagery, and CSS cues into a compact report.
The crawl reads robots.txt first and only visits the small route set below.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import time
from collections import Counter
from pathlib import Path
from urllib.parse import urljoin, urlparse
from urllib.robotparser import RobotFileParser

from curl_cffi import requests
from scrapling.parser import Selector


SITE = "https://www.shelf.im/"
USER_AGENT = "ShelfDesignStudy/1.0 (personal visual research)"
CHROMIUM = os.environ.get(
    "SHELF_CHROMIUM_PATH",
    "/home/abdul/.cache/ms-playwright/chromium-1234/chrome-linux64/chrome",
)
OUT = Path(__file__).parent / "research"

# Homepage, the two public editorial pages, data rights, and one public profile
# are enough to see each page layout without collecting an account directory.
PUBLIC_ROUTES = ["/", "/media-kit", "/woms", "/data-rights", "/minpriv"]


def allowed_by_robots(url: str) -> bool:
    """Honor the site's published robots rules before opening a route."""
    response = requests.get(
        urljoin(SITE, "robots.txt"),
        headers={"User-Agent": USER_AGENT},
        impersonate="chrome",
        timeout=20,
    )
    parser = RobotFileParser()
    parser.set_url(urljoin(SITE, "robots.txt"))
    parser.parse(response.text.splitlines() if response.status_code < 400 else [])
    return parser.can_fetch(USER_AGENT, url)


def render(url: str) -> str:
    """Let the page's own JavaScript hydrate, and return its rendered DOM."""
    result = subprocess.run(
        [
            CHROMIUM,
            "--headless",
            "--no-sandbox",
            "--disable-gpu",
            "--disable-dev-shm-usage",
            "--hide-scrollbars",
            "--virtual-time-budget=7000",
            "--dump-dom",
            url,
        ],
        capture_output=True,
        text=True,
        timeout=55,
        check=False,
    )
    if result.returncode not in (0, 133):
        raise RuntimeError(f"Chromium exited {result.returncode}: {result.stderr[-500:]}")
    return result.stdout


def page_design(url: str, html: str) -> dict:
    """Use Scrapling's adaptive CSS selectors to describe a rendered page."""
    page = Selector(html, adaptive=True)
    script_text = page.css("script::text").getall()
    style_text = page.css("style::text").getall()
    ignored_text = set(script_text + style_text)
    snippets = []
    for raw in page.css("body *::text").getall():
        value = re.sub(r"\s+", " ", raw).strip()
        if not value or value in ignored_text or len(value) > 150:
            continue
        if value.startswith(("(()=>", "!function", "html body {")) or "function(" in value:
            continue
        if value not in snippets:
            snippets.append(value)

    controls = []
    for element in page.css("button, [role=button], a[href]"):
        label = re.sub(r"\s+", " ", " ".join(element.css("::text").getall())).strip()
        if label:
            controls.append({
                "label": label[:100],
                "kind": element.tag,
                "class": element.attrib.get("class", "")[:120],
                "href": element.attrib.get("href", ""),
            })

    style_source = "\n".join(style_text)
    colors = Counter(re.findall(r"#[0-9a-fA-F]{3,8}\b|rgba?\([^)]*\)", style_source))
    fonts = Counter(re.findall(r"font-family\s*:\s*([^;}]+)", style_source, flags=re.I))
    keyframes = re.findall(r"@keyframes\s+([\w-]+)", style_source, flags=re.I)
    transitions = re.findall(r"(?:transition|animation-duration)\s*:\s*([^;}]+)", style_source, flags=re.I)

    return {
        "url": url,
        "status": "rendered",
        "title": (page.css("title::text").get() or "").strip(),
        "copy": snippets[:100],
        "controls": controls[:50],
        "images": [
            {
                "alt": image.attrib.get("alt", ""),
                "src": urljoin(url, image.attrib.get("src", "")),
                "class": image.attrib.get("class", "")[:120],
            }
            for image in page.css("img[src]")
        ],
        "internal_routes": list(dict.fromkeys(
            urlparse(urljoin(url, anchor.attrib.get("href", ""))).path
            for anchor in page.css("a[href]")
            if urlparse(urljoin(url, anchor.attrib.get("href", ""))).netloc.endswith("shelf.im")
        )),
        "inline_css_bytes": sum(len(css) for css in style_text),
        "css_colors": colors.most_common(24),
        "font_families": fonts.most_common(18),
        "keyframes": list(dict.fromkeys(keyframes)),
        "motion_rules": list(dict.fromkeys(transitions))[:30],
    }


def main() -> None:
    OUT.mkdir(exist_ok=True)
    results = []
    for route in PUBLIC_ROUTES:
        url = urljoin(SITE, route.lstrip("/")) if route != "/" else SITE
        if not allowed_by_robots(url):
            results.append({"url": url, "status": "disallowed by robots.txt"})
            continue
        try:
            html = render(url)
            results.append(page_design(url, html))
        except Exception as error:
            results.append({"url": url, "status": "error", "error": f"{type(error).__name__}: {error}"})
        time.sleep(.4)  # A short pause between public pages.

    report = {
        "source": SITE,
        "method": "Chromium-rendered public markup parsed with Scrapling adaptive Selector",
        "robots_txt_obeyed": True,
        "pages": results,
    }
    destination = OUT / "rendered-page-designs.json"
    destination.write_text(json.dumps(report, indent=2, ensure_ascii=False))
    for page in results:
        print(f"{page.get('status')}: {page.get('url')} — {page.get('title', '')}")
        if page.get("copy"):
            print("  visible copy:", " / ".join(page["copy"][:8]))
    print(f"\nSaved design extraction to {destination}")


if __name__ == "__main__":
    main()
