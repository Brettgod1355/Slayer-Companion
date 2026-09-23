#!/usr/bin/env python3
"""
Slayer Companion - wiki data generator (maintainer tool, never shipped to users).

Data flow
=========

    task-enum.txt ----------------------------------------------+
    (ENUM, display name, alt NPC names)                         |
                                                                v
    +-------------+   Slayer task/<name>   +---------------+  build_task()
    | WikiCache   | <--------------------- | resolve_task_ |  one record per
    | (API fetch, |   <singular> / <boss>  | page()        |  display name
    |  disk cache)|                        +---------------+
    |             |   monster page(s)      +---------------+
    |             | <--------------------- | resolve_      |
    +-------------+                        | monster_pages |
          |                                +---------------+
          v  raw wikitext
    parse_infobox_slayer()      requirements, task id, per-master ranges
    parse_gear_sections()       <tabber> labels -> Recommended equipment,
                                Equipment, Inventory, Rune pouch templates
    parse_strategy()            intro + ==Strategy== prose as plain text
    parse_unlocks()             "Related slayer shop options" table
    parse_infobox_monster()     slayer xp, combat, hp, style, max hit, ...
    parse_loclines()            {{LocLine}} -> locations with spawn coords
          |
          v
    merge_locations()           dedupe by (location text, plane) -> stable id
    apply_curated()             curated/verified/*.json overrides + extra locs
          |
          v
    out/tasks.json  out/locations.json  out/report.json  out/item-names.txt

Standard library only.  All network access goes through WikiCache which
stores every page (and every "missing page" answer) under cache/ so re-runs
are free unless --refresh is given.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

# --------------------------------------------------------------------------
# Constants
# --------------------------------------------------------------------------

USER_AGENT = "SlayerCompanion-datagen/0.1 (github.com/Brettgod1355/Slayer-Companion)"
API_URL = "https://oldschool.runescape.wiki/api.php"
FETCH_SLEEP = 0.5
BACKOFF_SECONDS = (2, 4, 8)

MASTERS = (
    "turael", "spria", "mazchna", "vannaka", "chaeldar",
    "konar", "nieve", "duradel", "krystilia", "mortimer",
)

GEAR_SLOTS = (
    "head", "neck", "cape", "body", "legs", "weapon", "shield",
    "ammo", "hands", "feet", "ring", "special",
)

# Task display name -> exact wiki page to use as the "task page".
# Fill this in as the report shows tasks whose page could not be found.
TASK_PAGE_OVERRIDES: dict[str, str] = {
    "Tzhaar": "Slayer task/TzHaar",
    "Cave kraken": "Slayer task/Cave krakens",
    "Fossil island wyverns": "Slayer task/Fossil Island wyverns",
    "The Cave Kraken Boss": "Kraken",
    "The Thermonuclear Smoke Devil": "Thermonuclear smoke devil",
    "Elves": "Slayer task/Elves",
}

# Task display name -> list of monster pages that REPLACE the automatic
# singular guess (alternatives from the task list are still added).
MONSTER_PAGE_OVERRIDES: dict[str, list[str]] = {
    "Bloodveld": ["Bloodveld"],
    "The Cave Kraken Boss": ["Kraken"],
    "The Thermonuclear Smoke Devil": ["Thermonuclear smoke devil"],
    # overview pages without {{Infobox Monster}}: use the linked variant pages
    "Barrows Brothers": ["Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
                         "Karil the Tainted", "Torag the Corrupted", "Verac the Defiled"],
    "Dagannoth Kings": ["Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme"],
    "Kalphites": ["Kalphite Worker", "Kalphite Soldier", "Kalphite Guardian"],
    "Revenants": ["Revenant imp", "Revenant goblin", "Revenant pyrefiend", "Revenant hobgoblin",
                  "Revenant cyclops", "Revenant hellhound", "Revenant demon", "Revenant ork",
                  "Revenant dark beast", "Revenant knight", "Revenant dragon", "Revenant maledictus"],
    "Sea snakes": ["Sea Snake Hatchling", "Sea Snake Young", "Giant Sea Snake"],
    "Tzhaar": ["TzHaar-Ket", "TzHaar-Xil", "TzHaar-Mej", "TzHaar-Hur"],
    "Custodian Stalkers": ["Elder custodian stalker", "Mature custodian stalker",
                           "Custodian stalker (monster)", "Ancient custodian stalker"],
}

# Tasks whose task page is a monster page but whose {{Infobox Monster}} `cat`
# does not contain "Bosses" although the task is a boss-style assignment.
BOSS_TASK_OVERRIDES = {"TzTok-Jad", "TzKal-Zuk"}

# Task display name -> extra monster pages fetched IN ADDITION to the
# automatic guess and the alternatives (e.g. variants with their own page).
EXTRA_MONSTER_PAGES: dict[str, list[str]] = {
    "Bloodveld": ["Mutated Bloodveld"],
    "Barrows Brothers": ["Barrows"],
}

# Wilderness surface bounding box (plane 0), used only as a hint that curated
# data can override.
WILDERNESS_BOX = (2944, 3520, 3392, 3970)


# --------------------------------------------------------------------------
# Wikitext primitives
# --------------------------------------------------------------------------

@dataclass
class Template:
    start: int
    end: int
    name: str      # lowercase, underscores -> spaces
    body: str      # text after the first '|' (without the braces)

    @property
    def args(self) -> tuple[list[str], dict[str, str]]:
        return split_args(self.body)


def find_templates(text: str, names: Iterable[str] | None = None) -> list[Template]:
    """Return every template (nested ones included) whose lowercase name is in
    `names` (or all templates when names is None), ordered by start offset."""
    wanted = None if names is None else {n.lower() for n in names}
    found: list[Template] = []
    stack: list[int] = []
    i, n = 0, len(text)
    while i < n - 1:
        if text[i] == "{" and text[i + 1] == "{":
            stack.append(i)
            i += 2
            continue
        if text[i] == "}" and text[i + 1] == "}" and stack:
            start = stack.pop()
            inner = text[start + 2:i]
            m = re.match(r"\s*([^|{}]*?)\s*(\||$)", inner, re.S)
            name = m.group(1).replace("_", " ").strip().lower() if m else ""
            body = inner[m.end():] if (m and m.group(2) == "|") else ""
            if wanted is None or name in wanted:
                found.append(Template(start, i + 2, name, body))
            i += 2
            continue
        i += 1
    found.sort(key=lambda t: t.start)
    return found


def _split_top_level(body: str, sep: str = "|") -> list[str]:
    parts: list[str] = []
    cur: list[str] = []
    depth_t = depth_l = 0
    i, n = 0, len(body)
    while i < n:
        two = body[i:i + 2]
        if two == "{{":
            depth_t += 1
            cur.append(two)
            i += 2
            continue
        if two == "}}":
            depth_t -= 1
            cur.append(two)
            i += 2
            continue
        if two == "[[":
            depth_l += 1
            cur.append(two)
            i += 2
            continue
        if two == "]]":
            depth_l -= 1
            cur.append(two)
            i += 2
            continue
        if body[i] == sep and depth_t == 0 and depth_l == 0:
            parts.append("".join(cur))
            cur = []
            i += 1
            continue
        cur.append(body[i])
        i += 1
    parts.append("".join(cur))
    return parts


def split_args(body: str) -> tuple[list[str], dict[str, str]]:
    """Split a template body into positional args and named args (keys lowercased)."""
    positional: list[str] = []
    named: dict[str, str] = {}
    for part in _split_top_level(body):
        eq = -1
        depth_t = depth_l = 0
        for j in range(len(part)):
            two = part[j:j + 2]
            if two == "{{":
                depth_t += 1
            elif two == "}}":
                depth_t -= 1
            elif two == "[[":
                depth_l += 1
            elif two == "]]":
                depth_l -= 1
            elif part[j] == "=" and depth_t == 0 and depth_l == 0:
                eq = j
                break
        key = part[:eq] if eq >= 0 else ""
        if eq >= 0 and re.fullmatch(r"\s*[\w\s\-']+\s*", key):
            named[key.strip().lower()] = part[eq + 1:].strip()
        else:
            positional.append(part.strip())
    return positional, named


_INNER_TEMPLATE = re.compile(r"\{\{([^{}]*)\}\}", re.S)


def _ordinal(n: int) -> str:
    if 10 <= n % 100 <= 20:
        suf = "th"
    else:
        suf = {1: "st", 2: "nd", 3: "rd"}.get(n % 10, "th")
    return f"{n}{suf}"


def render_template_text(inner: str) -> str:
    """Plain-text rendering of one (innermost) template; unknown templates render as ''."""
    positional, named = split_args(inner)
    name = positional[0].replace("_", " ").strip().lower() if positional else ""
    rest = positional[1:]
    if name in ("scp", "skill clickpic", "skill"):
        if len(rest) >= 2:
            return f"{rest[1]} {rest[0]}"
        return " ".join(rest)
    if name == "fairycode":
        return "fairy ring " + (rest[0].upper() if rest else "")
    if name == "floornumber":
        uk = named.get("uk")
        if uk is not None and uk.strip().lstrip("-").isdigit():
            k = int(uk)
            if k == 0:
                return "ground floor"
            if k < 0:
                return f"basement {abs(k)}"
            return f"{_ordinal(k)} floor"
        return "floor"
    if name in ("plink", "plinkp", "plinkt", "plinkl"):
        return named.get("txt") or (rest[0].split("#")[0] if rest else "")
    if name in ("nowrap", "nobr"):
        return rest[0] if rest else ""
    if name == "coins":
        return (rest[0] + " coins") if rest else ""
    if name == "na":
        return "N/A"
    if name in ("yes", "no"):
        return name.capitalize()
    if name in ("cannon",):
        return "cannon"
    return ""


def render_templates(text: str) -> str:
    prev = None
    while prev != text:
        prev = text
        text = _INNER_TEMPLATE.sub(lambda m: render_template_text(m.group(1)), text)
    return text


def remove_templates(text: str, names: Iterable[str] | None = None) -> str:
    """Remove (outermost) templates, optionally only those with the given names."""
    tpls = find_templates(text, names)
    out = []
    pos = 0
    last_end = -1
    for t in tpls:
        if t.start < last_end:
            continue  # nested inside an already-removed template
        out.append(text[pos:t.start])
        pos = t.end
        last_end = t.end
    out.append(text[pos:])
    return "".join(out)


def remove_files(text: str) -> str:
    out = []
    i, n = 0, len(text)
    while i < n:
        if text.startswith("[[", i) and re.match(r"\[\[\s*(File|Image|Media):", text[i:i + 14], re.I):
            depth = 0
            j = i
            while j < n:
                if text.startswith("[[", j):
                    depth += 1
                    j += 2
                    continue
                if text.startswith("]]", j):
                    depth -= 1
                    j += 2
                    if depth == 0:
                        break
                    continue
                j += 1
            i = j
            continue
        out.append(text[i])
        i += 1
    return "".join(out)


def remove_tables(text: str) -> str:
    out = []
    depth = 0
    for line in text.split("\n"):
        s = line.lstrip()
        if s.startswith("{|"):
            depth += 1
            continue
        if s.startswith("|}") and depth > 0:
            depth -= 1
            continue
        if depth == 0:
            out.append(line)
    return "\n".join(out)


def links_to_text(text: str) -> str:
    for _ in range(3):
        text = re.sub(r"\[\[([^\[\]|]*)\|([^\[\]]*)\]\]", r"\2", text)
        text = re.sub(r"\[\[([^\[\]|]*)\]\]", r"\1", text)
    text = re.sub(r"\[(?:https?|ftp)://[^\s\]]+\s+([^\]]*)\]", r"\1", text)
    text = re.sub(r"\[(?:https?|ftp)://[^\s\]]+\]", "", text)
    return text


def strip_refs(text: str) -> str:
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = re.sub(r"<ref[^>/]*/\s*>", "", text, flags=re.I)
    text = re.sub(r"<ref[^>]*>.*?</ref\s*>", "", text, flags=re.S | re.I)
    text = re.sub(r"<gallery[^>]*>.*?</gallery\s*>", "", text, flags=re.S | re.I)
    text = re.sub(r"<(?:math|score)[^>]*>.*?</(?:math|score)\s*>", "", text, flags=re.S | re.I)
    return text


def plain_text(wikitext: str) -> str:
    """Convert a wikitext fragment to plain text (templates rendered or dropped,
    link text kept, refs / files / tables dropped)."""
    t = strip_refs(wikitext)
    t = remove_tables(t)
    t = render_templates(t)
    t = remove_files(t)
    t = links_to_text(t)
    t = re.sub(r"<br\s*/?\s*>", "\n", t, flags=re.I)
    t = re.sub(r"</?br\s*>", "\n", t, flags=re.I)
    t = re.sub(r"'''''|'''|''", "", t)
    t = re.sub(r"<[^>]+>", "", t)
    t = html.unescape(t)
    t = re.sub(r"[ \t]+", " ", t)
    t = re.sub(r" *\n *", "\n", t)
    t = re.sub(r"\(\s*\)", "", t)
    t = re.sub(r" +([,.;:])", r"\1", t)
    return t.strip()


def paragraphs(plain: str) -> list[str]:
    """Split plain text into paragraphs; bullet lines are grouped into one paragraph."""
    paras: list[str] = []
    cur: list[str] = []
    cur_is_list = False
    for raw in plain.split("\n"):
        line = raw.strip()
        if not line:
            if cur:
                paras.append("\n".join(cur))
                cur, cur_is_list = [], False
            continue
        m = re.match(r"^(={2,6})\s*(.+?)\s*=+$", line)
        if m:
            if cur:
                paras.append("\n".join(cur))
                cur, cur_is_list = [], False
            paras.append(m.group(2) + ":")
            continue
        is_list = line[0] in "*#;:"
        if is_list:
            line = "- " + line.lstrip("*#;: ").strip()
            if cur and not cur_is_list:
                paras.append("\n".join(cur))
                cur = []
            cur_is_list = True
            cur.append(line)
        else:
            if cur and cur_is_list:
                paras.append("\n".join(cur))
                cur = []
            cur_is_list = False
            cur.append(line)
    if cur:
        paras.append("\n".join(cur))
    paras = [p for p in paras if p and re.search(r"[A-Za-z0-9]", p)]
    # drop heading-only paragraphs whose section produced no prose (e.g. a
    # heading followed only by a table that was stripped)
    cleaned: list[str] = []
    for i, p in enumerate(paras):
        is_heading = p.endswith(":") and "\n" not in p and len(p) < 80
        nxt = paras[i + 1] if i + 1 < len(paras) else None
        if is_heading and (nxt is None or (nxt.endswith(":") and "\n" not in nxt and len(nxt) < 80)):
            continue
        cleaned.append(p)
    return cleaned


_HEADING = re.compile(r"^(={2,6})\s*(.+?)\s*=+\s*$", re.M)


def sections(text: str) -> list[dict]:
    """Return [{level, title, start(body start), end(body end), head_start}]."""
    heads = list(_HEADING.finditer(text))
    out = []
    for i, m in enumerate(heads):
        level = len(m.group(1))
        body_start = m.end()
        end = len(text)
        for later in heads[i + 1:]:
            if len(later.group(1)) <= level:
                end = later.start()
                break
        out.append({"level": level, "title": m.group(2).strip(), "start": body_start,
                    "end": end, "head_start": m.start()})
    return out


def to_int(value) -> int | None:
    if value is None:
        return None
    s = str(value).strip().replace(",", "")
    m = re.search(r"-?\d+", s)
    if not m:
        return None
    try:
        return int(m.group(0))
    except ValueError:
        return None


def slugify(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return s or "unnamed"


# --------------------------------------------------------------------------
# Fetching with cache
# --------------------------------------------------------------------------

@dataclass
class Page:
    requested: str
    title: str | None
    wikitext: str | None
    redirected: bool
    status: str            # ok | missing | error
    error: str | None = None

    @property
    def ok(self) -> bool:
        return self.status == "ok" and self.wikitext is not None


def sanitise_title(title: str) -> str:
    return title.replace("/", "__").replace(" ", "_").replace(":", "_")


class WikiCache:
    """Fetches wikitext via the MediaWiki API, caching every answer on disk."""

    def __init__(self, cache_dir: Path, refresh: bool = False, sleep: float = FETCH_SLEEP):
        self.cache_dir = cache_dir
        self.refresh = refresh
        self.sleep = sleep
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.fetches = 0
        self.failed: dict[str, str] = {}
        self._mem: dict[str, Page] = {}

    def _paths(self, title: str) -> tuple[Path, Path]:
        base = self.cache_dir / sanitise_title(title)
        return base.with_suffix(".wikitext"), base.with_suffix(".meta.json")

    def get(self, title: str) -> Page:
        title = title.strip()
        if title in self._mem:
            return self._mem[title]
        text_path, meta_path = self._paths(title)
        if not self.refresh and meta_path.exists():
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
            if meta.get("status") == "ok" and text_path.exists():
                page = Page(title, meta.get("title"), text_path.read_text(encoding="utf-8"),
                            bool(meta.get("redirected")), "ok")
                self._mem[title] = page
                return page
            if meta.get("status") == "missing":
                page = Page(title, None, None, False, "missing", meta.get("error"))
                self._mem[title] = page
                return page
        page = self._fetch(title)
        if page.status in ("ok", "missing"):
            meta = {"requested": title, "title": page.title, "redirected": page.redirected,
                    "status": page.status, "error": page.error, "fetched": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
            meta_path.write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")
            if page.ok:
                text_path.write_text(page.wikitext, encoding="utf-8")
        else:
            self.failed[title] = page.error or "unknown error"
        self._mem[title] = page
        return page

    def _fetch(self, title: str) -> Page:
        params = {"action": "parse", "page": title, "prop": "wikitext", "format": "json",
                  "formatversion": "2", "redirects": "1"}
        url = API_URL + "?" + urllib.parse.urlencode(params)
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        attempts = 0
        while True:
            attempts += 1
            try:
                self.fetches += 1
                print(f"  fetch [{self.fetches}] {title}", file=sys.stderr)
                with urllib.request.urlopen(req, timeout=60) as resp:
                    data = json.load(resp)
                time.sleep(self.sleep)
                break
            except urllib.error.HTTPError as e:
                if e.code == 429 and attempts <= len(BACKOFF_SECONDS):
                    wait = BACKOFF_SECONDS[attempts - 1]
                    print(f"  429 for {title}; backing off {wait}s", file=sys.stderr)
                    time.sleep(wait)
                    continue
                time.sleep(self.sleep)
                return Page(title, None, None, False, "error", f"HTTP {e.code}")
            except (urllib.error.URLError, TimeoutError, OSError, ValueError) as e:
                time.sleep(self.sleep)
                return Page(title, None, None, False, "error", f"{type(e).__name__}: {e}")
        if "error" in data:
            code = data["error"].get("code", "")
            if code in ("missingtitle", "nosuchpageid", "invalidtitle"):
                return Page(title, None, None, False, "missing", code)
            return Page(title, None, None, False, "error", json.dumps(data["error"]))
        parse = data.get("parse", {})
        final = parse.get("title")
        redirected = bool(parse.get("redirects")) or (
            final is not None and _norm_title(final) != _norm_title(title))
        return Page(title, final, parse.get("wikitext", ""), redirected, "ok")


def _norm_title(t: str) -> str:
    t = t.replace("_", " ").strip()
    return (t[:1].upper() + t[1:]) if t else t


# --------------------------------------------------------------------------
# Page mapping
# --------------------------------------------------------------------------

def strip_the(name: str) -> str:
    return re.sub(r"^The\s+", "", name, flags=re.I)


def singular_variants(name: str) -> list[str]:
    """Best-effort singular forms of a task name, most likely first."""
    out: list[str] = []
    base = strip_the(name)
    low = base.lower()
    if low.endswith("ves"):
        out += [base[:-3] + "f", base[:-3] + "fe"]
    if low.endswith("ies"):
        out.append(base[:-3] + "y")
    if low.endswith("s") and not low.endswith("ss"):
        out.append(base[:-1])
    if low.endswith("es"):
        out.append(base[:-2])
    out.append(base)
    seen: list[str] = []
    for v in out:
        if v and v not in seen:
            seen.append(v)
    return seen


def resolve_task_page(cache: WikiCache, display: str) -> tuple[Page | None, list[str]]:
    """Try the override, 'Slayer task/<name>' variants, then the bare name."""
    tried: list[str] = []
    candidates: list[str] = []
    if display in TASK_PAGE_OVERRIDES:
        candidates.append(TASK_PAGE_OVERRIDES[display])
    candidates.append(f"Slayer task/{display}")
    if strip_the(display) != display:
        candidates.append(f"Slayer task/{strip_the(display)}")
    for s in singular_variants(display):
        candidates.append(f"Slayer task/{s}")
    candidates.append(display)
    candidates.append(strip_the(display))
    for s in singular_variants(display):
        candidates.append(s)
    seen: set[str] = set()
    for c in candidates:
        if c in seen:
            continue
        seen.add(c)
        tried.append(c)
        page = cache.get(c)
        if page.ok:
            return page, tried
    return None, tried


def resolve_monster_pages(cache: WikiCache, display: str, alternatives: list[str],
                          task_page: Page | None) -> tuple[list[Page], list[str]]:
    """Monster pages: override list or first existing singular guess, plus every
    alternative NPC name, plus the task page itself if it is a monster page."""
    pages: list[Page] = []
    tried: list[str] = []
    seen_titles: set[str] = set()

    def add(page: Page) -> None:
        if page.ok and page.title not in seen_titles:
            seen_titles.add(page.title)
            pages.append(page)

    if display in MONSTER_PAGE_OVERRIDES:
        for t in MONSTER_PAGE_OVERRIDES[display]:
            tried.append(t)
            add(cache.get(t))
    else:
        for s in singular_variants(display):
            tried.append(s)
            p = cache.get(s)
            if p.ok and "{{infobox monster" in p.wikitext.lower():
                add(p)
                break
    for extra in EXTRA_MONSTER_PAGES.get(display, []) + alternatives:
        extra = extra.strip()
        if extra:
            tried.append(extra)
            add(cache.get(extra))
    if task_page and task_page.ok and "{{infobox monster" in task_page.wikitext.lower():
        add(task_page)
    return pages, tried


# --------------------------------------------------------------------------
# Parsers: task page
# --------------------------------------------------------------------------

def parse_master_string(raw: str) -> dict | None:
    """'120-170 (200-250) (Weighting 9)' -> min,max,extMin,extMax,weight.
    Mortimer's '120-180 (200-250) <br/> or 170-280 (250-350)' keeps both ranges."""
    if raw is None:
        return None
    clean = render_templates(strip_refs(raw))
    weight = None
    mw = re.search(r"weighting\s*:?\s*(\d+)", clean, re.I)
    if mw:
        weight = int(mw.group(1))
    clean_no_w = re.sub(r"\(?\s*weighting\s*:?\s*\d+\s*\)?", "", clean, flags=re.I)
    parts = [p.strip() for p in re.split(r"<br\s*/?>|\bor\b|\n", clean_no_w, flags=re.I) if p.strip()]
    ranges_out: list[dict] = []
    for part in parts:
        # "120-170 (200-250)" or a fixed amount "50 (91-150)"
        ranges = [(int(a), int(b) if b else int(a))
                  for a, b in re.findall(r"(\d+)(?:\s*[-–]\s*(\d+))?", part)]
        if not ranges:
            continue
        r = {"min": ranges[0][0], "max": ranges[0][1], "extMin": None, "extMax": None}
        if len(ranges) > 1:
            r["extMin"], r["extMax"] = ranges[1]
        ranges_out.append(r)
    if not ranges_out:
        return None
    first = ranges_out[0]
    return {
        "raw": raw.strip(),
        "min": first["min"], "max": first["max"],
        "extMin": first["extMin"], "extMax": first["extMax"],
        "weight": weight,
        "alternatives": ranges_out[1:],
    }


