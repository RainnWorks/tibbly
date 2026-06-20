#!/usr/bin/env python3
"""Scrape OSRS quest walkthroughs into a structured local database.

For each quest in Category:Quests we fetch:
  1. The main page (`Dragon Slayer I`) — Infobox Quest metadata
  2. The `/Quick guide` subpage — section-by-section walkthrough

Output is one JSON record per quest with normalized fields (members yes/no
→ bool, qp → int, etc.) plus a `steps` list of {section, title, content}.
Content is stripped of wiki markup but preserves bullet structure.

The runtime side ([QuestDatabase]) loads this file once and serves
`get_quest_data("name")` in microseconds — replacing the 3-4 wiki API calls
the agent used to make for every quest question.

Outputs (both committed):
  src/main/resources/quest-data.json
  src/main/resources/quest-data.sqlite

Run:
  python3 scripts/build_quest_data.py
"""

from __future__ import annotations

import json
import re
import sqlite3
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

UA = "osrs-llm-helper/0.1 (quest data scrape)"
API = "https://oldschool.runescape.wiki/api.php"
OUT_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
OUT_JSON = OUT_DIR / "quest-data.json"
OUT_DB = OUT_DIR / "quest-data.sqlite"

DELAY_S = 0.12
PAGE_LIMIT = 500
JUNK_PREFIXES = (
    "RuneScape:", "Module:", "Template:", "Calculator:", "Guide:", "File:", "User:",
)


def http_get(url: str, attempts: int = 4) -> dict:
    for i in range(attempts):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.load(r)
        except Exception as e:
            if i == attempts - 1:
                raise
            time.sleep(1.5 * (2 ** i))
    raise RuntimeError("unreachable")


def list_quests() -> list[str]:
    """All top-level quest pages in Category:Quests."""
    names: list[str] = []
    cont: str | None = None
    while True:
        params = {
            "action": "query",
            "list": "categorymembers",
            "cmtitle": "Category:Quests",
            "cmtype": "page",
            "cmlimit": str(PAGE_LIMIT),
            "format": "json",
        }
        if cont:
            params["cmcontinue"] = cont
        data = http_get(API + "?" + urllib.parse.urlencode(params))
        for m in data.get("query", {}).get("categorymembers", []):
            title = m["title"]
            if m["ns"] != 0:
                continue
            if any(title.startswith(p) for p in JUNK_PREFIXES):
                continue
            if "/" in title:  # skip subpages like X/Quick_guide
                continue
            names.append(title)
        cont = data.get("continue", {}).get("cmcontinue")
        if not cont:
            break
        time.sleep(DELAY_S)
    return sorted(names)


def get_wikitext(page: str) -> str | None:
    """Wikitext for a page, or None if missing."""
    params = {
        "action": "parse",
        "page": page,
        "prop": "wikitext",
        "format": "json",
        "redirects": "1",
    }
    try:
        data = http_get(API + "?" + urllib.parse.urlencode(params))
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise
    parse = data.get("parse")
    if not parse:
        return None
    return parse.get("wikitext", {}).get("*")


INFOBOX_RE = re.compile(r"\{\{\s*Infobox Quest\s*(\|.*?)\n\}\}", re.DOTALL | re.IGNORECASE)
QUEST_DETAILS_RE = re.compile(r"\{\{\s*Quest details\s*(\|.*?)\n\}\}", re.DOTALL | re.IGNORECASE)


def _parse_template_params(body: str) -> dict[str, str]:
    """Split a `|key=val|key=val` template body into a flat dict.

    Top-level pipes are split on `\\n\\s*\\|` so nested templates ({{SCP|...}},
    {{Boostable|no}}) don't corrupt the parse.
    """
    out: dict[str, str] = {}
    parts = re.split(r"\n\s*\|", body)
    for part in parts:
        if "=" not in part:
            continue
        k, _, v = part.partition("=")
        k = k.strip()
        v = v.strip().rstrip("|").rstrip()
        if k:
            out[k] = v
    return out


