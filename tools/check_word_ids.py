#!/usr/bin/env python3
"""Compare global word ids in qpc-v1-15-lines.db with a naive tokenisation of qpc-hafs.db.

Tokenisation: whitespace split of verses.text, standalone '۞' dropped, ayah-number marker kept as a word.
Prints each surah where the offset between the two numberings changes.
"""
import sqlite3, sys
A = "app/src/main/assets/"
v = sqlite3.connect(A + "qpc-hafs.db"); c = sqlite3.connect(A + "qpc-v1-15-lines.db")
starts, idx = {}, 0
for _id, key, s, a, t in v.execute("select * from verses order by id"):
    if a == 1: starts[s] = idx + 1
    idx += len([w for w in t.split() if w != "۞"])
cur, first = None, {}
for t, f in c.execute("select line_type, first_word_id from pages order by page_number, line_number"):
    pass
cur = None
for t, s, f in c.execute("select line_type, surah_number, first_word_id from pages order by page_number, line_number"):
    if t == "surah_name": cur = s
    elif t == "ayah" and cur: first[cur] = f; cur = None
prev = 0
for s in range(1, 115):
    d = first[s] - starts[s]
    if d != prev: print(f"surah {s}: offset {prev} -> {d}"); prev = d
print("tokens:", idx, "line-db max word id:", c.execute("select max(last_word_id) from pages where last_word_id!=''").fetchone()[0])