def parse_skill_reqs(raw: str | None) -> list[dict]:
    out = []
    if not raw:
        return out
    for t in find_templates(raw, ("scp", "skill clickpic")):
        pos, _ = t.args
        if len(pos) >= 2 and to_int(pos[1]) is not None:
            out.append({"skill": pos[0].strip(), "level": to_int(pos[1])})
    return out


def parse_infobox_slayer(text: str) -> dict | None:
    tpls = find_templates(text, ("infobox slayer",))
    if not tpls:
        return None
    _, named = tpls[0].args
    masters = {}
    for m in MASTERS:
        if m in named:
            parsed = parse_master_string(named[m])
            if parsed:
                masters[m] = parsed
    combat = None
    for t in find_templates(named.get("combatreq", ""), ("scp", "skill clickpic")):
        pos, _ = t.args
        if len(pos) >= 2 and pos[0].strip().lower() == "combat":
            combat = to_int(pos[1])
    other = plain_text(named.get("otherreq", "")) or None
    if other and other.lower() == "none":
        other = None
    skills = parse_skill_reqs(named.get("skillreq"))
    slayer_level = next((s["level"] for s in skills if s["skill"].lower() == "slayer"), None)
    return {
        "name": plain_text(named.get("name", "")) or None,
        "taskId": to_int(named.get("id")),
        "requirements": {
            "skills": skills,
            "slayer": slayer_level,
            "combat": combat,
            "other": other,
        },
        "masters": masters,
    }


