#!/usr/bin/env python3
"""Independent integrity checks over qpc-hafs.db + qpc-v1-15-lines.db + quran-index.db. Exit code 1 on any hard failure."""
import os, sqlite3, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_data as q

fails, notes = [], []
def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail else ""))
    if not ok: fails.append(name)

ro = lambda p: sqlite3.connect(f"file:{p}?mode=ro", uri=True)
H, L, X = ro(q.HAFS), ro(q.LINES), ro(q.INDEX)
verses = q.load_verses(); adjs = q.load_adjustments()
words, vidx, total = q.build_words(verses, adjs)

# ---- Hafs source
check("hafs: 6236 verses, ids 1..6236 == rowids", len(verses) == 6236 and all(v[0] == v[1] == i + 1 for i, v in enumerate(verses)))
check("hafs: 114 surahs, ayah counts match canonical", [sum(1 for v in verses if v[2] == s) for s in range(1, 115)] == q.AYAH_COUNTS)
check("hafs: ayah numbers sequential per surah", all(v[3] == (verses[i-1][3] + 1 if i and verses[i-1][2] == v[2] else 1) for i, v in enumerate(verses)))
def arabic_int(t): return int("".join(str(ord(c) - 0x660) for c in t))
check("hafs: last token of every verse is its ayah number", all(arabic_int(q.tokenise(v[4])[-1]) == v[3] for v in verses))
check("hafs: only ' ' and NBSP separate tokens; no ornament inside tokens",
      all(not any(c.isspace() and c not in q.SEP for c in v[4]) and all(q.ORNAMENT not in t or t == q.ORNAMENT for t in v[4].replace(" ", " ").split(" ")) for v in verses))
naive = sum(len(q.tokenise(v[4])) for v in verses)
check("hafs: naive token count", naive == 83666, f"{naive}")

# ---- Line DB
lines = list(L.execute("select page_number,line_number,line_type,is_centered,first_word_id,last_word_id,surah_number from pages order by page_number,line_number"))
from collections import Counter
cnt = Counter(l[0] for l in lines)
check("lines: 604 pages; pages 1-2 have 8 lines, all others 15; line numbers 1..n", len(cnt) == 604 and all(cnt[p] == (8 if p < 3 else 15) for p in cnt)
      and all(sorted(l[1] for l in lines if l[0] == p) == list(range(1, cnt[p] + 1)) for p in (1, 2, 3, 604)))
ayah_lines = [l for l in lines if l[2] == "ayah"]
check("lines: types = 114 surah_name / 112 basmallah / 8820 ayah",
      (sum(l[2] == "surah_name" for l in lines), sum(l[2] == "basmallah" for l in lines), len(ayah_lines)) == (114, 112, 8820))
check("lines: ayah ranges contiguous 1..83668 in reading order",
      ayah_lines[0][4] == 1 and ayah_lines[-1][5] == 83668 and all(b[4] == a[5] + 1 and b[4] <= b[5] for a, b in zip(ayah_lines, ayah_lines[1:])))
check("mapping: total word ids == line DB max id", total == 83668, f"{total}")

# ---- Word map vs line DB (every word range)
mk = {}                      # last id of verse -> (surah, ayah)
first_of = {}                # first id of verse
vlist = []                   # (first_id, last_id, surah, ayah)
for (s, a), ws in words.items():
    vlist.append((ws[0]["id"], ws[-1]["id_end"], s, a)); mk[ws[-1]["id_end"]] = (s, a); first_of[ws[0]["id"]] = (s, a)
vlist.sort()
check("mapping: verse id ranges tile 1..83668 without gaps", vlist[0][0] == 1 and vlist[-1][1] == 83668 and all(b[0] == a[1] + 1 for a, b in zip(vlist, vlist[1:])))
# surah starts: line DB first ayah line after each surah_name must equal first word id of ayah 1
cur, starts = None, {}
for p, l, t, c, f, e, s in lines:
    if t == "surah_name": cur = s
    elif t == "ayah" and cur: starts[cur] = f; cur = None
check("surah starts: 114/114 line-DB first ids == mapped first word of ayah 1", all(starts[s] == words[(s, 1)][0]["id"] for s in range(1, 115)))
check("basmallah lines: present exactly for surahs != 1, 9",
      sorted(l[6] for i, l in enumerate(lines) if l[2] == "surah_name" and lines[i+1][2] == "basmallah") == [s for s in range(1, 115) if s not in (1, 9)])