def parse_infobox(wikitext: str) -> dict[str, str]:
    """Merge fields from {{Infobox Quest}} AND {{Quest details}}.

    Infobox Quest has the metadata (name, release, members, series, developer).
    Quest details has the gameplay info (start, difficulty, length, requirements,
    items, recommended, kills/enemies). Quest details wins on collisions because
    its fields are the canonical gameplay source.
    """
    merged: dict[str, str] = {}
    m = INFOBOX_RE.search(wikitext)
    if m:
        merged.update(_parse_template_params(m.group(1)))
    md = QUEST_DETAILS_RE.search(wikitext)
    if md:
        merged.update(_parse_template_params(md.group(1)))
    return merged


def clean_wikitext(text: str) -> str:
    """Strip wiki markup but keep prose + bullet structure readable."""
    # Pull bullet items out of {{Checklist|*foo|*bar}}-style templates first.
    def expand_checklist(m: re.Match[str]) -> str:
        body = m.group(1)
        items = re.split(r"\|\s*\*\s*", body)
        items = [i.strip().rstrip("|") for i in items if i.strip()]
        return "\n".join(f"- {i}" for i in items if i and not i.lower().startswith("checklist"))

    text = re.sub(r"\{\{\s*Checklist\s*\|(.+?)\}\}", expand_checklist, text, flags=re.DOTALL | re.IGNORECASE)
    text = re.sub(r"\{\{\s*Chat option\s*\|[^}]*\}\}", "", text, flags=re.IGNORECASE)
    text = re.sub(r"\{\{\s*[^{}]*?\}\}", "", text, flags=re.DOTALL)  # leftover templates
    # Wiki links: [[Page|label]] → label, [[Page]] → Page
    text = re.sub(r"\[\[[^\]|]*\|([^\]]+)\]\]", r"\1", text)
    text = re.sub(r"\[\[([^\]]+)\]\]", r"\1", text)
    # Bold / italic
    text = re.sub(r"'''([^']+?)'''", r"\1", text)
    text = re.sub(r"''([^']+?)''", r"\1", text)
    # <ref>...</ref>, HTML
    text = re.sub(r"<ref[^>]*>.*?</ref>", "", text, flags=re.DOTALL | re.IGNORECASE)
    text = re.sub(r"<[^>]+>", "", text)
    # Collapse blank lines
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def parse_walkthrough_sections(wikitext: str) -> list[dict[str, str]]:
    """Split the Quick guide's walkthrough into sections + sub-sections.

    Outputs a flat list of {section, title, content}. `section` is the top-level
    `== Heading ==` (usually "Walkthrough"); `title` is the `=== Sub ===` step.
    Quests without subsections just get one entry with title = "".
    """
    if not wikitext:
        return []
    # Find all headings with their level (2 = ==X==, 3 = ===X===).
    heading_re = re.compile(r"^(={2,3})\s*(.+?)\s*\1\s*$", re.MULTILINE)
    headings = [(m.start(), m.end(), len(m.group(1)), m.group(2).strip()) for m in heading_re.finditer(wikitext)]
    if not headings:
        return []

    sections: list[dict[str, str]] = []
    section_title = ""
    for i, (h_start, h_end, level, title) in enumerate(headings):
        body_start = h_end
        body_end = headings[i + 1][0] if i + 1 < len(headings) else len(wikitext)
        body = wikitext[body_start:body_end]
        cleaned = clean_wikitext(body)

        if level == 2:
            section_title = title
            # Top-level section may still have content directly below it (no sub-headings).
            if cleaned and (i + 1 >= len(headings) or headings[i + 1][2] == 2):
                sections.append({"section": section_title, "title": "", "content": cleaned})
        else:  # level 3
            if cleaned:
                sections.append({"section": section_title, "title": title, "content": cleaned})
    return sections