def collect_efn_names(text: str) -> dict[str, str]:
    """Map efn name -> note text for every named footnote defined on the page."""
    out: dict[str, str] = {}
    for t in find_templates(text, ("efn",)):
        pos, named = t.args
        note = pos[0] if pos else ""
        if named.get("name") and note and named["name"] not in out:
            out[named["name"]] = plain_text(note)
    return out


def _extract_items(value: str) -> list[dict]:
    items = []
    for t in find_templates(value, ("plink", "plinkp", "plinkt", "plinkl")):
        pos, named = t.args
        if not pos:
            continue
        name = pos[0].split("#")[0].strip()
        if not name:
            continue
        item = {"name": name}
        if named.get("txt"):
            item["txt"] = plain_text(named["txt"])
        if named.get("pic"):
            item["pic"] = named["pic"].strip()
        items.append(item)
    return items


def parse_recommended_equipment(t: Template, efn_names: dict[str, str]) -> dict:
    _, named = t.args
    slots: dict[str, list[list[dict]]] = {}
    slot_notes: dict[str, list[str]] = {}
    for key, value in named.items():
        m = re.fullmatch(r"([a-z]+)(\d)", key)
        if not m:
            continue
        slot, tier = m.group(1), int(m.group(2))
        if slot not in GEAR_SLOTS or not (1 <= tier <= 5):
            continue
        notes = []
        for e in find_templates(value, ("efn",)):
            pos, en = e.args
            note = plain_text(pos[0]) if pos else ""
            if not note and en.get("name"):
                note = efn_names.get(en["name"], "")
            if note:
                notes.append(f"tier {tier}: {note}")
        stripped = remove_templates(value, ("efn",))
        items = _extract_items(stripped)
        leftover = remove_templates(stripped)
        leftover = re.sub(r"<br\s*/?>", " ", leftover, flags=re.I)
        leftover = re.sub(r"[/>]|\bor:?\b", " ", leftover)
        leftover = plain_text(leftover)
        leftover = re.sub(r"[\s()]+", " ", leftover).strip()
        if re.search(r"[A-Za-z]", leftover):
            notes.append(f"tier {tier}: {leftover}")
        tiers = slots.setdefault(slot, [[], [], [], [], []])
        tiers[tier - 1] = items
        if notes:
            slot_notes.setdefault(slot, []).extend(notes)
    # trim trailing empty tiers
    for slot, tiers in slots.items():
        while tiers and not tiers[-1]:
            tiers.pop()
    return {
        "style": plain_text(named.get("style", "")) or None,
        "slots": slots,
        "slotNotes": {k: " | ".join(v) for k, v in slot_notes.items()},
    }


