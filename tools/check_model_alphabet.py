#!/usr/bin/env python3
"""Checks ModelAlphabet (Kotlin) against the real Hafs text: folding every word of qpc-hafs.db must produce only
letters of the model vocabulary (tokens.txt). Mirrors ModelAlphabet.kt / the model author's retrieve.py exactly.
Exit 1 on failure."""
import sqlite3, sys, os, collections
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
LETTERS = set("ءابتثجحخدذرزسشصضطظعغفقكلمنهوي")
FOLD = {"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ٲ": "ا", "ٳ": "ا", "ى": "ي", "ئ": "ي", "ے": "ي", "ی": "ي",
        "ؤ": "و", "ة": "ه", "ک": "ك"}

def fold(w): return "".join(c for c in (FOLD.get(ch, ch) for ch in w) if c in LETTERS)

vocab = [l.rsplit(" ", 1)[0] for l in open(os.path.join(ROOT, "app/src/main/assets/asr/quran-asr-small/tokens.txt"), encoding="utf-8").read().splitlines() if l.strip()]
assert set(vocab[1:-1]) == LETTERS and len(vocab) == 31, "LETTERS != tokens.txt vocabulary"

db = sqlite3.connect(os.path.join(ROOT, "app/src/main/assets/qpc-hafs.db"))
dropped = collections.Counter(); used = collections.Counter(); words = 0; empty = []; single = []
for vid, key, text in db.execute("select id, verse_key, text from verses order by id"):
    for tok in text.replace("\u00a0", " ").split(" "):
        if not tok or tok == "۞": continue
        f = fold(tok)
        for ch in tok:
            c = FOLD.get(ch, ch)
            if c not in LETTERS: dropped[ch] += 1
        for ch in f: used[ch] += 1
        words += 1
        if not f: empty.append((key, tok))
        elif f in ("ا", "ي"): single.append((key, tok, f))
print(f"words (incl. ayah-number markers): {words}")
print(f"model letters used: {len(used)}/29; unused: {sorted(LETTERS - set(used)) or 'none'}")
print(f"words folding to EMPTY (ayah-number markers etc.): {len(empty)}  e.g. {empty[:2]}")
non_marker_empty = [e for e in empty if not all('\u0660' <= c <= '\u0669' for c in e[1])]
print(f"  of which NOT pure digits: {len(non_marker_empty)} {non_marker_empty[:5]}")
print(f"words folding to a lone ا/ي: {len(single)} {single[:6]}")
print("characters dropped by folding (code point: count):")
for ch, n in sorted(dropped.items(), key=lambda x: -x[1])[:16]:
    print(f"  U+{ord(ch):04X} {n}")
ok = bool(used) and set(used) <= LETTERS and not non_marker_empty
print("PASS" if ok else "FAIL")
sys.exit(0 if ok else 1)
