#!/usr/bin/env python3
"""Exports a few real pages (words exactly as the app builds them) as a TSV test fixture for the JVM matcher tests.

    python3 tools/export_test_pages.py            # writes app/src/test/resources/test_pages.tsv
    python3 tools/export_test_pages.py --check    # fails if the committed fixture differs from the databases

The text is copied verbatim from qpc-hafs.db (same tokenisation/word ids as data/WordMapper.kt). The fixture exists only so
the matcher tests run on the plain JVM with real canonical words; the app never reads it.
"""
import os, sqlite3, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_data as q

PAGES = [1, 2, 3, 50, 187, 262, 531, 532, 586, 604]
OUT = os.path.join(q.ROOT, "app", "src", "test", "resources", "test_pages.tsv")
LINES_DB = os.path.join(q.ASSETS, "qpc-v1-15-lines.db")

def build():
    words, _, total = q.build_words(q.load_verses(), q.load_adjustments())
    by_id = {}
    for (s, a), ws in words.items():
        for w in ws:
            by_id[w["id"]] = (s, a, w)
    c = sqlite3.connect(f"file:{LINES_DB}?mode=ro", uri=True)
    rows = ["# page\tline\tkind\t(H: surah | B | A: centered, wordId, surah, ayah, indexInAyah, marker, text)"]
    for p in PAGES:
        for line, typ, cen, first, last, surah in c.execute(
            "select line_number,line_type,is_centered,first_word_id,last_word_id,surah_number from pages "
            "where page_number=? order by line_number", (p,)):
            if typ == "surah_name":
                rows.append(f"{p}\t{line}\tH\t{surah}")
            elif typ == "basmallah":
                rows.append(f"{p}\t{line}\tB")
            else:
                for wid in range(int(first), int(last) + 1):
                    if wid not in by_id:   # id swallowed by a SPLIT word (covered by idEnd)
                        continue
                    s, a, w = by_id[wid]
                    rows.append(f"{p}\t{line}\tA\t{int(cen)}\t{wid}\t{s}\t{a}\t{w['index']}\t{int(w['marker'])}\t{w['text']}")
    return "\n".join(rows) + "\n"

if __name__ == "__main__":
    text = build()
    if "--check" in sys.argv:
        ok = os.path.exists(OUT) and open(OUT, encoding="utf-8").read() == text
        print("fixture matches databases" if ok else "FIXTURE OUT OF DATE")
        sys.exit(0 if ok else 1)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf-8").write(text)
    print("wrote", OUT, len(text), "bytes")
