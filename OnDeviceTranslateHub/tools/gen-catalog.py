#!/usr/bin/env python3
"""
Cross-check catalog.json language lists against Hugging Face model cards.

Sources, in priority order:
  1. https://hf-mirror.com/api/models/<repo>  -> cardData.language
  2. model card README language table (manual review)
  3. technical report appendix (manual review)

The script is read-only by default: it prints a diff report so a human can
decide. Pass --write to apply the cardData result (only adds codes that exist
in LanguageCatalog, never removes anything).

Usage:
    python tools/gen-catalog.py
    python tools/gen-catalog.py --write
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, "app", "src", "main", "assets", "catalog.json")
LANG_CATALOG_KT = os.path.join(ROOT, "api", "src", "main", "java",
                               "org", "caojun", "ai", "translate", "provider",
                               "LanguageCatalog.kt")

HF = "https://hf-mirror.com/api/models/"

# The HF language tag is often a 2-letter code; map the ones we know differ.
ALIASES = {
    "zh": "zh-Hans",
    "zho": "zh-Hans",
    "zh-hans": "zh-Hans",
    "zh-hant": "zh-Hant",
    "tl": "fil",
    "fil": "fil",
    "pes": "fa",
    "fas": "fa",
    "heb": "he",
    "mya": "my",
    "uig": "ug",
    "bod": "bo",
    "mon": "mn",
    "kaz": "kk",
}
ALIASES.update({k: k for k in []})  # placeholder to keep dict literal readable


def known_codes() -> set[str]:
    with open(LANG_CATALOG_KT, encoding="utf-8") as f:
        text = f.read()
    return set(re.findall(r'Language\("([^"]+)"', text))


def normalize(code: str, known: set[str]) -> str | None:
    c = code.strip()
    if c in known:
        return c
    low = c.lower()
    if low in ALIASES:
        mapped = ALIASES[low]
        return mapped if mapped in known else None
    base = low.split("-")[0]
    if base in ALIASES:
        mapped = ALIASES[base]
        return mapped if mapped in known else None
    for k in known:
        if k.lower() == low:
            return k
    return None


def repo_of(model: dict) -> str | None:
    sources = model.get("langsSource", "")
    m = re.search(r"/api/models/([^/]+/[^/\s]+)", sources)
    if m:
        return m.group(1)
    for f in model.get("files", []):
        for url in f.get("urls", []):
            m = re.search(r"hf-mirror\.com/([^/]+/[^/]+)/resolve", url)
            if m:
                return m.group(1)
    return None


def fetch_card_languages(repo: str) -> list[str]:
    with urllib.request.urlopen(HF + repo, timeout=30) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    langs = (data.get("cardData") or {}).get("language") or []
    out: list[str] = []
    for item in langs:
        if isinstance(item, str):
            out.append(item)
        elif isinstance(item, dict):
            for key in ("code", "name"):
                if key in item:
                    out.append(str(item[key]))
                    break
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="apply cardData languages")
    args = parser.parse_args()

    known = known_codes()
    with open(CATALOG, encoding="utf-8") as f:
        catalog = json.load(f)

    changed = False
    for model in catalog.get("models", []):
        repo = repo_of(model)
        if not repo:
            print(f"[skip] {model['id']}: no repo in langsSource/urls")
            continue
        try:
            raw = fetch_card_languages(repo)
        except Exception as exc:  # noqa: BLE001
            print(f"[warn] {model['id']}: cannot fetch {repo}: {exc}")
            continue

        normalized = {normalize(c, known) for c in raw}
        normalized.discard(None)
        current = set(model.get("langs", []))

        missing = sorted(normalized - current)
        unknown = sorted(c for c in raw if normalize(c, known) is None)

        print(f"\n{model['id']}  ({repo})")
        print(f"  catalog: {len(current)} langs   cardData: {len(normalized)} langs")
        if missing:
            print(f"  + in cardData only: {', '.join(missing)}")
        if unknown:
            print(f"  ? unmapped: {', '.join(unknown)}")
        if current - normalized:
            print(f"  - catalog only (verify in README/report): "
                  f"{', '.join(sorted(current - normalized))}")

        if args.write and missing:
            model["langs"] = sorted(current | normalized)
            changed = True

    if args.write and changed:
        with open(CATALOG, "w", encoding="utf-8") as f:
            json.dump(catalog, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("\n[write] catalog.json updated; review the diff and run unit tests")
    elif args.write:
        print("\n[write] nothing to update")
    return 0


if __name__ == "__main__":
    sys.exit(main())
