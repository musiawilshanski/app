#!/usr/bin/env python3
"""Validate the hand-curated offline English-Hebrew dictionary.

Translations are no longer mined from subtitles: that method produced
context-dependent and misleading word pairs. Each row in dictionary.tsv is
kept as supplied and can include multiple meanings separated by /.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
DICT = ROOT / "app/src/main/assets/dictionary.tsv"
EN_RE = re.compile(r"^[A-Za-z][A-Za-z '\-?]{0,59}$")
HE_RE = re.compile(r"^[א-ת][א-ת ׳״ '\-/]{0,79}$")

def main():
    rows = []
    seen = set()
    for number, line in enumerate(DICT.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) != 2:
            raise SystemExit(f"Invalid dictionary row {number}: expected exactly 2 columns")
        en, he = (part.strip() for part in parts)
        if not EN_RE.fullmatch(en) or not HE_RE.fullmatch(he):
            raise SystemExit(f"Invalid characters in dictionary row {number}: {line}")
        key = (en.casefold(), he)
        if key not in seen:
            seen.add(key)
            rows.append((en, he))
    DICT.write_text("".join(f"{en}\t{he}\n" for en, he in rows), encoding="utf-8")
    print(f"Validated {len(rows):,} curated English-Hebrew entries.")
    print("No subtitle-mined or automatically guessed translations were added.")

if __name__ == "__main__":
    main()
