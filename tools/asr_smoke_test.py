#!/usr/bin/env python3
"""Desktop smoke test of the exact model + runtime version used on Android (sherpa-onnx 2.0.1, zipformer-CTC loader).

  pip install sherpa-onnx==2.0.1 soundfile numpy
  bash tools/fetch_asr_assets.sh        # once, to get model.int8.onnx
  python3 tools/asr_smoke_test.py [--require-audio]

Checks (blocking): model loads; decoding synthetic audio runs; tokens/timestamps are consistent (40 ms grid);
and, when EveryAyah clips can be downloaded: word error rate vs the Hafs text after ModelAlphabet-style folding.
Also prints how a truncated clip's transcript relates to the full one (what Partial events look like). It does NOT
test Android audio capture or the Kotlin pipeline (those are covered by JVM tests / the on-device diagnostics screen).
"""
import argparse, io, os, sqlite3, sys, time, urllib.request
import numpy as np, soundfile as sf, sherpa_onnx

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSET = os.path.join(ROOT, "app/src/main/assets/asr/quran-asr-small")
LETTERS = set("ءابتثجحخدذرزسشصضطظعغفقكلمنهوي")
FOLD = {"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ٲ": "ا", "ٳ": "ا", "ى": "ي", "ئ": "ي", "ے": "ي", "ی": "ي", "ؤ": "و", "ة": "ه", "ک": "ك"}
fold = lambda w: "".join(c for c in (FOLD.get(ch, ch) for ch in w) if c in LETTERS)
CLIPS = [("001001", "1:1"), ("001002", "1:2"), ("001006", "1:6"), ("112001", "112:1"), ("114001", "114:1"), ("002002", "2:2")]
URL = "https://everyayah.com/data/Alafasy_128kbps/{}.mp3"

def wer(ref, hyp):
    d = list(range(len(hyp) + 1))
    for i, r in enumerate(ref, 1):
        prev, d[0] = d[0], i
        for j, h in enumerate(hyp, 1):
            prev, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, prev + (r != h))
    return d[-1] / max(1, len(ref))

def to16k(wave, sr):
    wave = wave.mean(axis=1) if wave.ndim > 1 else wave
    if sr != 16000:
        from math import gcd
        from scipy.signal import resample_poly
        g = gcd(sr, 16000); wave = resample_poly(wave, 16000 // g, sr // g)
    return wave.astype(np.float32)

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--require-audio", action="store_true"); a = ap.parse_args()
    model = os.path.join(ASSET, "model.int8.onnx")
    if not os.path.exists(model): sys.exit("model.int8.onnx missing: run tools/fetch_asr_assets.sh")
    t0 = time.time()
    rec = sherpa_onnx.OfflineRecognizer.from_zipformer_ctc(model=model, tokens=os.path.join(ASSET, "tokens.txt"), num_threads=2)
    print(f"model loaded in {time.time() - t0:.2f}s")

    def decode(x):
        s = rec.create_stream()
        s.accept_waveform(16000, x)
        rec.decode_stream(s)

        return s.result

    # 1) synthetic audio must decode without error, for a range of lengths incl. very short ones
    rng = np.random.default_rng(0)
    for sec in (0.3, 1, 5, 10):
        x = (0.1 * rng.standard_normal(int(16000 * sec))).astype(np.float32)
        t = time.time(); r = decode(x); dt = time.time() - t
        print(f"synthetic {sec:>4}s noise -> {len(r.text)} chars, {dt*1000:.0f} ms (RTF {dt/sec:.3f})")

    # 2) real audio
    db = sqlite3.connect(os.path.join(ROOT, "app/src/main/assets/qpc-hafs.db"))
    wers = []; fail = False
    for clip, key in CLIPS:
        try:
            raw = urllib.request.urlopen(urllib.request.Request(URL.format(clip), headers={"User-Agent": "asr-smoke"}), timeout=30).read()
            wave, sr = sf.read(io.BytesIO(raw), dtype="float32", always_2d=True)
        except Exception as e:
            print(f"[{key}] audio unavailable: {e}"); continue
        x = to16k(wave, sr)
        s, ay = key.split(":")
        text = db.execute("select text from verses where surah=? and ayah=?", (int(s), int(ay))).fetchone()[0]
        ref = [f for f in (fold(w) for w in text.replace("\u00a0", " ").split(" ") if w != "۞") if f]
        t = time.time(); r = decode(x); dt = time.time() - t
        hyp = [w for w in r.text.split("\u2581") if w]
        w_ = wer(ref, hyp); wers.append(w_)
        # timestamp sanity (40 ms grid) on real output
        ts = list(r.timestamps)
        if len(ts) != len(r.tokens) or any(abs(round(v / 0.04) * 0.04 - v) > 1e-3 for v in ts) or ts != sorted(ts):
            print(f"[{key}] TIMESTAMP ANOMALY"); fail = True
        # how do truncated decodes look (Partial behaviour)?
        parts = []
        for frac in (0.5, 0.75):
            ph = [w for w in decode(x[: int(len(x) * frac)]).text.split("\u2581") if w]
            n = 0
            while n < min(len(ph), len(hyp)) and ph[n] == hyp[n]: n += 1
            parts.append(f"{int(frac*100)}%:{n}/{len(ph)} prefix-words stable")
        print(f"[{key}] {len(x)/16000:.1f}s audio, {dt*1000:.0f} ms, WER {w_:.2f} | {'; '.join(parts)}\n   ref: {' '.join(ref)}\n   hyp: {' '.join(hyp)}")
    if not wers:
        print("no audio available"); sys.exit(1 if a.require_audio else 0)
    avg = sum(wers) / len(wers); print(f"mean WER over {len(wers)} clips: {avg:.3f}")
    if fail or avg > 0.15: print("FAIL"); sys.exit(1)
    print("PASS")

main()
