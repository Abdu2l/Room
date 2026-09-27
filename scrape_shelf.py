"""Crawl Shelf.im's public design pages and summarize the visible design system.

Uses Scrapling's adaptive Selector for HTML extraction. curl_cffi supplies the
HTTP transport; no login, private data, or anti-bot bypass is used.
"""

from __future__ import annotations

import json
import re
import time
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from urllib.parse import urldefrag, urljoin, urlparse
from urllib.robotparser import RobotFileParser

from curl_cffi import requests
from scrapling.parser import Selector


ROOT = "https://www.shelf.im/"
HOST = urlparse(ROOT).netloc
OUT = Path(__file__).parent / "research"
OUT.mkdir(exist_ok=True)
USER_AGENT = "ShelfDesignStudy/1.0 (personal visual research; contact: local prototype)"
MAX_PAGES = 100
MAX_STYLESHEETS = 120
PAUSE_SECONDS = 0.35  # Keep the small public-site crawl gentle.


def clean_url(raw: str, base: str) -> str | None:
    """Resolve a link, discard fragments/trackers, and stay on Shelf.im."""
    absolute = urldefrag(urljoin(base, raw.strip()))[0]
    parsed = urlparse(absolute)
    if parsed.scheme not in {"http", "https"} or parsed.netloc not in {HOST, "shelf.im"}:
        return None
    if parsed.query:
        # Query-string variants are not new design pages.
        absolute = absolute.split("?", 1)[0]
    return absolute.rstrip("/") + ("/" if parsed.path in {"", "/"} else "")


def fetch(url: str):
    """Fetch one public URL and return its response plus Scrapling's parser."""
    time.sleep(PAUSE_SECONDS)
    response = requests.get(
        url,
        headers={"User-Agent": USER_AGENT},
        impersonate="chrome",
        timeout=25,
        allow_redirects=True,
    )
    if response.status_code >= 400:
        return response, None
    # Adaptive parsing keeps the extraction code resilient to small DOM changes.
    return response, Selector(response.text, adaptive=True)


def text_list(page, selector: str) -> list[str]:
    """Get non-empty text nodes while keeping the output compact."""
    values = page.css(selector).getall()
    return [re.sub(r"\s+", " ", value).strip() for value in values if value.strip()]


def robots_and_sitemaps() -> tuple[RobotFileParser, list[str]]:
    """Load robots.txt first and collect every sitemap it advertises."""
    response = requests.get(
        urljoin(ROOT, "robots.txt"),
        headers={"User-Agent": USER_AGENT},
        impersonate="chrome",
        timeout=20,
    )
    robots_text = response.text if response.status_code < 400 else ""
    parser = RobotFileParser()
    parser.set_url(urljoin(ROOT, "robots.txt"))
    parser.parse(robots_text.splitlines())
    sitemap_urls = re.findall(r"(?im)^\s*sitemap:\s*(\S+)", robots_text)
    # Some static-site generators omit the Sitemap line, so check the standard path.
    if not sitemap_urls:
        sitemap_urls = [urljoin(ROOT, "sitemap.xml")]
    return parser, sitemap_urls


def sitemap_pages(sitemap_urls: list[str]) -> list[str]:
    """Read URL entries from sitemap files, including nested sitemap indexes."""
    discovered: list[str] = []
    pending = list(sitemap_urls)
    seen_maps: set[str] = set()
    while pending and len(seen_maps) < 20:
        sitemap_url = pending.pop(0)
        if sitemap_url in seen_maps:
            continue
        seen_maps.add(sitemap_url)
        response = requests.get(
            sitemap_url,
            headers={"User-Agent": USER_AGENT},
            impersonate="chrome",
            timeout=20,
        )
        if response.status_code >= 400:
            continue
        try:
            root = ET.fromstring(response.content)
        except ET.ParseError:
            continue
        for element in root.iter():
            if element.tag.rsplit("}", 1)[-1] != "loc" or not element.text:
                continue
            location = element.text.strip()
            if location.endswith(".xml"):
                pending.append(location)
            else:
                normalized = clean_url(location, ROOT)
                if normalized:
                    discovered.append(normalized)
    return discovered


