#!/usr/bin/env python3
"""Bulk-scrape POI coordinates from the OSRS Wiki via the parse API.

Coordinates are extracted from {{Map|...|x=X|y=Y|...}} or {{Coords|X|Y|Z}}
templates in the wikitext.

Output: a single JSON file per POI category at
  src/main/resources/poi-data/<category>.json

Usage:
  python3 scripts/wiki_scrape.py
"""

import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

UA = "osrs-llm-helper/0.1 (research)"
API = "https://oldschool.runescape.wiki/api.php"
OUT_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "poi-data"

# (wiki_page, type, display_name, notes_override)
TARGETS = [
    # -------- Slayer masters --------
    ("Turael",             "slayer_master", "Turael (Burthorpe)",        "Lowest-level master, 0 points per task, free task skipping"),
    ("Spria",              "slayer_master", "Spria (Draynor Village)",   "Equivalent to Turael; F2P-accessible"),
    ("Mazchna",            "slayer_master", "Mazchna (Canifis)",         "Low-level master; combat 20"),
    ("Vannaka",            "slayer_master", "Vannaka (Edgeville Dungeon)", "Mid-level master; combat 40"),
    ("Chaeldar",           "slayer_master", "Chaeldar (Zanaris)",        "High-level master; access requires Lost City"),
    ("Konar quo Maten",    "slayer_master", "Konar (Mount Karuulm)",     "Boss task locations; combat 75"),
    ("Nieve",              "slayer_master", "Nieve (Tree Gnome Stronghold)", "Replaced by Steve after Monkey Madness II; combat 85"),
    ("Steve",              "slayer_master", "Steve (Tree Gnome Stronghold)", "Post-MM2 Nieve replacement"),
    ("Duradel",            "slayer_master", "Duradel (Shilo Village)",   "Top-level master; combat 100; Shilo Village + 50 slayer"),
    ("Krystilia",          "slayer_master", "Krystilia (Edgeville)",     "Wilderness-only tasks"),
    ("Aya",                "slayer_master", "Aya (Burthorpe)",           "Sailing-era master; very low requirements"),

    # -------- Boss arenas --------
    ("Zulrah",                  "boss_arena", "Zulrah (Zul-Andra)",                "Solo boss; Regicide required"),
    ("Vorkath",                 "boss_arena", "Vorkath (Ungael)",                  "Dragon Slayer II required"),
    ("Cerberus",                "boss_arena", "Cerberus (Taverley Dungeon)",       "91 Slayer; Hellhound task active"),
    ("Kraken",                  "boss_arena", "Kraken (Kraken Cove)",              "87 Slayer"),
    ("Thermonuclear smoke devil","boss_arena","Thermonuclear smoke devil",         "93 Slayer; Smoke devil task"),
    ("Skotizo",                 "boss_arena", "Skotizo (Catacombs of Kourend)",    "Use Dark Totem"),
    ("Sarachnis",               "boss_arena", "Sarachnis (Forthos Dungeon)",       "Mid-level boss; A Kingdom Divided"),
    ("Demonic gorillas",        "boss_arena", "Demonic gorillas (Crash Site Cavern)", "Monkey Madness II"),
    ("General Graardor",        "boss_arena", "General Graardor (Bandos GWD)",     "70 strength + GWD requirements"),
    ("K'ril Tsutsaroth",        "boss_arena", "K'ril Tsutsaroth (Zamorak GWD)",    "70 hitpoints + GWD requirements"),
    ("Commander Zilyana",       "boss_arena", "Commander Zilyana (Saradomin GWD)", "70 agility + GWD requirements"),
    ("Kree'arra",               "boss_arena", "Kree'arra (Armadyl GWD)",           "70 ranged + GWD requirements"),
    ("Corporeal Beast",         "boss_arena", "Corporeal Beast (Corporeal Beast Cave)", "Spear/Halberd damage; team boss"),
    ("Wintertodt",              "boss_arena", "Wintertodt (Wintertodt Camp)",      "50 firemaking; minigame-style boss"),
    ("Tempoross",               "boss_arena", "Tempoross (Ruins of Unkah)",        "35 fishing; minigame-style boss"),
    ("Sire",                    "boss_arena", "Abyssal Sire (Abyssal Nexus)",      "85 slayer"),
    ("Grotesque Guardians",     "boss_arena", "Grotesque Guardians (Slayer Tower roof)", "75 slayer + Gargoyle task; needs brittle key"),
    ("Alchemical Hydra",        "boss_arena", "Alchemical Hydra (Karuulm Slayer Dungeon)", "95 slayer"),
    ("Phantom Muspah",          "boss_arena", "Phantom Muspah (Ancient Vault)",    "Secrets of the North required"),
    ("Duke Sucellus",           "boss_arena", "Duke Sucellus (Ghorrock)",          "DT2; req Desert Treasure II"),
    ("Vardorvis",               "boss_arena", "Vardorvis (Stranglewood)",          "DT2"),
    ("The Leviathan",           "boss_arena", "The Leviathan (Lassar Undercity)",  "DT2"),
    ("The Whisperer",           "boss_arena", "The Whisperer (Ungael)",            "DT2"),
    ("The Nightmare",           "boss_arena", "The Nightmare (Sisterhood Sanctuary)", "Group/solo; Sins of the Father"),
    ("Phosani's Nightmare",     "boss_arena", "Phosani's Nightmare (Sisterhood Sanctuary)", "Solo-only Nightmare variant"),
    ("Nex",                     "boss_arena", "Nex (Ancient Prison)",              "Frozen Door access required"),
    ("Inferno",                 "boss_arena", "The Inferno (Mor Ul Rek)",          "Solo only; req fire cape"),
    ("Fight Caves",             "boss_arena", "Fight Caves (Mor Ul Rek)",          "Solo; rewards fire cape"),

    # -------- Slayer dungeons & common task spots --------
    ("Slayer Tower",            "slayer_dungeon", "Slayer Tower (Canifis)",         "Banshees, gargoyles, abyssal demons, nechryael"),
    ("Stronghold Slayer Cave",  "slayer_dungeon", "Stronghold Slayer Cave",         "F2P-accessible; many low/mid tasks"),
    ("Fremennik Slayer Dungeon","slayer_dungeon", "Fremennik Slayer Dungeon (Rellekka)", "Cave crawlers, jellies, dust devils"),
    ("Catacombs of Kourend",    "slayer_dungeon", "Catacombs of Kourend",           "Dark totem fragments; large variety of tasks"),
    ("Smoke Devil Dungeon",     "slayer_dungeon", "Smoke Devil Dungeon",            "93 slayer; access via Pollnivneach"),
    ("Iorwerth Dungeon",        "slayer_dungeon", "Iorwerth Dungeon (Prifddinas)",  "Dark beasts, Mourners, Crystalline mobs"),
    ("Brimhaven Dungeon",       "slayer_dungeon", "Brimhaven Dungeon",              "Bronze dragons, hellhounds, etc."),
    ("Taverley Dungeon",        "slayer_dungeon", "Taverley Dungeon",               "Black demons, blue dragons, Cerberus access"),
    ("Lumbridge Swamp Caves",   "slayer_dungeon", "Lumbridge Swamp Caves",          "Low-level slayer + Cave goblins"),
    ("Asgarnian Ice Dungeon",   "slayer_dungeon", "Asgarnian Ice Dungeon",          "Ice warriors, ice giants"),
    ("God Wars Dungeon",        "slayer_dungeon", "God Wars Dungeon entrance",      "Killcount required for each boss room"),
    ("Karuulm Slayer Dungeon",  "slayer_dungeon", "Karuulm Slayer Dungeon",         "Hot floor — boots of stone required"),
    ("Forthos Dungeon",         "slayer_dungeon", "Forthos Dungeon",                "A Kingdom Divided"),
    ("Lithkren Vault",          "slayer_dungeon", "Lithkren Vault",                 "Adamant dragons, rune dragons"),

    # -------- Notable shops --------
    ("Aubury",                  "shop", "Aubury's Rune Shop (Varrock)",     "Sells essence; teleports to Essence Mine"),
    ("Lundail",                 "shop", "Lundail's Rune Shop (Mage Arena)", "Sells law, death, blood, and elemental runes"),
    ("Ali Morrisane",           "shop", "Ali Morrisane (Al-Kharid)",        "General store + black market events"),
    ("Apothecary",              "shop", "Apothecary (Varrock)",             "Strength potion quest + cosmetic potions"),
    ("Jatix",                   "shop", "Jatix's Herblore Shop (Taverley)", "Vials, eye of newt"),
    ("Razmire",                 "shop", "Razmire's General Store (Mort'ton)", "Sawmill access + general goods"),
    ("Bob's Brilliant Axes",    "shop", "Bob's Brilliant Axes (Lumbridge)", "Axes, basic tools, RIMMINGTON shop"),
    ("Lowe's Archery Emporium", "shop", "Lowe's Archery Emporium (Varrock)", "Bows + arrows, F2P range gear"),
    ("Brian's Archery Supplies","shop", "Brian's Archery Supplies (Rimmington)", "Buckler shop"),
    ("Tyras Camp",              "shop", "Tyras Camp Rune Shop",             "Sells various elemental runes"),
    ("Wizards' Guild Store",    "shop", "Wizards' Guild rune store",        "66 magic; sells law/cosmic/chaos"),
]