_EQUIPMENT_IGNORE = {"align", "buttons", "type", "title", "text", "width", "style", "class", "caption", "name", "summary"}


def parse_equipment(t: Template) -> dict:
    _, named = t.args
    equipment = {}
    for k, v in named.items():
        if k in _EQUIPMENT_IGNORE:
            continue
        val = plain_text(v)
        if val:
            equipment[k] = val
    return equipment


def parse_inventory(t: Template) -> dict:
    pos, named = t.args
    grid: list[str | None] = [None] * 28
    idx = 0
    for p in pos:
        idx += 1
        val = plain_text(p)
        if 1 <= idx <= 28 and val:
            grid[idx - 1] = val
    for k, v in named.items():
        if k.isdigit():
            n = int(k)
            val = plain_text(v)
            if 1 <= n <= 28 and val:
                grid[n - 1] = val
    return {"grid": grid, "items": [g for g in grid if g]}


def parse_rune_pouch(t: Template) -> list[str]:
    pos, named = t.args
    runes: dict[int, str] = {}
    for i, p in enumerate(pos, start=1):
        val = plain_text(p)
        if val:
            runes[i] = val
    for k, v in named.items():
        if k.isdigit():
            val = plain_text(v)
            if val:
                runes[int(k)] = val
    return [runes[k] for k in sorted(runes)]


def _chunks(text: str) -> list[tuple[str | None, str]]:
    """Split a page into (label, text) chunks: one per <tabber> tab (label = tab
    label) and, for text outside tabbers, one per level-2 section."""
    chunks: list[tuple[str | None, str]] = []
    outside: list[str] = []
    pos = 0
    for m in re.finditer(r"<tabber>(.*?)</tabber>", text, re.S | re.I):
        outside.append(text[pos:m.start()])
        pos = m.end()
        for tab in re.split(r"\|-\|", m.group(1)):
            lm = re.match(r"\s*([^=\n{}]+?)\s*=(.*)$", tab, re.S)
            if lm:
                chunks.append((lm.group(1).strip(), lm.group(2)))
            else:
                chunks.append((None, tab))
    outside.append(text[pos:])
    rest = "\n".join(outside)
    secs = sections(rest)
    if not secs:
        chunks.append((None, rest))
    else:
        chunks.append((None, rest[:secs[0]["head_start"]]))
        for s in secs:
            if s["level"] == 2:
                chunks.append((s["title"], rest[s["start"]:s["end"]]))
    return chunks


def parse_gear_sections(text: str) -> tuple[list[dict], list[dict]]:
    """Return (gearTables, exampleSetups) for the whole page."""
    efn_names = collect_efn_names(text)
    gear_tables: list[dict] = []
    setups: list[dict] = []
    for label, chunk in _chunks(text):
        recs = find_templates(chunk, ("recommended equipment",))
        equips = find_templates(chunk, ("equipment",))
        invs = find_templates(chunk, ("inventory",))
        pouches = find_templates(chunk, ("rune pouch",))
        if not (recs or equips or invs or pouches):
            continue
        notes_text = remove_templates(chunk)
        notes = paragraphs(plain_text(notes_text))
        notes_joined = "\n\n".join(notes)
        if len(notes_joined) > 1500:
            notes_joined = notes_joined[:1500].rsplit(" ", 1)[0] + "…"
        for r in recs:
            parsed = parse_recommended_equipment(r, efn_names)
            parsed["label"] = label
            gear_tables.append(parsed)
        n = max(len(equips), len(invs), len(pouches))
        for i in range(n):
            setup = {
                "label": label if n == 1 or label is None else f"{label} #{i + 1}",
                "equipment": parse_equipment(equips[i]) if i < len(equips) else {},
                "inventory": parse_inventory(invs[i])["items"] if i < len(invs) else [],
                "inventoryGrid": parse_inventory(invs[i])["grid"] if i < len(invs) else [],
                "runePouch": parse_rune_pouch(pouches[i]) if i < len(pouches) else [],
                "notes": notes_joined if i == 0 else None,
            }
            setups.append(setup)
    return gear_tables, setups


def parse_strategy(text: str, intro_paragraphs: int = 3, cap: int = 2500) -> tuple[list[str], str | None]:
    """First N intro paragraphs + every ==Strategy== paragraph, capped."""
    body = re.sub(r"<tabber>.*?</tabber>", "", text, flags=re.S | re.I)
    secs = sections(body)
    intro_end = secs[0]["head_start"] if secs else len(body)
    intro = paragraphs(plain_text(body[:intro_end]))
    strat: list[str] = []
    for s in secs:
        if s["level"] == 2 and re.fullmatch(r"strateg(y|ies)", s["title"], re.I):
            strat_body = body[s["start"]:s["end"]]
            strat = paragraphs(plain_text(strat_body))
            break
    summary = None
    if intro:
        summary = intro[0]
        if len(summary) > 400:
            cut = summary[:400]
            dot = cut.rfind(". ")
            summary = (cut[:dot + 1] if dot > 120 else cut.rsplit(" ", 1)[0] + "…")
    out: list[str] = []
    total = 0
    for p in intro[:intro_paragraphs] + strat:
        if total + len(p) > cap:
            room = cap - total
            if room > 200:
                out.append(p[:room].rsplit(" ", 1)[0] + "…")
            break
        out.append(p)
        total += len(p) + 2
    return out, summary


def _table_rows(table: str) -> list[list[str]]:
    rows: list[list[str]] = []
    body = table
    body = re.sub(r"^\{\|[^\n]*\n?", "", body)
    body = re.sub(r"\n\|\}\s*$", "", body)
    for row in re.split(r"\n\|-[^\n]*", "\n" + body):
        cells: list[str] = []
        lines = [ln for ln in row.split("\n") if ln.strip()]
        for ln in lines:
            s = ln.strip()
            if s.startswith("!") or s.startswith("|+"):
                continue
            if s.startswith("|"):
                for cell in re.split(r"\|\|", s[1:]):
                    cells.append(cell)
            elif cells:
                cells[-1] += "\n" + s
        if cells:
            rows.append(cells)
    return rows


def parse_unlocks(text: str) -> list[dict]:
    unlocks: list[dict] = []
    for s in sections(text):
        if not re.search(r"slayer (shop options|unlocks?|rewards?)", s["title"], re.I):
            continue
        body = text[s["start"]:s["end"]]
        for tbl in re.finditer(r"\{\|.*?\n\|\}", body, re.S):
            for cells in _table_rows(tbl.group(0)):
                texts = []
                for c in cells:
                    if "[[File:" in c or "[[file:" in c:
                        continue
                    texts.append(plain_text(c))
                texts = [t for t in texts if t]
                if len(texts) < 2:
                    continue
                cost = to_int(texts[1]) if re.fullmatch(r"[\d,]+", texts[1].strip()) else None
                note = " ".join(texts[2:]) if cost is not None else " ".join(texts[1:])
                unlocks.append({"name": texts[0], "cost": cost, "note": note or None})
    return unlocks


# --------------------------------------------------------------------------
# Parsers: monster page
# --------------------------------------------------------------------------

def _versioned(named: dict[str, str], key: str, versions: list[str], conv):
    """Return (scalar, byVersion) for possibly versioned infobox fields (key, key1, key2...)."""
    by_version = {}
    for i, vname in enumerate(versions, start=1):
        if f"{key}{i}" in named:
            by_version[vname] = conv(named[f"{key}{i}"])
    scalar = conv(named[key]) if key in named else None
    if scalar is None and by_version:
        scalar = next(iter(by_version.values()))
    return scalar, (by_version or None)


