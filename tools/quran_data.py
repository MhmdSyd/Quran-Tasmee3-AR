"""Shared logic for building/validating the Quran word-id mapping. See docs/handoffs/AGENT_2_HANDOFF.md."""
import hashlib, json, os, sqlite3

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
HAFS, LINES, INDEX = (os.path.join(ASSETS, n) for n in ("qpc-hafs.db", "qpc-v1-15-lines.db", "quran-index.db"))
ADJ_FILE = os.path.join(ROOT, "tools", "word_adjustments.json")
SEP = {" ", " "}          # the only separators in verses.text (asserted in tokenise)
ORNAMENT = "۞"           # ۞ standalone rub-el-hizb token, not a word

AYAH_COUNTS = [7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,112,78,118,64,77,227,93,88,69,60,
 34,30,73,54,45,83,182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,
 44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6]
_NAMES = ("الفاتحة|البقرة|آل عمران|النساء|المائدة|الأنعام|الأعراف|الأنفال|التوبة|يونس|هود|يوسف|الرعد|إبراهيم|الحجر|النحل|الإسراء|الكهف|"
 "مريم|طه|الأنبياء|الحج|المؤمنون|النور|الفرقان|الشعراء|النمل|القصص|العنكبوت|الروم|لقمان|السجدة|الأحزاب|سبأ|فاطر|يس|الصافات|ص|الزمر|غافر|"
 "فصلت|الشورى|الزخرف|الدخان|الجاثية|الأحقاف|محمد|الفتح|الحجرات|ق|الذاريات|الطور|النجم|القمر|الرحمن|الواقعة|الحديد|المجادلة|الحشر|الممتحنة|"
 "الصف|الجمعة|المنافقون|التغابن|الطلاق|التحريم|الملك|القلم|الحاقة|المعارج|نوح|الجن|المزمل|المدثر|القيامة|الإنسان|المرسلات|النبأ|النازعات|عبس|"
 "التكوير|الانفطار|المطففين|الانشقاق|البروج|الطارق|الأعلى|الغاشية|الفجر|البلد|الشمس|الليل|الضحى|الشرح|التين|العلق|القدر|البينة|الزلزلة|"
 "العاديات|القارعة|التكاثر|العصر|الهمزة|الفيل|قريش|الماعون|الكوثر|الكافرون|النصر|المسد|الإخلاص|الفلق|الناس")
SURAH_NAMES = _NAMES.split("|")

def sha256(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()

def tokenise(text):
    """Split canonical verse text into word tokens (ayah-number marker is the last token)."""
    out, cur = [], []
    for ch in text:
        if ch in SEP:
            if cur: out.append("".join(cur)); cur = []
        else:
            cur.append(ch)
    if cur: out.append("".join(cur))
    return [t for t in out if t != ORNAMENT]

def load_adjustments():
    return json.load(open(ADJ_FILE, encoding="utf-8"))

def load_verses():
    """[(rowid, id, surah, ayah, text)] ordered by id."""
    c = sqlite3.connect(f"file:{HAFS}?mode=ro", uri=True)
    return list(c.execute("select rowid,id,surah,ayah,text from verses order by id"))

def build_words(verses, adjustments):
    """Returns per-verse list of display words: dict(id,id_end,text,index,marker) plus verse_index rows.
    split  : token k occupies two consecutive ids (id,id_end=id+1); text is NOT altered.
    merge  : tokens k and k+1 form one word with one id; text = both tokens joined by a single space."""
    adj = {(a["surah"], a["ayah"]): a for a in adjustments}
    nid, words, vidx = 1, {}, []
    for rowid, vid, s, a, text in verses:
        toks = tokenise(text)
        ad = adj.get((s, a))
        ws, i = [], 0
        while i < len(toks):
            k = i + 1
            if ad and ad["kind"] == "split" and k == ad["tokenIndex"]:
                ws.append(dict(id=nid, id_end=nid + 1, text=toks[i])); nid += 2
            elif ad and ad["kind"] == "merge" and k == ad["tokenIndex"]:
                ws.append(dict(id=nid, id_end=nid, text=toks[i] + " " + toks[i + 1])); nid += 1; i += 1
            else:
                ws.append(dict(id=nid, id_end=nid, text=toks[i])); nid += 1
            i += 1
        for n, w in enumerate(ws): w["index"] = n + 1; w["marker"] = (n == len(ws) - 1)
        words[(s, a)] = ws
        vidx.append((vid, s, a, rowid, ws[0]["id"], ws[-1]["id_end"] - ws[0]["id"] + 1))
    return words, vidx, nid - 1
