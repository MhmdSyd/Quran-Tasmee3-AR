#!/usr/bin/env python3
"""Builds app/src/main/assets/quran-index.db (derived, small) from the two source DBs + tools/word_adjustments.json.
Run from anywhere:  python3 tools/build_quran_index.py   (then python3 tools/validate_quran_data.py)"""
import os, sqlite3, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_data as q

verses = q.load_verses()
adjs = q.load_adjustments()
words, vidx, total = q.build_words(verses, adjs)
src = sqlite3.connect(f"file:{q.LINES}?mode=ro", uri=True)
lines = list(src.execute("select page_number,line_number,line_type,is_centered,first_word_id,last_word_id,surah_number from pages order by page_number,line_number"))
def n(x): return None if x in ("", None) else int(x)

if os.path.exists(q.INDEX): os.remove(q.INDEX)
db = sqlite3.connect(q.INDEX)
db.executescript("""
PRAGMA journal_mode=OFF;
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE surahs(surah INTEGER PRIMARY KEY, name_ar TEXT NOT NULL, ayah_count INTEGER NOT NULL, start_page INTEGER NOT NULL,
  first_word_id INTEGER NOT NULL, last_word_id INTEGER NOT NULL);
CREATE TABLE verse_index(verse_id INTEGER PRIMARY KEY, surah INTEGER NOT NULL, ayah INTEGER NOT NULL, hafs_rowid INTEGER NOT NULL,
  first_word_id INTEGER NOT NULL, word_count INTEGER NOT NULL);
CREATE UNIQUE INDEX idx_verse_key ON verse_index(surah, ayah);
CREATE UNIQUE INDEX idx_verse_firstword ON verse_index(first_word_id);
CREATE TABLE lines(page INTEGER NOT NULL, line INTEGER NOT NULL, type TEXT NOT NULL, centered INTEGER NOT NULL,
  first_word_id INTEGER, last_word_id INTEGER, surah INTEGER, PRIMARY KEY(page,line)) WITHOUT ROWID;
CREATE INDEX idx_lines_firstword ON lines(first_word_id);
CREATE TABLE word_adjustments(surah INTEGER, ayah INTEGER, kind TEXT, token_index INTEGER, page INTEGER, verified INTEGER, note TEXT);
""")
db.executemany("insert into verse_index values(?,?,?,?,?,?)", vidx)
db.executemany("insert into lines values(?,?,?,?,?,?,?)", [(p,l,t,c,n(f),n(e),n(s)) for p,l,t,c,f,e,s in lines])
db.executemany("insert into word_adjustments values(?,?,?,?,?,?,?)",
               [(a["surah"],a["ayah"],a["kind"],a["tokenIndex"],a["page"],int(a["verified"]),a["note"]) for a in adjs])
page_of_surah = {s: p for p,l,t,c,f,e,s in lines if t == "surah_name"}
for s in range(1, 115):
    vs = [v for v in vidx if v[1] == s]
    db.execute("insert into surahs values(?,?,?,?,?,?)", (s, q.SURAH_NAMES[s-1], len(vs), page_of_surah[s], vs[0][4], vs[-1][4] + vs[-1][5] - 1))
for k, v in {"schema_version": "1", "total_words": str(total), "hafs_text_sha256": q.sha256_hafs_text(q.HAFS), "lines_sha256": q.sha256(q.LINES),
             "mapping_fully_verified": str(int(all(a["verified"] for a in adjs)))}.items():
    db.execute("insert into meta values(?,?)", (k, v))
db.commit(); db.execute("VACUUM"); db.close()
print("wrote", q.INDEX, os.path.getsize(q.INDEX), "bytes; total word ids", total)