_QTY_PREFIX_RE = re.compile(r"^(\d+)\s*[x×]?\s+", re.IGNORECASE)
_WIKILINK_RE = re.compile(r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\]")
_PAREN_NOTE_RE = re.compile(r"\(([^)]+)\)")
_OPTIONAL_HINTS = re.compile(r"\boptional\b|\bif\b|\brecommend|may be|can be obtained", re.IGNORECASE)


def parse_items_list(wikitext: str, default_optional: bool = False) -> list[dict]:
    """Parse an Infobox Quest items/recommended field into structured records.

    Heuristic — quests use ad-hoc wiki bullet formatting and there's no canonical
    schema. We accept that and do best-effort:
      - Each bullet line (`*`, `**`, `* `) is one entry.
      - Leading "N " is qty; default 1.
      - First [[wikilink]] is the canonical item name; falls back to leading text.
      - Trailing `(...)` becomes the note.
      - Keywords like "optional", "if", "may be" inside the note → optional=true.
    Returns [] when the field is blank/unparseable.
    """
    if not wikitext.strip():
        return []
    out: list[dict] = []
    # Split on bullet markers (preserves multi-line content per bullet).
    bullets = re.split(r"(?:^|\n)\s*\*+\s*", wikitext)
    for raw in bullets:
        line = raw.strip()
        if not line:
            continue
        # Skip section dividers etc.
        if line.startswith("=") or line.startswith("|"):
            continue

        qty = 1
        m = _QTY_PREFIX_RE.match(line)
        if m:
            qty = int(m.group(1))
            line = line[m.end():]

        # Prefer the link target (canonical name) over its display label.
        link = _WIKILINK_RE.search(line)
        if link:
            name = (link.group(2) or link.group(1)).strip()
        else:
            # No wikilink — strip leading template residue and take everything
            # up to the first comma / open-paren / "or".
            head = re.split(r",|\(|\bor\b", line, maxsplit=1)[0]
            name = clean_wikitext(head).strip()
        if not name:
            continue

        note_m = _PAREN_NOTE_RE.search(line)
        note = clean_wikitext(note_m.group(1)).strip() if note_m else None

        optional = default_optional
        if note and _OPTIONAL_HINTS.search(note):
            optional = True
        # Stripped-down "or X" alternative wording counts as optional too.
        if re.search(r"\bor\b", line, re.IGNORECASE) and not optional:
            # "Lobster or better food" — required but with alternatives.
            pass  # keep optional=False; the agent reads `note` for alternatives

        out.append({
            "name": name,
            "qty": qty,
            "optional": optional,
            "note": note,
        })
    # Fallback: if nothing parsed but the field is non-empty, return a single
    # raw entry so the agent at least sees the text.
    if not out and wikitext.strip():
        plain = clean_wikitext(wikitext)
        if plain:
            out.append({"name": plain[:120], "qty": 1, "optional": default_optional, "note": None})
    return out


def normalize_bool(v: str | None) -> bool | None:
    if not v:
        return None
    v = v.strip().lower()
    if v in ("yes", "true", "y"): return True
    if v in ("no", "false", "n"): return False
    return None


def normalize_int(v: str | None) -> int | None:
    if not v:
        return None
    m = re.match(r"(-?\d+)", v.strip())
    return int(m.group(1)) if m else None