# surah_name line precedes surah's first ayah line (and no ayah of the previous surah shares that line)
check("surah ends: line before next surah_name ends on that surah's last-ayah marker",
      all(lines[i-1][5] == words[(l[6]-1, q.AYAH_COUNTS[l[6]-2])][-1]["id_end"] for i, l in enumerate(lines) if l[2] == "surah_name" and l[6] > 1 and lines[i-1][2] == "ayah"))
check("surah ends: last line of page 604 ends at 83668 (114:6 marker)", mk[83668] == (114, 6))
# page ends on markers (property of the 1405 Madani print) — hard check, page-level exactness
pages_last = {}
for p, l, t, c, f, e, s in ayah_lines: pages_last[p] = e
bad_pages = [p for p, e in pages_last.items() if e not in mk]
check("pages: every page's last word id is an ayah-number marker (604/604)", not bad_pages, f"bad={bad_pages[:10]}")
pages_first = {}
for p, l, t, c, f, e, s in ayah_lines: pages_first.setdefault(p, f)
# pages begin at an ayah start (or surah start)
check("pages: every page's first word id is the first word of an ayah", all(f in first_of for f in pages_first.values()))
# independent anchors: juz starts begin at line 1 of page 1+20*(j-1)... known (surah, ayah) and page numbers
JUZ = [(1,1),(2,142),(2,253),(3,92),(4,24),(4,148),(5,83),(6,111),(7,88),(8,41),(9,94),(11,6),(12,53),(15,1),(17,1),(18,75),(21,1),(23,1),(25,21),(27,56),
       (29,46),(33,31),(36,28),(39,32),(41,47),(46,1),(51,31),(58,1),(67,1),(78,1)]
ok = all(pages_first.get(1 if i == 0 else 2 + 20 * i) == words[sa][0]["id"] for i, sa in enumerate(JUZ) if i != 25)  # juz 26 (46:1) starts mid-page 502
check("anchors: 29 of 30 juz begin on page 1 / 2+20*(j-1) at the expected ayah (independent of the mapping)", ok)
KNOWN = {2: 2, 3: 50, 9: 187, 18: 293, 36: 440, 67: 562, 78: 582, 114: 604}
check("anchors: known surah start pages", all(X.execute("select start_page from surahs where surah=?", (s,)).fetchone()[0] == p for s, p in KNOWN.items()))

# ---- Derived index DB agrees with this independent rebuild
check("index: verse_index == rebuilt", sorted(X.execute("select verse_id,surah,ayah,hafs_rowid,first_word_id,word_count from verse_index")) == sorted(vidx))
check("index: lines table == line DB (typed)", list(X.execute("select page,line,type,centered,first_word_id,last_word_id,surah from lines order by page,line"))
      == [(p, l, t, c, None if f == "" else f, None if e == "" else e, None if s == "" else s) for p, l, t, c, f, e, s in lines])
check("index: surahs table (114 rows, names, ayah counts)", [r[:3] for r in X.execute("select surah,name_ar,ayah_count from surahs order by surah")] == [(i+1, q.SURAH_NAMES[i], q.AYAH_COUNTS[i]) for i in range(114)])
check("index: source hashes match", dict(X.execute("select key,value from meta"))["hafs_sha256"] == q.sha256(q.HAFS) and dict(X.execute("select key,value from meta"))["lines_sha256"] == q.sha256(q.LINES))

# ---- Canonical text preservation: every displayed word is a verbatim substring/join of the Hafs verse
def canon_ok():
    for rowid, vid, s, a, text in verses:
        toks = [t for t in text.replace(" ", " ").split(" ") if t and t != q.ORNAMENT]
        out = []
        for w in words[(s, a)]: out.extend(w["text"].split(" "))
        if out != toks: return False
    return True
check("text: concatenated mapped words == tokens of the original verse text (no normalisation)", canon_ok())

# ---- Report on unresolved parts
unver = [a for a in adjs if not a["verified"]]
print("\nUNRESOLVED:", len(unver), "adjustment(s) unverified:", [(a["surah"], a["ayah"], a["kind"], "page", a["page"]) for a in unver])
for a in unver:
    n = sum(1 for l in ayah_lines if l[0] == a["page"])
    print(f"  page {a['page']}: {n} ayah lines — word SET of page exact; line breaks inside it may be off by <=1 word")
print("\nRESULT:", "FAILED " + str(fails) if fails else "all hard checks passed")
sys.exit(1 if fails else 0)