def _styles(raw: str) -> list[str]:
    txt = plain_text(raw)
    parts = re.split(r",|/| and |;", txt)
    return [p.strip() for p in parts if p.strip()]


def parse_infobox_monster(text: str) -> dict | None:
    tpls = find_templates(text, ("infobox monster",))
    if not tpls:
        return None
    _, named = tpls[0].args
    versions = []
    i = 1
    while f"version{i}" in named:
        versions.append(plain_text(named[f"version{i}"]) or f"v{i}")
        i += 1
    out: dict = {"name": plain_text(named.get("name", "")) or None, "versions": versions}
    fields = {
        "slayxp": ("slayerXp", to_int),
        "slaylvl": ("slayerLevel", to_int),
        "combat": ("combat", to_int),
        "hitpoints": ("hitpoints", to_int),
        "max hit": ("maxHit", lambda v: plain_text(v) or None),
        "attack style": ("attackStyles", _styles),
        "weakness": ("weakness", lambda v: plain_text(v) or None),
        "attributes": ("attributes", lambda v: plain_text(v) or None),
        "size": ("size", to_int),
        "aggressive": ("aggressive", lambda v: plain_text(v) or None),
        "poisonous": ("poisonous", lambda v: plain_text(v) or None),
        "attack speed": ("attackSpeed", to_int),
        "immunecannon": ("immuneCannon", lambda v: plain_text(v) or None),
        "immunethrall": ("immuneThrall", lambda v: plain_text(v) or None),
        "id": ("npcIds", lambda v: [to_int(x) for x in v.split(",") if to_int(x) is not None]),
        "cat": ("slayerCategory", lambda v: plain_text(v) or None),
        "assignedby": ("assignedBy", lambda v: [x.strip().lower() for x in plain_text(v).split(",") if x.strip()]),
    }
    for key, (out_key, conv) in fields.items():
        scalar, by_version = _versioned(named, key, versions, conv)
        out[out_key] = scalar
        if by_version:
            out[out_key + "ByVersion"] = by_version
    return out


_COORD = re.compile(r"x\s*:\s*(-?\d+)\s*,\s*y\s*:\s*(-?\d+)(?:\s*,\s*plane\s*:\s*(\d+))?", re.I)
_BARE_COORD = re.compile(r"^\s*(-?\d+)\s*,\s*(-?\d+)\s*(?:,|$)")


def coords_in_arg(arg: str) -> list[tuple[int, int, int | None]]:
    """Coordinates in one positional map/LocLine argument: 'x:3025,y:4916[,plane:1]'
    or the bare '3025,4916[,icon:...]' form."""
    found = [(int(m.group(1)), int(m.group(2)), int(m.group(3)) if m.group(3) else None)
             for m in _COORD.finditer(arg)]
    if not found:
        m = _BARE_COORD.match(arg)
        if m:
            found.append((int(m.group(1)), int(m.group(2)), None))
    return found


def parse_location_text(raw: str) -> dict:
    """'[[Slayer Tower]] ({{FloorNumber|uk=2}})' -> name/displayName/link/annotations."""
    annotations = []
    for t in find_templates(raw):
        rendered = render_templates(raw[t.start:t.end])
        annotations.append({"raw": raw[t.start:t.end], "text": rendered or None})
    link = None
    lm = re.search(r"\[\[([^\[\]|]*)(?:\|[^\[\]]*)?\]\]", raw)
    if lm:
        link = lm.group(1).split("#")[0].strip()
    name = plain_text(remove_templates(raw))
    name = re.sub(r"\s+", " ", name).strip(" -,")
    display = plain_text(raw)
    display = re.sub(r"\s+", " ", display).strip(" -,")
    return {"name": name or display or "Unknown", "displayName": display or name or "Unknown",
            "link": link, "annotations": annotations}


def parse_loclines(text: str, page_title: str) -> list[dict]:
    locs: list[dict] = []
    for t in find_templates(text, ("locline",)):
        pos, named = t.args
        spawns = []
        for p in pos:
            for x, y, pl in coords_in_arg(p):
                spawns.append({"x": x, "y": y, "plane": pl})
        loc_info = parse_location_text(named.get("location", ""))
        plane = to_int(named.get("plane")) or 0
        levels_txt = plain_text(named.get("levels", ""))
        levels = [int(x) for x in re.findall(r"\d+", levels_txt.replace(",", " "))]
        locs.append({
            **loc_info,
            "monster": plain_text(named.get("name", "")) or None,
            "page": page_title,
            "levels": levels,
            "levelsRaw": levels_txt or None,
            "members": plain_text(named.get("members", "")) or None,
            "mapID": to_int(named.get("mapid")),
            "plane": plane,
            "dropversion": plain_text(named.get("dropversion", "")) or None,
            "spawns": [[s["x"], s["y"]] for s in spawns],
            "source": "locline",
        })
    return locs


def parse_map_locations(text: str, page_title: str) -> list[dict]:
    """Fallback for pages without {{LocLine}} (instanced bosses): {{Map}} templates
    inside ==Location== / ==Transportation== sections."""
    locs: list[dict] = []
    for s in sections(text):
        if not re.fullmatch(r"(locations?|transportation|getting there)", s["title"], re.I):
            continue
        body = text[s["start"]:s["end"]]
        for t in find_templates(body, ("map",)):
            pos, named = t.args
            spawns: list[list[int]] = []
            titles: list[str] = []
            for p in pos:
                for x, y, _pl in coords_in_arg(p):
                    spawns.append([x, y])
                    tm = re.search(r"title\s*:\s*([^,|]+)", p)
                    if tm:
                        titles.append(tm.group(1).strip())
            if to_int(named.get("x")) is not None and to_int(named.get("y")) is not None:
                spawns.append([to_int(named["x"]), to_int(named["y"])])
            if not spawns:
                continue
            caption = plain_text(named.get("caption", "")) or plain_text(named.get("name", ""))
            display = f"{page_title} ({caption.rstrip('.')})" if caption else page_title
            locs.append({
                "name": page_title, "displayName": display, "link": page_title,
                "annotations": [{"raw": None, "text": tt} for tt in ([caption] if caption else []) + titles],
                "monster": page_title, "page": page_title, "levels": [], "levelsRaw": None,
                "members": None, "mapID": to_int(named.get("mapid")),
                "plane": to_int(named.get("plane")) or 0, "dropversion": None,
                "spawns": spawns, "source": "map",
            })
    return locs


# --------------------------------------------------------------------------
# Location merging
# --------------------------------------------------------------------------

def location_id(name: str, plane: int | None) -> str:
    return f"{slugify(name)}-p{plane if plane is not None else 'x'}"


def centroid(spawns: list[list[int]]) -> tuple[int | None, int | None]:
    if not spawns:
        return None, None
    xs = [s[0] for s in spawns]
    ys = [s[1] for s in spawns]
    return round(sum(xs) / len(xs)), round(sum(ys) / len(ys))


def looks_wilderness(loc: dict) -> bool:
    if re.search(r"wilderness", (loc.get("name") or "") + " " + (loc.get("link") or ""), re.I):
        return True
    if loc.get("plane", 0) == 0:
        x0, y0, x1, y1 = WILDERNESS_BOX
        for x, y in loc.get("spawns", []):
            if x0 <= x <= x1 and y0 <= y <= y1:
                return True
    return False


def merge_locations(raw_locs: list[dict]) -> list[dict]:
    """Dedupe LocLine records by (location text, plane) into TaskLocation records."""
    merged: dict[str, dict] = {}
    for loc in raw_locs:
        lid = location_id(loc["name"], loc["plane"])
        if lid not in merged:
            merged[lid] = {
                "id": lid,
                "name": loc["name"],
                "displayName": loc["displayName"],
                "link": loc["link"],
                "annotations": [a["text"] for a in loc["annotations"] if a["text"]],
                "annotationsRaw": [a["raw"] for a in loc["annotations"] if a["raw"]],
                "source": loc.get("source", "locline"),
                "plane": loc["plane"],
                "mapID": loc["mapID"],
                "members": loc["members"],
                "monsters": [],
                "levels": [],
                "dropversions": [],
                "pages": [],
                "spawns": [],
                "spawnsByMonster": {},
            }
        m = merged[lid]
        if loc["monster"] and loc["monster"] not in m["monsters"]:
            m["monsters"].append(loc["monster"])
        for lv in loc["levels"]:
            if lv not in m["levels"]:
                m["levels"].append(lv)
        if loc["dropversion"] and loc["dropversion"] not in m["dropversions"]:
            m["dropversions"].append(loc["dropversion"])
        if loc["page"] not in m["pages"]:
            m["pages"].append(loc["page"])
        if m["mapID"] is None:
            m["mapID"] = loc["mapID"]
        for s in loc["spawns"]:
            if s not in m["spawns"]:
                m["spawns"].append(s)
        key = loc["monster"] or "?"
        m["spawnsByMonster"].setdefault(key, [])
        for s in loc["spawns"]:
            if s not in m["spawnsByMonster"][key]:
                m["spawnsByMonster"][key].append(s)
        if len(m["displayName"]) < len(loc["displayName"]):
            m["displayName"] = loc["displayName"]
    out = []
    for m in merged.values():
        x, y = centroid(m["spawns"])
        m.update({
            "x": x, "y": y,
            "spawnCount": len(m["spawns"]),
            "coordsMissing": x is None,
            "rank": None,
            "multi": "unknown",
            "cannon": "unknown",
            "wilderness": looks_wilderness(m),
            "wildernessLevelMin": None,
            "wildernessLevelMax": None,
            "konarAssignable": "unknown",
            "requirements": [],
            "notes": None,
            "curated": False,
        })
        m["levels"].sort()
        out.append(m)
    out.sort(key=lambda l: (l["name"].lower(), l["plane"]))
    return out


