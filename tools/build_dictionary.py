#!/usr/bin/env python3
"""Validate the curated offline English-Hebrew dictionary.

Subtitle-mined translation guesses are deliberately disabled because they
created misleading pairs. Each entry is reviewed vocabulary and may include
several senses separated by /.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DICT = ROOT / "app/src/main/assets/dictionary.tsv"
EN_ALLOWED = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz '-?.,!()")
HE_ALLOWED = set("אבגדהוזחטיכלמנסעפצקרשתךםןףץ׳״ '-/.,!?()…")

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
        if not en or not he or not set(en) <= EN_ALLOWED or not set(he) <= HE_ALLOWED:
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
