#!/usr/bin/env python3
"""Renders representative mushaf pages with the exact Hafs font + surah_header_bg.png, using the SAME algorithm and
constants as MushafFit.kt / MushafPage.kt. It previews the layout; it is not the Android renderer.
usage: render_preview.py [out_dir]   (needs Pillow with raqm)"""
import os, sys, sqlite3
from PIL import Image, ImageDraw, ImageFont
HERE = os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0, HERE)
import quran_data as q
ROOT = os.path.dirname(HERE)
FONT = os.path.join(ROOT, "app/src/main/res/font/uthmanic_hafs.ttf")
HDR = os.path.join(ROOT, "app/src/main/res/drawable-nodpi/surah_header_bg.png")
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs/mushaf-previews")
os.makedirs(OUT, exist_ok=True)
# --- constants: keep identical to MushafFit.kt
REF, GROW, MING, NATG, SAFE, LH, MINSLOT, ASPECT = 18.0, 1.15, 0.15, 0.22, 0.985, 1.6, 1.45, 2147/227
S = 2                                   # preview pixels per dp
W_DP, H_DP = 374, 690                   # typical phone page area (390x844 minus bars)
INK, NUM, HID, FRESH, FRESHTXT, MISTAKE, MIST_BG, GOLD, MUTED, PAPER = "#1F2A24", "#8A6D1F", "#8F8668", "#D7EADD", "#17513C", "#A33A2B", "#F6D9D3", "#B8923A", "#5B6258", "#FFFDF6"
rt = lambda f, t: f.getlength(t, direction="rtl", language="ar")
def place_rtl(ws, avail, centered, nat):
    n = len(ws); sm = sum(ws)
    if n == 1 or centered:
        gap = 0 if n == 1 else nat; tot = sm + gap * (n - 1)
        edge = avail if tot >= avail else (avail + tot) / 2
    else:
        gap = max(0, (avail - sm) / (n - 1)); edge = avail
    xs = []
    for w in ws:
        xs.append(round(edge - w)); edge = edge - w - gap
    return xs
verses = q.load_verses(); words, _, _ = q.build_words(verses, q.load_adjustments())
byid = {w["id"]: w for ws in words.values() for w in ws}
names = q.SURAH_NAMES
db = sqlite3.connect(q.INDEX)
bas = [w["text"] for w in words[(1, 1)] if not w["marker"]]
hdr = Image.open(HDR).convert("RGBA")
def render(page, states=None, mode="reading", fn=None):
    lines = list(db.execute("select line,type,centered,first_word_id,last_word_id,surah from lines where page=? order by line", (page,)))
    n = len(lines); W, H = W_DP * S, H_DP * S; slot = H / n
    ref = ImageFont.truetype(FONT, 200, layout_engine=ImageFont.Layout.RAQM)
    em = lambda t: rt(ref, t) / 200
    widest = 0
    for _, t, c, a, b, s in lines:
        if t == "ayah":
            ws = [em(byid[i]["text"]) for i in range(a, b + 1) if i in byid]
            widest = max(widest, sum(ws) + (NATG if c else MING) * (len(ws) - 1))
        elif t == "basmallah":
            ws = [em(x) for x in bas]; widest = max(widest, sum(ws) + NATG * (len(ws) - 1))
    scale = GROW if widest <= 0 else min(GROW, REF / widest)
    fpx = min(W * SAFE * scale / REF, slot / MINSLOT)
    f = ImageFont.truetype(FONT, max(1, round(fpx)), layout_engine=ImageFont.Layout.RAQM)
    img = Image.new("RGB", (W, H), PAPER); d = ImageDraw.Draw(img)
    natg = round(NATG * fpx)
    for k, (ln, t, c, a, b, s) in enumerate(lines):
        cy = (k + .5) * slot
        if t == "surah_name":
            hh = min(slot, W / ASPECT); hw = hh * ASPECT
            im = hdr.resize((round(hw), round(hh)), Image.LANCZOS)
            img.paste(im, (round((W - hw) / 2), round(cy - hh / 2)), im)
            tf = ImageFont.truetype(FONT, max(1, round(min(fpx * 1.25, hh * .5))), layout_engine=ImageFont.Layout.RAQM)
            tx = "سورة " + names[s - 1]
            d.text((W / 2, cy), tx, font=tf, fill=INK, direction="rtl", language="ar", anchor="mm")
            continue
        toks = [(x, False, None) for x in bas] if t == "basmallah" else \
               [(byid[i]["text"], byid[i]["marker"], i) for i in range(a, b + 1) if i in byid]
        ws = [round(rt(f, x[0])) for x in toks]
        xs = place_rtl(ws, W, True if t == "basmallah" else bool(c), natg)
        for (txt, mk, wid), x, w in zip(toks, xs, ws):
            st = "normal" if (mk or wid is None) else (states or {}).get(wid, "normal" if mode == "reading" else "hidden")
            col = NUM if mk else {"normal": INK, "revealed": INK, "correct": FRESHTXT, "current": FRESHTXT, "uncertain": MUTED}.get(st, None)
            ly = cy + fpx * .16 + fpx * .45   # underline just under the baseline
            padx = 3 * S
            if st == "current": d.rounded_rectangle((x - padx, cy - slot*.3, x + w + padx, cy + slot*.3), 6 * S, fill=FRESH)
            if st == "uncertain":
                d.rounded_rectangle((x - padx, cy - slot*.3, x + w + padx, cy + slot*.3), 6 * S, fill="#F3EBD3")
                for xx in range(x, x + w, 10 * S): d.line((xx, ly, min(xx + 6 * S, x + w), ly), fill=GOLD, width=2 * S)
            if st == "mistake":
                d.rounded_rectangle((x - padx, cy - slot*.3, x + w + padx, cy + slot*.3), 4 * S, fill=MIST_BG)
                d.line((x, ly, x + w, ly), fill=MISTAKE, width=round(2.5 * S))
            if st == "hidden": d.line((x, ly, x + w, ly), fill=HID, width=round(1.5 * S))
            if col: d.text((x, cy), txt, font=f, fill=col, direction="rtl", language="ar", anchor="lm")
    img.save(os.path.join(OUT, fn or f"page_{page:03d}.png"))
    return scale, fpx / S
pages = {1: "Al-Fatiha: 8 lines, all centered", 2: "Al-Baqarah start: header + basmallah + centered lines", 3: "plain page",
         50: "Aal-Imran header + basmallah at top", 76: "header on LAST line (basmallah on p77)", 77: "basmallah on first line",
         187: "At-Tawbah header, no basmallah", 262: "unverified page (line breaks may be off by 1 word)",
         586: "two headers (81, 82)", 601: "three headers", 604: "three headers + short centered lines"}
for p, why in pages.items():
    sc, fp = render(p); print(f"page {p:3d}  scale {sc:.2f}  font {fp:5.1f}dp  {why}")
# state demo on page 3: first 40 words revealed, 41 correct, 42 current, 43 uncertain, 44 mistake, rest hidden
ids = sorted(i for i in byid if 78 <= i <= 400 and not byid[i]["marker"])[:80]
st = {i: "revealed" for i in ids[:40]}; st.update({ids[40]: "correct", ids[41]: "current", ids[42]: "uncertain", ids[43]: "mistake"})
render(3, st, "recitation", "page_003_recitation_states.png"); print("states demo written")