# --------------------------------------------------------------------------
# Curated overrides
# --------------------------------------------------------------------------

_FLOOR_PAREN = re.compile(
    r"\((?:[^()]*(?:floor|basement|upstairs|downstairs|level \d|ground|top|\d+(?:st|nd|rd|th))[^()]*)\)", re.I)


def normalise_loc_name(name: str, strip_all_parens: bool = False) -> str:
    s = name or ""
    s = _FLOOR_PAREN.sub(" ", s)
    if strip_all_parens:
        s = re.sub(r"\([^()]*\)", " ", s)
    s = s.lower()
    s = re.sub(r"[^a-z0-9]+", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def load_curated(curated_dir: Path) -> tuple[dict[str, dict], list[str]]:
    """Load curated/verified/*.json -> {task display name lower: record}."""
    by_task: dict[str, dict] = {}
    problems: list[str] = []
    if not curated_dir.exists():
        return by_task, problems
    for f in sorted(curated_dir.glob("*.json")):
        try:
            data = json.loads(f.read_text(encoding="utf-8"))
        except (OSError, ValueError) as e:
            problems.append(f"{f.name}: {e}")
            continue
        if isinstance(data, dict):
            data = data.get("tasks", [data])
        if not isinstance(data, list):
            problems.append(f"{f.name}: expected an array of task objects")
            continue
        for rec in data:
            if not isinstance(rec, dict) or not rec.get("task"):
                problems.append(f"{f.name}: record without 'task' field skipped")
                continue
            key = rec["task"].strip().lower()
            if key in by_task:
                problems.append(f"{f.name}: duplicate curated record for '{rec['task']}' (later file wins)")
            rec["_file"] = f.name
            by_task[key] = rec
    return by_task, problems


_CURATED_TASK_FIELDS = ("summary", "recommendedStyle", "requiredItems", "usefulItems",
                        "superior", "alternatives", "bossTask")
_CURATED_LOC_FIELDS = ("rank", "multi", "cannon", "wilderness", "wildernessLevelMin",
                       "wildernessLevelMax", "konarAssignable", "requirements", "notes")


def apply_curated(task: dict, curated: dict | None, report: dict) -> None:
    if not curated:
        return
    task["curatedFile"] = curated.get("_file")
    for f in _CURATED_TASK_FIELDS:
        if f in curated and curated[f] is not None:
            task[f] = curated[f]
    locs = task["locations"]
    for cl in curated.get("locations") or []:
        cname = cl.get("name")
        if not cname:
            continue
        # tier 1: exact (punctuation-insensitive) match on name or displayName;
        # tier 2: floor/basement parentheticals stripped; tier 3: all parentheticals stripped
        exact = re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", cname.lower())).strip()
        matches = [l for l in locs if exact in (
            re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", l["name"].lower())).strip(),
            re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", l["displayName"].lower())).strip())]
        if not matches:
            norm = normalise_loc_name(cname)
            matches = [l for l in locs if normalise_loc_name(l["name"]) == norm
                       or normalise_loc_name(l["displayName"]) == norm]
        if not matches:
            norm2 = normalise_loc_name(cname, strip_all_parens=True)
            matches = [l for l in locs if normalise_loc_name(l["name"], True) == norm2]
        if len(matches) > 1:
            report["ambiguousCuratedLocations"].append(
                {"task": task["task"], "curated": cname, "matches": [m["id"] for m in matches]})
        if matches:
            target = matches[0]
            for f in _CURATED_LOC_FIELDS:
                if f in cl and cl[f] is not None:
                    target[f] = cl[f]
            target["curated"] = True
            target["curatedName"] = cname
        else:
            report["unmatchedCuratedLocations"].append({"task": task["task"], "curated": cname})
            new = {
                "id": location_id(cname, None),
                "name": cname, "displayName": cname, "link": None,
                "annotations": [], "annotationsRaw": [], "plane": None, "mapID": None,
                "members": None, "monsters": [], "levels": [], "dropversions": [], "pages": [],
                "spawns": [], "spawnsByMonster": {}, "x": None, "y": None, "spawnCount": 0,
                "coordsMissing": True, "rank": None, "multi": "unknown", "cannon": "unknown",
                "wilderness": False, "wildernessLevelMin": None, "wildernessLevelMax": None,
                "konarAssignable": "unknown", "requirements": [], "notes": None,
                "curated": True, "curatedName": cname,
            }
            for f in _CURATED_LOC_FIELDS:
                if f in cl and cl[f] is not None:
                    new[f] = cl[f]
            locs.append(new)
    locs.sort(key=lambda l: (l["rank"] if isinstance(l.get("rank"), int) else 10 ** 6,
                             l["name"].lower(), l["plane"] if l["plane"] is not None else -1))


# --------------------------------------------------------------------------
# Task assembly
# --------------------------------------------------------------------------

@dataclass
class TaskRow:
    enum: str
    display: str
    alternatives: list[str] = field(default_factory=list)


def read_task_list(path: Path) -> list[TaskRow]:
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.rstrip("\n").split("\t")
        enum = parts[0].strip()
        display = parts[1].strip() if len(parts) > 1 else enum
        alts = [a.strip() for a in parts[2].split("|")] if len(parts) > 2 and parts[2].strip() else []
        rows.append(TaskRow(enum, display, alts))
    return rows


def build_task(cache: WikiCache, row: TaskRow, report: dict) -> dict:
    print(f"== {row.display}", file=sys.stderr)
    task_page, tried = resolve_task_page(cache, row.display)
    monster_pages, mtried = resolve_monster_pages(cache, row.display, row.alternatives, task_page)

    task: dict = {
        "enum": row.enum,
        "task": row.display,
        "alternatives": list(row.alternatives),
        "wikiTaskPage": task_page.title if task_page else None,
        "wikiTaskPageRequested": task_page.requested if task_page else None,
        "wikiTaskPageRedirected": task_page.redirected if task_page else None,
        "wikiTaskPageKind": None,
        "wikiTaskPageTried": tried,
        "wikiMonsterPages": [p.title for p in monster_pages],
        "wikiMonsterPagesTried": mtried,
        "bossTask": None,  # decided after the pages are parsed (see below)
        "taskId": None,
        "requirements": {"skills": [], "slayer": None, "combat": None, "other": None},
        "masters": {},
        "summary": None,
        "recommendedStyle": None,
        "styleNotes": [],
        "requiredItems": [],
        "usefulItems": [],
        "superior": None,
        "xpPerKill": None,
        "gearTables": [],
        "exampleSetups": [],
        "strategy": [],
        "unlocks": [],
        "monsters": [],
        "locations": [],
        "curatedFile": None,
    }

    if task_page:
        text = task_page.wikitext
        infobox = parse_infobox_slayer(text)
        task["wikiTaskPageKind"] = "slayer-task" if infobox else "monster"
        if infobox:
            task["taskId"] = infobox["taskId"]
            task["requirements"] = infobox["requirements"]
            task["masters"] = infobox["masters"]
        gear_tables, setups = parse_gear_sections(text)
        task["gearTables"] = gear_tables
        task["exampleSetups"] = setups
        task["strategy"], task["summary"] = parse_strategy(text)
        task["unlocks"] = parse_unlocks(text)
        styles = []
        for g in gear_tables:
            if g["style"] and g["style"] not in styles:
                styles.append(g["style"])
        task["recommendedStyle"] = styles[0] if styles else None
        task["styleNotes"] = [f"{g['label'] or 'Default'}: {g['style']}" for g in gear_tables if g["style"]]
    else:
        report["tasksWithoutTaskPage"].append({"task": row.display, "tried": tried})

    raw_locs: list[dict] = []
    for p in monster_pages:
        info = parse_infobox_monster(p.wikitext)
        locs = parse_loclines(p.wikitext, p.title)
        map_locs = [] if locs else parse_map_locations(p.wikitext, p.title)
        raw_locs.extend(locs + map_locs)
        entry = {"page": p.title, "redirected": p.redirected, "hasInfobox": info is not None,
                 "locLines": len(locs), "mapLocations": len(map_locs)}
        if info:
            entry.update(info)
        task["monsters"].append(entry)
    task["locations"] = merge_locations(raw_locs)

    primary = None
    for m in task["monsters"]:
        if m.get("slayerXp") is not None:
            primary = m
            break
    if primary:
        task["xpPerKill"] = primary["slayerXp"]
        if task["requirements"]["slayer"] is None and primary.get("slayerLevel"):
            task["requirements"]["slayer"] = primary["slayerLevel"]

    # Boss task: the "task page" is a monster page (no {{Infobox Slayer}}) whose
    # {{Infobox Monster}} `cat` lists "Bosses" (or an explicit override).
    # Plain monsters without a Slayer task page (e.g. Cows) also land on a
    # monster page but are not bosses.  Curated data can override this.
    boss = False
    if task_page and task["wikiTaskPageKind"] == "monster":
        cats = [m.get("slayerCategory") or "" for m in task["monsters"] if m["page"] == task_page.title]
        if not cats:  # overview page without its own infobox: look at the variant pages
            cats = [m.get("slayerCategory") or "" for m in task["monsters"] if m.get("hasInfobox")]
            boss = bool(cats) and all("bosses" in c.lower() for c in cats)
        else:
            boss = any("bosses" in c.lower() for c in cats)
        boss = boss or row.display in BOSS_TASK_OVERRIDES
    task["bossTask"] = boss

    if not task["gearTables"] and not task["exampleSetups"]:
        report["tasksWithoutEquipment"].append(row.display)
    if not task["locations"]:
        report["tasksWithoutLocations"].append({"task": row.display, "monsterPagesTried": mtried})
    if not task["monsters"]:
        report["tasksWithoutMonsterPage"].append({"task": row.display, "tried": mtried})
    return task


def collect_item_names(tasks: list[dict]) -> list[str]:
    names: set[str] = set()
    for t in tasks:
        for g in t["gearTables"]:
            for tiers in g["slots"].values():
                for tier in tiers:
                    for item in tier:
                        names.add(item["name"])
                        if item.get("pic"):
                            names.add(item["pic"])
        for s in t["exampleSetups"]:
            names.update(v for v in s["equipment"].values() if v)
            names.update(s["inventory"])
            names.update(s["runePouch"])
    return sorted(n for n in names if n)


def build_locations_index(tasks: list[dict]) -> list[dict]:
    index: dict[str, dict] = {}
    for t in tasks:
        for loc in t["locations"]:
            entry = index.setdefault(loc["id"], {
                "id": loc["id"], "name": loc["name"], "displayName": loc["displayName"],
                "link": loc["link"], "annotations": loc["annotations"], "plane": loc["plane"],
                "mapID": loc["mapID"], "x": None, "y": None, "spawns": [], "spawnsByTask": {},
                "tasks": [], "wilderness": loc["wilderness"], "coordsMissing": True,
            })
            if t["task"] not in entry["tasks"]:
                entry["tasks"].append(t["task"])
            entry["spawnsByTask"][t["task"]] = loc["spawns"]
            for s in loc["spawns"]:
                if s not in entry["spawns"]:
                    entry["spawns"].append(s)
            if entry["mapID"] is None:
                entry["mapID"] = loc["mapID"]
            entry["wilderness"] = entry["wilderness"] or loc["wilderness"]
    out = []
    for e in index.values():
        e["x"], e["y"] = centroid(e["spawns"])
        e["coordsMissing"] = e["x"] is None
        e["spawnCount"] = len(e["spawns"])
        out.append(e)
    out.sort(key=lambda e: e["id"])
    return out


# --------------------------------------------------------------------------
# Post-processing: general Slayer gear, task summary, derived styles
# --------------------------------------------------------------------------

GENERAL_GEAR_PAGE = "Slayer training"


def _section_body(text: str, title_re: str, level: int = 2) -> str | None:
    for s in sections(text):
        if s["level"] == level and re.fullmatch(title_re, s["title"], re.I):
            return text[s["start"]:s["end"]]
    return None


def build_general_gear(cache: WikiCache) -> dict | None:
    """General (non task-specific) Slayer gear from the ==Equipment== section of
    the "Slayer training" page: one GearTable + ExampleSetup per <tabber> style."""
    page = cache.get(GENERAL_GEAR_PAGE)
    if not page.ok:
        return None
    body = _section_body(page.wikitext, r"equipment")
    if body is None:
        return None
    gear_tables, setups = parse_gear_sections(body)
    prose = re.sub(r"<tabber>.*?</tabber>", "", body, flags=re.S | re.I)
    notes = [p for p in paragraphs(plain_text(prose)) if not p.endswith(":")][:8]
    return {
        "source": page.title or GENERAL_GEAR_PAGE,
        "gearTables": gear_tables,
        "exampleSetups": setups,
        "notes": notes,
    }


_CELL_ATTR = re.compile(r'^\s*(?:[\w\-]+\s*=\s*(?:"[^"]*"|\'[^\']*\'|[^\s|]+)\s*)+\|(?!\|)')


def _cell_text(cell: str, line_sep: str = " ") -> str:
    cell = _CELL_ATTR.sub("", cell)
    txt = plain_text(cell)
    lines = [ln.strip() for ln in txt.split("\n") if ln.strip()]
    return line_sep.join(lines)


def parse_task_summary(text: str) -> list[dict]:
    """Rows of the ==Task summary== wikitable (Assignment, Slayer level, Weight,
    Pros, Cons, Recommendation, Approx. XP/h)."""
    body = _section_body(text, r"task summary")
    if body is None:
        return []
    m = re.search(r"\{\|.*?\n\|\}", body, re.S)
    if not m:
        return []
    rows: list[dict] = []
    for cells in _table_rows(m.group(0)):
        if len(cells) < 7:
            continue
        assignment = _cell_text(cells[0])
        if not assignment:
            continue
        rows.append({
            "assignment": assignment,
            "slayerLevel": to_int(_cell_text(cells[1])),
            "weight": to_int(_cell_text(cells[2])),
            "pros": _cell_text(cells[3]),
            "cons": _cell_text(cells[4]),
            "recommendation": _cell_text(cells[5]),
            "xpPerHour": _cell_text(cells[6], "; "),
        })
    return rows


def _norm_name(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


def _plural_variants(name: str) -> list[str]:
    low = name.lower()
    out = [name + "s", name + "es"]
    if low.endswith("y"):
        out.append(name[:-1] + "ies")
    if low.endswith("f"):
        out.append(name[:-1] + "ves")
    if low.endswith("fe"):
        out.append(name[:-2] + "ves")
    if low.endswith("man"):
        out.append(name[:-3] + "men")
    return out


def _name_keys(name: str | None) -> set[str]:
    """Normalised forms of a name: as is, without 'The', singular and plural."""
    keys: set[str] = set()
    if not name:
        return keys
    forms = {name, strip_the(name)}
    for base in list(forms):
        forms.update(singular_variants(base))
    for base in list(forms):
        forms.update(_plural_variants(base))
    for f in forms:
        k = _norm_name(f)
        if k:
            keys.add(k)
    return keys


def _task_key_tiers(task: dict) -> list[set[str]]:
    """Matching keys in priority order: display name, alternatives, monster names."""
    tier0 = _name_keys(task["task"])
    tier1: set[str] = set()
    for alt in task.get("alternatives", []):
        tier1 |= _name_keys(alt)
    tier2: set[str] = set()
    for m in task.get("monsters", []):
        tier2 |= _name_keys(m.get("page"))
        tier2 |= _name_keys(m.get("name"))
        for v in m.get("versions") or []:
            tier2 |= _name_keys(v)
    return [tier0, tier1 - tier0, tier2 - tier0 - tier1]


def apply_training_summary(tasks: list[dict], cache: WikiCache, report: dict | None = None) -> list[dict]:
    """Attach the "Slayer training" task-summary row to the matching task as
    task["trainingSummary"]; returns the rows that matched no task (also written
    to report["trainingSummaryUnmatched"] when a report is given)."""
    for t in tasks:
        t.pop("trainingSummary", None)
    page = cache.get(GENERAL_GEAR_PAGE)
    rows = parse_task_summary(page.wikitext) if page.ok else []
    tiers = [_task_key_tiers(t) for t in tasks]
    unmatched: list[dict] = []
    ambiguous: list[dict] = []
    for row in rows:
        keys = _name_keys(row["assignment"])
        hit: dict | None = None
        for level in range(3):
            cands = [t for t, tk in zip(tasks, tiers) if tk[level] & keys]
            if cands:
                if len(cands) > 1:
                    ambiguous.append({"assignment": row["assignment"], "tasks": [c["task"] for c in cands]})
                hit = cands[0]
                break
        if hit is None:
            unmatched.append(dict(row, reason="no task"))
        elif "trainingSummary" in hit:
            unmatched.append(dict(row, reason=f"task {hit['task']!r} already has a row"))
        else:
            hit["trainingSummary"] = {
                "assignment": row["assignment"],
                "pros": row["pros"],
                "cons": row["cons"],
                "recommendation": row["recommendation"],
                "xpPerHour": row["xpPerHour"],
            }
    if report is not None:
        report["trainingSummaryRows"] = len(rows)
        report["trainingSummaryUnmatched"] = unmatched
        report["trainingSummaryAmbiguous"] = ambiguous
    return unmatched


_STYLE_WORDS = (
    (r"\bmagic\b|\bbarrag(e|ing)\b|\bburst(s|ing)?\b", "Magic"),
    (r"\branged?\b|\bchin(s|ning|chompas?)?\b", "Ranged"),
    (r"\bmelee\b", "Melee"),
)

_WEAKNESS_WORDS = (
    (r"\b(crush|slash|stab)\b", "Melee"),
    (r"\b(fire|water|earth|air|magic)\b", "Magic"),
    (r"\b(ranged?|arrows?|bolts?)\b", "Ranged"),
)


def _style_from_text(text: str | None, rules=_STYLE_WORDS) -> str | None:
    """First style named in `text`, in order of appearance."""
    if not text:
        return None
    best: tuple[int, str] | None = None
    for pattern, style in rules:
        m = re.search(pattern, text, re.I)
        if m and (best is None or m.start() < best[0]):
            best = (m.start(), style)
    return best[1] if best else None


def _cached_monster_page(cache: WikiCache, task: dict, final_title: str) -> Page | None:
    """The cached page whose final title is `final_title`, never fetching."""
    for requested in [final_title] + list(task.get("wikiMonsterPagesTried", [])):
        _, meta_path = cache._paths(requested)
        if not meta_path.exists():
            continue
        p = cache.get(requested)
        if p.ok and p.title == final_title:
            return p
    return None


def _monster_weakness(cache: WikiCache, task: dict) -> str | None:
    """Text of the infobox `weakness` / `elementalweaknesstype` field(s) of the
    task's monsters (the wiki replaced `weakness` with `elementalweaknesstype`)."""
    parts: list[str] = []
    for m in task.get("monsters", []):
        if not m.get("hasInfobox"):
            continue
        page = _cached_monster_page(cache, task, m["page"])
        if page is None:
            continue
        tpls = find_templates(page.wikitext, ("infobox monster",))
        if not tpls:
            continue
        _, named = tpls[0].args
        for key, value in named.items():
            if re.fullmatch(r"weakness\d*", key):
                txt = plain_text(value)
                if txt:
                    parts.append(txt)
            elif re.fullmatch(r"elementalweaknesstype(\d*)", key):
                # A mild elemental weakness (most monsters have 15-20%) is not a reason to
                # recommend Magic; only a strong one (>= 50%) counts.
                suffix = key[len("elementalweaknesstype"):]
                pct_txt = plain_text(named.get("elementalweaknesspercent" + suffix, "") or "")
                m = re.search(r"\d+", pct_txt)
                pct = int(m.group(0)) if m else 0
                txt = plain_text(value)
                # Disabled: an elemental weakness says which spell hits hardest, not that Magic is
                # the best way to do the task (spectres, dragons and kraken are weak to elements but
                # are not normally maged). Kept for reference; raise the bar to re-enable.
                if txt and pct >= 1000:
                    parts.append(txt)
    return "; ".join(parts) or None


def derive_recommended_styles(tasks: list[dict], cache: WikiCache) -> dict[str, int]:
    """Fill recommendedStyle where it is null, from data already in the record,
    and record where it came from in recommendedStyleSource."""
    counts: dict[str, int] = {}
    for t in tasks:
        if t.get("recommendedStyleSource") is not None:
            # derived on a previous run (task reused through --only): redo it
            t["recommendedStyle"] = None
        t.pop("recommendedStyleSource", None)
        source = None
        if t["recommendedStyle"]:
            source = "curated" if t.get("curatedFile") else "gearTables"
        else:
            style = next((g["style"] for g in t["gearTables"] if g.get("style")), None)
            if style:
                t["recommendedStyle"], source = style, "gearTables"
            else:
                ts = t.get("trainingSummary") or {}
                style = _style_from_text(ts.get("recommendation")) or _style_from_text(ts.get("xpPerHour"))
                if style:
                    t["recommendedStyle"], source = style, "trainingSummary"
                else:
                    style = _style_from_text(_monster_weakness(cache, t), _WEAKNESS_WORDS)
                    if style:
                        t["recommendedStyle"], source = style, "monsterWeakness"
        if source:
            t["recommendedStyleSource"] = source
            counts[source] = counts.get(source, 0) + 1
    return counts


def write_json(path: Path, data) -> None:
    path.write_text(json.dumps(data, indent=2, sort_keys=True, ensure_ascii=False) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    here = Path(__file__).resolve().parent
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--out", type=Path, default=here / "out")
    ap.add_argument("--cache", type=Path, default=here / "cache")
    ap.add_argument("--curated", type=Path, default=here / "curated" / "verified")
    ap.add_argument("--tasks", type=Path, default=here / "task-enum.txt")
    ap.add_argument("--refresh", action="store_true", help="ignore the cache and re-fetch every page")
    ap.add_argument("--only", help="comma-separated display names (or substrings) to (re)build; "
                                  "other tasks are loaded from the existing out/tasks.json")
    ap.add_argument("--limit", type=int, default=0, help="stop after N tasks (testing)")
    ap.add_argument("--sleep", type=float, default=FETCH_SLEEP)
    args = ap.parse_args(argv)

    cache = WikiCache(args.cache, refresh=args.refresh, sleep=args.sleep)
    rows = read_task_list(args.tasks)
    if args.limit:
        rows = rows[:args.limit]

    existing: dict[str, dict] = {}
    only: list[str] | None = None
    if args.only:
        only = [s.strip().lower() for s in args.only.split(",") if s.strip()]
        prev = args.out / "tasks.json"
        if prev.exists():
            for t in json.loads(prev.read_text(encoding="utf-8")):
                existing[t["task"].lower()] = t

    curated, curated_problems = load_curated(args.curated)
    report: dict = {
        "generated": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "taskCount": len(rows),
        "fetches": 0,
        "failedPages": {},
        "tasksWithoutTaskPage": [],
        "tasksWithoutMonsterPage": [],
        "tasksWithoutEquipment": [],
        "tasksWithoutLocations": [],
        "tasksWithoutMasters": [],
        "unmatchedCuratedLocations": [],
        "ambiguousCuratedLocations": [],
        "curatedProblems": curated_problems,
        "curatedTasksUnused": [],
        "counts": {},
    }

    tasks: list[dict] = []
    used_curated: set[str] = set()
    for row in rows:
        key = row.display.lower()
        if only is not None and not any(o == key or o in key for o in only) and key in existing:
            t = existing[key]
        else:
            t = build_task(cache, row, report)
            apply_curated(t, curated.get(key), report)
        if key in curated:
            used_curated.add(key)
        if not t["masters"]:
            report["tasksWithoutMasters"].append(row.display)
        tasks.append(t)

    report["curatedTasksUnused"] = sorted(k for k in curated if k not in used_curated)

    # post-processing from the "Slayer training" page (cached like any other page)
    general_gear = build_general_gear(cache)
    apply_training_summary(tasks, cache, report)
    style_sources = derive_recommended_styles(tasks, cache)
    report["failedPages"] = dict(cache.failed)
    report["fetches"] = cache.fetches
    report["counts"] = {
        "tasks": len(tasks),
        "withTaskPage": sum(1 for t in tasks if t["wikiTaskPage"]),
        "withSlayerInfobox": sum(1 for t in tasks if t["wikiTaskPageKind"] == "slayer-task"),
        "withGearTables": sum(1 for t in tasks if t["gearTables"]),
        "withAnyEquipment": sum(1 for t in tasks if t["gearTables"] or t["exampleSetups"]),
        "withLocations": sum(1 for t in tasks if t["locations"]),
        "withMasters": sum(1 for t in tasks if t["masters"]),
        "withStrategy": sum(1 for t in tasks if t["strategy"]),
        "withUnlocks": sum(1 for t in tasks if t["unlocks"]),
        "withXp": sum(1 for t in tasks if t["xpPerKill"] is not None),
        "curatedTasks": len(used_curated),
        "withTrainingSummary": sum(1 for t in tasks if t.get("trainingSummary")),
        "withRecommendedStyle": sum(1 for t in tasks if t["recommendedStyle"]),
        "recommendedStyleBySource": style_sources,
        "generalGearTables": len(general_gear["gearTables"]) if general_gear else 0,
    }

    args.out.mkdir(parents=True, exist_ok=True)
    write_json(args.out / "tasks.json", tasks)
    write_json(args.out / "locations.json", build_locations_index(tasks))
    write_json(args.out / "report.json", report)
    if general_gear is not None:
        write_json(args.out / "general-gear.json", general_gear)
    (args.out / "item-names.txt").write_text("\n".join(collect_item_names(tasks)) + "\n", encoding="utf-8")

    print(json.dumps(report["counts"], indent=2))
    print(f"fetches this run: {cache.fetches}; failed pages: {len(cache.failed)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
