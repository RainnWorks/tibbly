#!/usr/bin/env python3
"""Build a wiki-sourced item → tag index for the bank categorizer.

Approach: dynamically enumerate ALL subcategories of Category:Items, walk each,
and invert the relation to produce {item_name: [tag, ...]}. We don't curate at
scrape time — even "niche" tags ("Items with charges", "Members' items", "Items
storable in the costume room") are useful for follow-up queries. The runtime
side ([BankCategorizer]) decides which tags to surface by default vs. on-demand.

Outputs (both committed; the JSON is the runtime source of truth):

  src/main/resources/item-categories.json
  src/main/resources/item-categories.sqlite

Run:
  python3 scripts/build_item_categories.py
"""

from __future__ import annotations

import json
import re
import sqlite3
import sys
import time
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path

UA = "osrs-llm-helper/0.1 (item categorizer scrape)"
API = "https://oldschool.runescape.wiki/api.php"
OUT_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
OUT_JSON = OUT_DIR / "item-categories.json"
OUT_DB = OUT_DIR / "item-categories.sqlite"

# Pages to filter out — they show up in many categories but aren't items.
JUNK_TITLE_PREFIXES = (
    "RuneScape:", "Module:", "User:", "File:", "Template:",
    "Calculator:", "Guide:", "Update:", "Special:",
)

# Hard limits + politeness.
PAGE_LIMIT = 500
SUBCAT_DEPTH = 1           # descend one level into nested subcategories
DELAY_S = 0.10
MAX_SUBCATS_PER_CAT = 80


def http_get(url: str, attempts: int = 4) -> dict:
    for i in range(attempts):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.load(r)
        except Exception as e:
            if i == attempts - 1:
                raise
            backoff = 1.5 * (2 ** i)
            print(f"    retry in {backoff:.1f}s ({e})", file=sys.stderr)
            time.sleep(backoff)
    raise RuntimeError("unreachable")


def slug(name: str) -> str:
    """Convert a wiki category name to a stable lowercase snake_case tag."""
    s = name.lower()
    s = re.sub(r"[^a-z0-9]+", "_", s).strip("_")
    return s


def list_item_subcategories() -> list[str]:
    """Every subcategory directly under Category:Items."""
    cats: list[str] = []
    cont: str | None = None
    while True:
        params = {
            "action": "query",
            "list": "categorymembers",
            "cmtitle": "Category:Items",
            "cmtype": "subcat",
            "cmlimit": str(PAGE_LIMIT),
            "format": "json",
        }
        if cont:
            params["cmcontinue"] = cont
        data = http_get(API + "?" + urllib.parse.urlencode(params))
        for m in data.get("query", {}).get("categorymembers", []):
            cats.append(m["title"].replace("Category:", "", 1))
        cont = data.get("continue", {}).get("cmcontinue")
        if not cont:
            break
        time.sleep(DELAY_S)
    return cats


def category_members(category: str, depth: int = 0,
                     visited: set[str] | None = None) -> set[str]:
    """Item-page names in `category`, recursing into subcategories."""
    if visited is None:
        visited = set()
    if category in visited:
        return set()
    visited.add(category)
    items: set[str] = set()
    subcats_visited = 0
    cont: str | None = None
    while True:
        params = {
            "action": "query",
            "list": "categorymembers",
            "cmtitle": f"Category:{category}",
            "cmlimit": str(PAGE_LIMIT),
            "format": "json",
        }
        if cont:
            params["cmcontinue"] = cont
        data = http_get(API + "?" + urllib.parse.urlencode(params))
        for member in data.get("query", {}).get("categorymembers", []):
            ns = member["ns"]
            title = member["title"]
            if ns == 0:
                if not any(title.startswith(p) for p in JUNK_TITLE_PREFIXES):
                    items.add(title)
            elif ns == 14 and depth < SUBCAT_DEPTH and subcats_visited < MAX_SUBCATS_PER_CAT:
                sub = title.replace("Category:", "", 1)
                subcats_visited += 1
                items |= category_members(sub, depth + 1, visited)
        cont = data.get("continue", {}).get("cmcontinue")
        if not cont:
            break
        time.sleep(DELAY_S)
    return items


def main() -> int:
    subcats = list_item_subcategories()
    print(f"Enumerating {len(subcats)} subcategories of Category:Items...")

    item_to_tags: dict[str, set[str]] = defaultdict(set)
    for i, wiki_cat in enumerate(subcats):
        tag = slug(wiki_cat)
        print(f"  [{i+1:3d}/{len(subcats)}] {wiki_cat:<40} → {tag}", end=" ", flush=True)
        try:
            members = category_members(wiki_cat)
        except Exception as e:
            print(f"FAILED: {e}", file=sys.stderr)
            continue
        print(f"({len(members)} items)")
        for item in members:
            item_to_tags[item].add(tag)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    serializable = {k: sorted(v) for k, v in sorted(item_to_tags.items())}
    OUT_JSON.write_text(json.dumps(serializable, indent=2, ensure_ascii=False))
    print(f"\nWrote {len(serializable)} items → {OUT_JSON}")

    if OUT_DB.exists():
        OUT_DB.unlink()
    conn = sqlite3.connect(OUT_DB)
    cur = conn.cursor()
    cur.execute("CREATE TABLE item_tags (item TEXT, tag TEXT)")
    cur.execute("CREATE INDEX idx_item ON item_tags(item)")
    cur.execute("CREATE INDEX idx_tag  ON item_tags(tag)")
    for item, cats in item_to_tags.items():
        for c in cats:
            cur.execute("INSERT INTO item_tags VALUES (?, ?)", (item, c))
    conn.commit()

    # Also dump a tag→count summary for quick inspection.
    cur.execute("SELECT tag, COUNT(*) c FROM item_tags GROUP BY tag ORDER BY c DESC")
    rows = cur.fetchall()
    print(f"\n{len(rows)} unique tags. Top 20 by item count:")
    for tag, count in rows[:20]:
        print(f"  {count:>5}  {tag}")
    conn.close()
    print(f"\nWrote SQLite → {OUT_DB}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
