#!/usr/bin/env python3
"""Python mirror of MushafStructureTest (page structure the mushaf renderer relies on). Exit 1 on failure."""
import os, sqlite3, sys
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
db = sqlite3.connect(os.path.join(root, "app/src/main/assets/quran-index.db"))
L = list(db.execute("select page,line,type,centered,coalesce(surah,0) from lines order by page,line"))
fails = []
def check(name, ok, d=""):
    print(("PASS " if ok else "FAIL ") + name + (f"  [{d}]" if d else ""))
    if not ok: fails.append(name)
bad = [p for p in range(1, 605) if [l[1] for l in L if l[0] == p] != list(range(1, (8 if p <= 2 else 15) + 1))]
check("every page has 8 (p1-2) or 15 lines, numbered in order", not bad, str(bad[:5]))
check("114 headers, 112 basmallah", sum(l[2]=="surah_name" for l in L)==114 and sum(l[2]=="basmallah" for l in L)==112)
check("header/basmallah lines always centered", all(l[3]==1 for l in L if l[2]!="ayah"))
bad=[l[4] for i,l in enumerate(L) if l[2]=="surah_name" and ((i+1<len(L) and L[i+1][2]=="basmallah") != (l[4] not in (1,9)))]
check("basmallah directly follows header (also across pages) except surahs 1 and 9", not bad, str(bad))
split=[l[4] for i,l in enumerate(L) if l[2]=="surah_name" and l[1]==15]
check("headers on last line whose basmallah is on next page", len(split)==21, f"{len(split)} surahs")
check("pages 586,601-604 have >=2 headers", all(sum(1 for l in L if l[0]==p and l[2]=="surah_name")>=2 for p in (586,601,602,603,604)))
sys.exit(1 if fails else 0)
