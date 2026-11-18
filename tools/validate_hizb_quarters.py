#!/usr/bin/env python3
"""Validate the hizb data inside app/src/main/assets/qpc-hafs.db (tables `hizb_quarters` and the hizb columns of `verses`).

  python3 tools/validate_hizb_quarters.py

Checks: 240 quarters, strictly increasing, first = 1:1, every juz start is quarter 1, 9, 17 ... 233, every ۞ sign in the
canonical text is a quarter start, quarters without a sign are all first ayahs of a surah (the text has no sign there),
and every derived column (juz, hizb, hizb_quarter, ranges, ayah_count, verses.*) agrees with the 240 starts.
Exit code 0 = valid. Stdlib only. Mirrors HizbQuarters.problem() and QuranDataIntegrityTest.
"""
import sqlite3, sys
from pathlib import Path

DB = Path(__file__).resolve().parent.parent / "app/src/main/assets/qpc-hafs.db"
JUZ = [(1,1),(2,142),(2,253),(3,93),(4,24),(4,148),(5,82),(6,111),(7,88),(8,41),(9,93),(11,6),(12,53),(15,1),(17,1),
       (18,75),(21,1),(23,1),(25,21),(27,56),(29,46),(33,31),(36,28),(39,32),(41,47),(46,1),(51,31),(58,1),(67,1),(78,1)]

def main():
    db = sqlite3.connect(DB)
    verses = db.execute("SELECT id, surah, ayah, text, juz, hizb, rub_el_hizb, hizb_quarter, is_quarter_start FROM verses ORDER BY id").fetchall()
    rows = db.execute("SELECT rub_el_hizb, juz, hizb, hizb_quarter, start_ayah_id, start_verse_key, end_ayah_id, end_verse_key, ayah_count FROM hizb_quarters ORDER BY rub_el_hizb").fetchall()
    errs = []
    starts = [tuple(map(int, r[5].split(":"))) for r in rows]
    key_of = {v[0]: f"{v[1]}:{v[2]}" for v in verses}; id_of = {(v[1], v[2]): v[0] for v in verses}
    if len(rows) != 240: errs.append(f"{len(rows)} quarters, expected 240")
    if starts[:1] != [(1, 1)]: errs.append("first quarter is not 1:1")
    if any(starts[i] <= starts[i-1] for i in range(1, len(starts))): errs.append("quarters not strictly increasing")
    if len(rows) == 240:
        for j, js in enumerate(JUZ):
            if starts[8*j] != js: errs.append(f"juz {j+1}: quarter {8*j+1} is {starts[8*j]}, expected {js}")
    marks = {(v[1], v[2]) for v in verses if "۞" in v[3]}
    if marks - set(starts): errs.append(f"signs not at a quarter start: {sorted(marks - set(starts))[:5]}")
    odd = [s for s in set(starts) - marks if s[1] != 1]
    if odd: errs.append(f"quarter starts without a sign that are not first ayahs: {sorted(odd)}")
    n = len(verses)
    for k, r in enumerate(rows):
        sid = id_of[starts[k]]; end = (id_of[starts[k+1]] - 1) if k + 1 < len(starts) else n
        h = k // 4 + 1
        exp = (k + 1, (h + 1) // 2, h, k % 4 + 1, sid, f"{starts[k][0]}:{starts[k][1]}", end, key_of[end], end - sid + 1)
        if r != exp: errs.append(f"hizb_quarters row {k+1} is {r}, expected {exp}"); break
    q_of = {}
    for k in range(len(starts)):
        end = (id_of[starts[k+1]] - 1) if k + 1 < len(starts) else n
        for vid in range(id_of[starts[k]], end + 1): q_of[vid] = k
    for v in verses:
        k = q_of[v[0]]; h = k // 4 + 1
        if (v[4], v[5], v[6], v[7], v[8]) != ((h + 1) // 2, h, k + 1, k % 4 + 1, 1 if (v[1], v[2]) == starts[k] else 0):
            errs.append(f"verse {v[1]}:{v[2]} hizb columns disagree with the quarter list"); break
    if errs:
        print("INVALID:"); [print("  -", e) for e in errs]; return 1
    print(f"OK: 240 quarters; 30 juz starts; {len(marks)} text signs; {len(set(starts)-marks)} unsigned starts are all surah openings.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
