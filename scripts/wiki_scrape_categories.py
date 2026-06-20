#!/usr/bin/env python3
"""Enumerate OSRS Wiki categories and bulk-extract NPC/POI coordinates.

For each category, walks all member pages (with pagination), fetches each page's
wikitext, and extracts the first {{Map|x=X|y=Y}} template as coordinates.

Usage:
  python3 scripts/wiki_scrape_categories.py
"""

import json
import re
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from threading import Lock

UA = "osrs-llm-helper/0.1 (research scrape)"
API = "https://oldschool.runescape.wiki/api.php"
OUT_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "poi-data"

# (category_name, target_poi_type, notes_label_or_None)
CATEGORIES = [
    ("Bosses",                "boss_arena",  "Boss arena"),
    ("Members' shops",        "shop",        None),
    ("Free-to-play shops",    "shop",        None),
    ("Slayer masters",        "slayer_master", "Slayer master"),
    ("Quests",                "quest_start", "Quest"),
    ("Stalls",                "stall",       "Thieving stall"),
    ("Non-player characters", "npc",         None),
    ("Monsters",              "monster",     None),
]

PARALLEL_WORKERS = 2

MAP_RE = re.compile(
    r"\{\{Map\b[^}]*?x\s*=\s*(\d+)[^}]*?y\s*=\s*(\d+)(?:[^}]*?z\s*=\s*(\d+))?",
    re.IGNORECASE | re.DOTALL,
)


def api_get(params: dict) -> dict:
    params = {**params, "format": "json"}
    qs = urllib.parse.urlencode(params)
    req = urllib.request.Request(f"{API}?{qs}", headers={"User-Agent": UA})
    backoff = 1.0
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            if e.code in (429, 503) and attempt < 4:
                time.sleep(backoff)
                backoff = min(backoff * 2, 30)
                continue
            raise
        except Exception:
            if attempt < 4:
                time.sleep(backoff)
                backoff = min(backoff * 2, 30)
                continue
            raise
    raise RuntimeError("retry exhausted")


def enumerate_category(cat: str) -> list[str]:
    titles = []
    cont = None
    while True:
        params = {
            "action": "query",
            "list": "categorymembers",
            "cmtitle": f"Category:{cat}",
            "cmlimit": "500",
            "cmtype": "page",
        }
        if cont:
            params["cmcontinue"] = cont
        data = api_get(params)
        for m in data.get("query", {}).get("categorymembers", []):
            titles.append(m["title"])
        cont = data.get("continue", {}).get("cmcontinue")
        if not cont:
            break
        time.sleep(0.1)
    return titles


def fetch_wikitext(page: str) -> str | None:
    try:
        data = api_get({"action": "parse", "prop": "wikitext", "page": page, "redirects": 1})
        return data.get("parse", {}).get("wikitext", {}).get("*")
    except Exception as e:
        print(f"    ! fetch fail {page}: {e}", file=sys.stderr)
        return None


def extract_coord(wt: str) -> tuple[int, int, int] | None:
    if not wt:
        return None
    m = MAP_RE.search(wt)
    if m:
        return int(m.group(1)), int(m.group(2)), int(m.group(3) or 0)
    return None


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    pois = []
    seen_keys = set()

    lock = Lock()

    def process(title: str, ptype: str, note_label: str | None):
        wt = fetch_wikitext(title)
        coord = extract_coord(wt) if wt else None
        if coord is None:
            return None
        x, y, z = coord
        with lock:
            key = (ptype, title.lower())
            if key in seen_keys:
                return None
            seen_keys.add(key)
        entry = {"type": ptype, "name": title, "x": x, "y": y, "plane": z}
        if note_label:
            entry["notes"] = note_label
        return entry

    for cat, ptype, note_label in CATEGORIES:
        print(f"\n=== Category:{cat} → type={ptype} ===")
        members = enumerate_category(cat)
        print(f"  {len(members)} pages to fetch (parallel x{PARALLEL_WORKERS})")
        found = 0
        with ThreadPoolExecutor(max_workers=PARALLEL_WORKERS) as ex:
            futs = {ex.submit(process, t, ptype, note_label): t for t in members}
            for i, fut in enumerate(as_completed(futs), 1):
                try:
                    entry = fut.result()
                except Exception:
                    entry = None
                if entry is not None:
                    pois.append(entry)
                    found += 1
                    if found % 50 == 0:
                        print(f"    progress: {found} with coords / {i} processed", flush=True)
                        save_progress(pois)
        print(f"  done: {found} entries with coords (of {len(members)})", flush=True)
        save_progress(pois)

    out = OUT_DIR / "wiki-categories.json"
    out.write_text(json.dumps({"pois": pois}, indent=2))
    print(f"\nWrote {len(pois)} POIs to {out}")


def save_progress(pois: list):
    """Persist progress periodically so we don't lose everything on crash."""
    out = OUT_DIR / "wiki-categories.json"
    out.write_text(json.dumps({"pois": pois}, indent=2))


if __name__ == "__main__":
    main()