# -------- Quest start NPCs (subset). Quest names match RuneLite QuestSnapshot. --------
QUESTS = [
    ("Reldo",             "Cook's Assistant",        "Kitchen, Lumbridge Castle"),
    ("Veronica",          "Vampyre Slayer",          "Outside Draynor Village"),
    ("Hetty",             "Witch's Potion",          "Rimmington witch's hut"),
    ("Father Aereck",     "The Restless Ghost",      "Lumbridge church"),
    ("Romeo",             "Romeo and Juliet",        "Varrock west square"),
    ("Wydin",             "Pirate's Treasure",       "Port Sarim general store"),
    ("Doric",             "Doric's Quest",           "Northwest of Falador"),
    ("Sir Amik Varze",    "Black Knights' Fortress", "White Knights' Castle Falador"),
    ("Wizard Mizgog",     "Imp Catcher",             "Wizards' Tower top floor"),
    ("Generals Bentnoze and Wartface","Goblin Diplomacy", "Goblin Village"),
    ("Ned",               "Sheep Herder",            "Lumbridge"),
    ("Fred the Farmer",   "Sheep Shearer",           "Lumbridge farm"),
    ("Guildmaster",       "Dragon Slayer I",         "Champions' Guild, south Varrock"),
    ("Wormbrain",         "Pirate's Treasure",       "Port Sarim jail"),
    ("Hairdresser",       "Misthalin Mystery",       "Falador hair salon"),
    ("Charlie the Tramp", "X Marks the Spot",        "Lumbridge graveyard"),
    ("Reldo (RFD)",       "Recipe for Disaster",     "Lumbridge Castle kitchen"),
    ("Camorra",           "Tree Gnome Village",      "Tree Gnome Village"),
    ("King Bolren",       "Tree Gnome Village",      "Tree Gnome Village center"),
    ("Brother Omad",      "Monk's Friend",           "Ardougne Monastery"),
    ("Eohric",            "The Knight's Sword",      "Varrock Castle"),
    ("Hassan",            "Prince Ali Rescue",       "Al-Kharid palace"),
    ("Caroline",          "Sea Slug",                "Witchaven"),
    ("Lord Iorwerth",     "Mourning's End Part I",   "Iorwerth Camp, Tirannwn"),
    ("Glough",            "The Grand Tree",          "Stronghold of Gnome Village"),
    ("Achietties",        "Heroes' Quest",           "Heroes' Guild"),
    ("King Arthur",       "Holy Grail",              "Camelot throne room"),
    ("Sigli the Huntsman","The Fremennik Trials",    "Rellekka"),
]


