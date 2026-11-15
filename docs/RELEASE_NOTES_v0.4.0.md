## تسميع — Quran Tasmee3 v0.4.0

Offline Quran recitation-practice app: open a page of the Madani Mushaf, hide the words, recite, and watch each word appear as the on-device speech recogniser hears it. No internet permission; your voice never leaves the phone.

### ✨ What's new
**Navigation**
- New navigation button with three tabs: **Surahs** (all 114), **Page** (1–604) and **Juz** (1–30).
- Settings is now a gear icon; page buttons are `<` and `>` placed together on the left.

**Recitation controls**
- One microphone button for **start / pause / resume**: green mic (ready), amber pause icon (recording), blue play icon (paused). Pausing really stops the microphone and resuming continues from the same word.
- New **إظهار الآية** (Show ayah) button: reveals the current ayah; every further press reveals the next one, continuing onto the next page when needed, without ending the session.
- `<` `>` page buttons now also work during recitation.
- All controls are the same 48 dp size, and the Quran page keeps exactly the same size and position in reading and recitation.

**Mushaf look**
- Surah names use the dedicated surah-name font inside the original decorative frame.
- Hidden words now show one thin, continuous gray underline across the unrevealed words of each line (no more separate short lines); revealed words stay in place with full diacritics.
- Plain white background like a printed Mushaf.

**App identity**
- New app logo and the app is now named **تسميع**.
- New welcome page with the Basmala.

### 📱 Install
1. Download **`Quran-Tasmee3-debug.apk`** below.
2. Open it on your phone and allow *Install unknown apps* if asked.
3. Requires **Android 8.0 or newer** (arm64-v8a, armeabi-v7a, x86_64). Size is about 100 MB because the offline speech model is included.
4. Grant the microphone permission on the first recitation.

> This APK is signed with a debug key. If you later install a build signed with a different key you will need to uninstall this one first.

### ⚠️ Known limitations
- Speech recognition is **approximate**; the app does not judge Tajweed. Words listed *للمراجعة* are uncertain or skipped words, not confirmed mistakes.
- Surah 1 (الفاتحة) has no glyph in the surah-name font and is written in the Quran font.
- Moving to another page with the arrows or *إظهار الآية* during recitation does not carry the previous page's progress into the summary.
- A status message can briefly cover the bottom of the page.
- The font and Quran data licences have not been legally cleared; see `THIRD_PARTY_NOTICES.md`.

**Full change list:** see the repository history. Feedback and bug reports: open an issue.
