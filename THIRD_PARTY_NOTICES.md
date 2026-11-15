# Third-party notices

Redistribution status is stated as *found*, not as legal clearance. **Verify before publishing the APK.**

| Component | Where | Terms found | Status |
|---|---|---|---|
| **KFGQPC HAFS Uthmanic Script** (`UthmanicHafs1Ver18.ttf`, v0.18) | `app/src/main/res/font/uthmanic_hafs.ttf`, byte-identical to the supplied file | The licence embedded in the font (name table, ID 13) grants, free of cost, the right to **Use, Copy, Distribute**, and says the font **cannot be sold, modified, altered, translated, reverse engineered or reproduced**; © King Fahd Glorious Quran Printing Complex, all rights reserved; "AS IS". Source: fonts.qurancomplex.gov.sa | The font is bundled **unmodified**. Do not sell the app/font. Not legally reviewed; confirm with KFGQPC if distributing widely. |
| **Quran text** `qpc-hafs.db` and **15-line layout** `qpc-v1-15-lines.db` (Quran Complex V1, 1405 print) | `app/src/main/assets/` (copies of the supplied files; `quran-index.db` is derived from them) | No licence text is included in either database file. | **Unverified.** Provenance/licence of these exports (QPC/QUL-style data) must be confirmed by the project owner before redistribution. |
| `surah_header_bg.png` | `res/drawable-nodpi/` | Supplied with the project; no licence statement supplied. | **Unverified.** |
| **quran-asr-small** (`Abdou/quran-asr-small`, commit `0c2744b9…`) | Fetched at build time into the APK (not stored in git) | Model card reported as MIT by the integration notes (not independently re-checked); the training data keeps its own terms: `tarteel-ai/tlog` is CC-BY-4.0 → attribution is shown in Settings. | Attribution included. Re-check the model card before publishing. |
| **sherpa-onnx** v2.0.1 (Kotlin API vendored in `com/k2fsa/sherpa/onnx/`, native libs fetched) | see `LICENSE-sherpa-onnx.txt` | Apache-2.0 | OK; licence file included. ONNX Runtime is statically linked into the sherpa library (MIT). |
| AndroidX, Jetpack Compose, Material3, Navigation, Kotlin, kotlinx.coroutines | Gradle dependencies | Apache-2.0 | OK |
| Design reference | `docs/design/` | Project-internal | – |

The UI font (Cairo) is **not** bundled.

## header-surah-name.ttf
Surah-name font supplied by the project owner (res/font/header_surah_name.ttf; renamed because Android resource names cannot contain hyphens). One glyph per surah 2..114 at U+E000+n. Used only for surah names/headings.