def fetch_wikitext(page: str) -> str | None:
    url = f"{API}?action=parse&prop=wikitext&page={urllib.parse.quote(page)}&format=json&redirects=1"
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            data = json.loads(r.read())
        return data.get("parse", {}).get("wikitext", {}).get("*")
    except Exception as e:
        print(f"  ! failed to fetch {page}: {e}", file=sys.stderr)
        return None


MAP_RE = re.compile(r"\{\{Map\b[^}]*?x\s*=\s*(\d+)[^}]*?y\s*=\s*(\d+)(?:[^}]*?z\s*=\s*(\d+))?", re.IGNORECASE | re.DOTALL)
COORDS_TMPL_RE = re.compile(r"\{\{Coords\s*\|\s*(\d+)\s*\|\s*(\d+)(?:\s*\|\s*(\d+))?\s*\}\}", re.IGNORECASE)


def extract_coord(wikitext: str) -> tuple[int, int, int] | None:
    """Return (x, y, plane) from the first plausible coord template."""
    if not wikitext:
        return None
    m = MAP_RE.search(wikitext)
    if m:
        return int(m.group(1)), int(m.group(2)), int(m.group(3) or 0)
    m = COORDS_TMPL_RE.search(wikitext)
    if m:
        return int(m.group(1)), int(m.group(2)), int(m.group(3) or 0)
    return None


def scrape(targets: list[tuple[str, str, str, str | None]]) -> list[dict]:
    pois = []
    for i, (page, ptype, name, notes) in enumerate(targets, 1):
        print(f"[{i}/{len(targets)}] {page} ({ptype}) … ", end="", flush=True)
        wt = fetch_wikitext(page)
        if wt is None:
            print("FAIL")
            continue
        coord = extract_coord(wt)
        if coord is None:
            print("no coords found")
            continue
        x, y, z = coord
        entry = {"type": ptype, "name": name, "x": x, "y": y, "plane": z}
        if notes:
            entry["notes"] = notes
        pois.append(entry)
        print(f"({x},{y},{z})")
        time.sleep(0.15)  # be polite
    return pois


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    print("=== Slayer / bosses / shops ===")
    pois = scrape(TARGETS)

    print("\n=== Quest start NPCs ===")
    quest_targets = [(npc.split(" (")[0], "quest_start", f"{quest} — {npc}", notes) for npc, quest, notes in QUESTS]
    pois.extend(scrape(quest_targets))

    out = OUT_DIR / "wiki-scraped.json"
    out.write_text(json.dumps({"pois": pois}, indent=2))
    print(f"\nWrote {len(pois)} POIs to {out}")


if __name__ == "__main__":
    main()