def scrape_pages(robots: RobotFileParser, seeds: list[str]) -> tuple[list[dict], list[str]]:
    """Crawl reachable public pages; keep profiles to a few design samples."""
    queue = list(dict.fromkeys([ROOT, *seeds]))
    visited: set[str] = set()
    pages: list[dict] = []
    stylesheet_urls: list[str] = []
    profile_count = 0

    while queue and len(pages) < MAX_PAGES:
        url = queue.pop(0)
        if url in visited:
            continue
        visited.add(url)
        if not robots.can_fetch(USER_AGENT, url):
            continue

        try:
            response, page = fetch(url)
        except Exception as error:  # Record an inaccessible path and continue the crawl.
            pages.append({"url": url, "error": f"{type(error).__name__}: {error}"})
            continue
        if page is None:
            pages.append({"url": url, "status": response.status_code})
            continue

        path = urlparse(response.url).path or "/"
        is_profile = path not in {"/", "/media-kit", "/woms", "/about", "/contact", "/privacy", "/terms"}
        if is_profile:
            profile_count += 1
            if profile_count > 6:
                continue

        links = []
        for anchor in page.css("a[href]"):
            href = anchor.attrib.get("href", "")
            absolute = clean_url(href, response.url)
            label = " ".join(anchor.css("::text").getall()).strip()
            if absolute:
                links.append({"text": re.sub(r"\s+", " ", label), "url": absolute})
                if absolute not in visited and absolute not in queue:
                    queue.append(absolute)

        styles = []
        for link in page.css('link[rel~="stylesheet"][href]'):
            href = link.attrib.get("href", "")
            if href:
                styles.append(urljoin(response.url, href))
        stylesheet_urls.extend(styles)

        buttons = []
        for element in page.css("button, [role=button], a.button, a[class*=button], a[class*=Button]"):
            label = " ".join(element.css("::text").getall()).strip()
            buttons.append({
                "text": re.sub(r"\s+", " ", label)[:140],
                "class": element.attrib.get("class", ""),
                "aria_label": element.attrib.get("aria-label", ""),
                "href": element.attrib.get("href", ""),
            })

        pages.append({
            "url": url,
            "final_url": response.url,
            "status": response.status_code,
            "title": (page.css("title::text").get() or "").strip(),
            "description": (page.css('meta[name="description"]::attr(content)').get() or "").strip(),
            "headings": {
                "h1": text_list(page, "h1::text"),
                "h2": text_list(page, "h2::text"),
                "h3": text_list(page, "h3::text"),
            },
            "buttons": buttons,
            "navigation": text_list(page, "nav a::text"),
            "images": [
                {
                    "alt": image.attrib.get("alt", ""),
                    "src": urljoin(response.url, image.attrib.get("src", "")),
                    "class": image.attrib.get("class", ""),
                }
                for image in page.css("img[src]")
            ],
            "stylesheets": styles,
            "scripts": [urljoin(response.url, script.attrib["src"]) for script in page.css("script[src]")],
            "inline_css": page.css("style::text").getall(),
            "links": links,
        })

    return pages, list(dict.fromkeys(stylesheet_urls))[:MAX_STYLESHEETS]


def scrape_stylesheets(urls: list[str]) -> list[dict]:
    """Capture public CSS so colors, type, radii, and motion can be reviewed."""
    sheets: list[dict] = []
    for url in urls:
        try:
            time.sleep(PAUSE_SECONDS)
            response = requests.get(url, headers={"User-Agent": USER_AGENT}, impersonate="chrome", timeout=25)
            if response.status_code < 400 and "text/css" in response.headers.get("content-type", "text/css"):
                css = response.text
                sheets.append({
                    "url": url,
                    "status": response.status_code,
                    "bytes": len(response.content),
                    "css": css,
                })
        except Exception as error:
            sheets.append({"url": url, "error": f"{type(error).__name__}: {error}"})
    return sheets


def summarize(pages: list[dict], sheets: list[dict]) -> dict:
    """Extract the repeated visual language from the crawled page and CSS set."""
    colors: Counter[str] = Counter()
    families: Counter[str] = Counter()
    radii: Counter[str] = Counter()
    animations: Counter[str] = Counter()
    durations: Counter[str] = Counter()
    css_text = "\n".join(sheet.get("css", "") for sheet in sheets)
    css_text += "\n" + "\n".join(css for page in pages for css in page.get("inline_css", []))
    for pattern, counter in [
        (r"#[0-9a-fA-F]{3,8}\b|rgba?\([^)]*\)|hsla?\([^)]*\)", colors),
        (r"font-family\s*:\s*([^;}]+)", families),
        (r"border-radius\s*:\s*([^;}]+)", radii),
        (r"@keyframes\s+([\w-]+)", animations),
        (r"(?:transition|animation-duration)\s*:[^;}]*?(\d+(?:\.\d+)?m?s)", durations),
    ]:
        for match in re.findall(pattern, css_text, flags=re.I):
            counter[re.sub(r"\s+", " ", match).strip()] += 1
    return {
        "page_count": len(pages),
        "stylesheet_count": len(sheets),
        "page_titles": [p.get("title", "") for p in pages if p.get("title")],
        "headings": [heading for p in pages for group in p.get("headings", {}).values() for heading in group],
        "button_labels": [button["text"] for p in pages for button in p.get("buttons", []) if button["text"]],
        "colors": colors.most_common(50),
        "font_families": families.most_common(30),
        "border_radii": radii.most_common(24),
        "keyframes": animations.most_common(30),
        "motion_durations": durations.most_common(24),
        "page_routes": [p.get("url") for p in pages],
    }


def main() -> None:
    robots, sitemap_urls = robots_and_sitemaps()
    seeds = sitemap_pages(sitemap_urls)
    pages, stylesheet_urls = scrape_pages(robots, seeds)
    sheets = scrape_stylesheets(stylesheet_urls)

    report = {
        "source": ROOT,
        "crawler": "Scrapling adaptive Selector + curl_cffi HTTP transport",
        "robots_txt_obeyed": True,
        "sitemaps": sitemap_urls,
        "pages": pages,
        "stylesheets": [{key: value for key, value in sheet.items() if key != "css"} for sheet in sheets],
        "design_summary": summarize(pages, sheets),
        "css_sources": sheets,
    }
    (OUT / "shelf-crawl.json").write_text(json.dumps(report, indent=2, ensure_ascii=False))
    print(json.dumps(report["design_summary"], indent=2, ensure_ascii=False))
    print(f"\nSaved full crawl to {OUT / 'shelf-crawl.json'}")


if __name__ == "__main__":
    main()
