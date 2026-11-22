#!/usr/bin/env python3
"""Build a larger EN<->HE offline word list from the public word2word lexicon.

The word2word project derives bilingual associations from OpenSubtitles2018.
The resulting associations are automatically mined and can be noisy; they are
not a professionally edited dictionary. Keep the original hand-curated rows
first, then append up to 30,000 distinct additional pairs.
"""
from pathlib import Path
import re
import sys

from word2word import Word2word

ROOT = Path(__file__).resolve().parents[1]
DICT = ROOT / "app/src/main/assets/dictionary.tsv"
TARGET_ADDITIONS = 30_000
EN_RE = re.compile(r"^[A-Za-z][A-Za-z'-]{0,39}$")
HE_RE = re.compile(r"^[א-ת][א-ת \-׳״']{0,49}$")


def clean_pair(en, he):
    en = " ".join(str(en).strip().split())
    he = " ".join(str(he).strip().split())
    if not EN_RE.fullmatch(en) or not HE_RE.fullmatch(he):
        return None
    return en, he


def main():
    original = DICT.read_text(encoding="utf-8").splitlines()
    rows = []
    seen = set()
    for line in original:
        parts = line.split("\t")
        if len(parts) >= 2:
            pair = clean_pair(parts[0], parts[1])
            if pair:
                key = (pair[0].casefold(), pair[1])
                if key not in seen:
                    seen.add(key)
                    rows.append(pair)

    added = 0
    # Both directions help cover vocabulary that is common in Hebrew but
    # appears less often as an English source token.
    for source, target in (("en", "he"), ("he", "en")):
        model = Word2word(source, target)
        for word in model.word2x.keys():
            try:
                candidates = model(word, n_best=5)
            except (KeyError, TypeError):
                continue
            for candidate in candidates:
                pair = clean_pair(word, candidate) if source == "en" else clean_pair(candidate, word)
                if not pair:
                    continue
                key = (pair[0].casefold(), pair[1])
                if key in seen:
                    continue
                seen.add(key)
                rows.append(pair)
                added += 1
                if added >= TARGET_ADDITIONS:
                    break
            if added >= TARGET_ADDITIONS:
                break
        if added >= TARGET_ADDITIONS:
            break

    if added < TARGET_ADDITIONS:
        raise SystemExit(
            f"Only found {added:,} additional valid pairs; "
            f"need {TARGET_ADDITIONS:,}. APK build stopped rather than claiming 30,000."
        )

    DICT.write_text(
        "".join(f"{en}\t{he}\n" for en, he in rows),
        encoding="utf-8",
    )
    print(f"Dictionary ready: {len(rows):,} total pairs; {added:,} new pairs.")
    print("Source: word2word bilingual lexicon mined from OpenSubtitles2018.")
    print("Quality note: automatically mined translations can be context-dependent.")


if __name__ == "__main__":
    main()