def scrape_quest(name: str) -> dict:
    main = get_wikitext(name) or ""
    quick = get_wikitext(f"{name}/Quick guide") or ""
    infobox = parse_infobox(main)
    steps = parse_walkthrough_sections(quick)

    items_raw = infobox.get("items", "")
    recommended_raw = infobox.get("recommended", "")
    items_required = parse_items_list(items_raw, default_optional=False)
    items_recommended = parse_items_list(recommended_raw, default_optional=True)

    return {
        "name": name,
        "url": "https://oldschool.runescape.wiki/w/" + name.replace(" ", "_"),
        "quickGuideUrl": "https://oldschool.runescape.wiki/w/" + name.replace(" ", "_") + "/Quick_guide",
        "members": normalize_bool(infobox.get("members")),
        "questPoints": normalize_int(infobox.get("qp") or infobox.get("number")),
        "difficulty": clean_wikitext(infobox.get("difficulty", "")),
        "length": clean_wikitext(infobox.get("length", "")),
        "series": clean_wikitext(infobox.get("series", "")),
        "released": clean_wikitext(infobox.get("release", "")),
        "developer": clean_wikitext(infobox.get("developer", "")),
        "requirements": clean_wikitext(infobox.get("requirements", "")),
        # Structured items: required + recommended merged into one list. Each entry
        # is {name, qty, optional, note}. `optional=true` covers both the
        # `|recommended=` field AND explicit "(optional)" / "if you..." notes in
        # the required field. Note field is the parenthetical the wiki put after
        # the item ("can be obtained during the quest", "or better food", etc.).
        "items": items_required + items_recommended,
        # Originals kept as plain text in case the agent wants prose.
        "itemsRaw": clean_wikitext(items_raw),
        "recommendedRaw": clean_wikitext(recommended_raw),
        "enemies": clean_wikitext(infobox.get("kills", "") or infobox.get("enemies", "")),
        "start": clean_wikitext(infobox.get("start", "")),
        "steps": steps,
    }


def main() -> int:
    print("Listing quests...")
    quests = list_quests()
    print(f"Found {len(quests)} quests\n")

    out: dict[str, dict] = {}
    for i, name in enumerate(quests):
        print(f"  [{i+1:3d}/{len(quests)}] {name}", flush=True)
        try:
            out[name] = scrape_quest(name)
        except Exception as e:
            print(f"     FAILED: {e}", file=sys.stderr)
        time.sleep(DELAY_S)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(out, indent=2, ensure_ascii=False))
    total_steps = sum(len(q["steps"]) for q in out.values())
    print(f"\nWrote {len(out)} quests, {total_steps} step sections → {OUT_JSON}")

    # SQLite for ad-hoc queries / inspection.
    if OUT_DB.exists():
        OUT_DB.unlink()
    conn = sqlite3.connect(OUT_DB)
    cur = conn.cursor()
    cur.execute("""
        CREATE TABLE quests (
            name TEXT PRIMARY KEY,
            members INTEGER,
            quest_points INTEGER,
            difficulty TEXT,
            length TEXT,
            series TEXT,
            requirements TEXT,
            items TEXT,
            recommended TEXT,
            url TEXT
        )
    """)
    cur.execute("CREATE TABLE quest_steps (quest TEXT, idx INTEGER, section TEXT, title TEXT, content TEXT)")
    cur.execute("CREATE INDEX idx_quest_steps ON quest_steps(quest)")
    cur.execute("""
        CREATE TABLE quest_items (
            quest TEXT, name TEXT, qty INTEGER,
            optional INTEGER, note TEXT
        )
    """)
    cur.execute("CREATE INDEX idx_quest_items ON quest_items(quest)")
    cur.execute("CREATE INDEX idx_item_quest ON quest_items(name)")
    for name, q in out.items():
        cur.execute(
            "INSERT INTO quests VALUES (?,?,?,?,?,?,?,?,?,?)",
            (
                name,
                1 if q["members"] else 0 if q["members"] is False else None,
                q["questPoints"],
                q["difficulty"], q["length"], q["series"],
                q["requirements"], q["itemsRaw"], q["recommendedRaw"], q["url"],
            ),
        )
        for idx, step in enumerate(q["steps"]):
            cur.execute(
                "INSERT INTO quest_steps VALUES (?,?,?,?,?)",
                (name, idx, step["section"], step["title"], step["content"]),
            )
        for item in q["items"]:
            cur.execute(
                "INSERT INTO quest_items VALUES (?,?,?,?,?)",
                (name, item["name"], item.get("qty", 1),
                 1 if item.get("optional") else 0, item.get("note")),
            )
    conn.commit()
    conn.close()
    print(f"Wrote SQLite → {OUT_DB}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
