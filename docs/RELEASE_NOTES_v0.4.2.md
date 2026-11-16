## تسميع — Quran Tasmee3 v0.4.2

Offline Quran recitation-practice app: open a page of the Madani Mushaf, hide the words, recite, and watch each word appear as the on-device speech recogniser hears it. No internet permission; your voice never leaves the phone.

### 🛠 Fixes in this version
- **Next page after the results:** pressing *الصفحة التالية* (or *أعد التسميع*) on the results screen now opens the page ready to recite. Before, the results appeared again with zero values and the recording stopped.
- **Page buttons:** the next/previous buttons now use drawn arrows, mirror images of each other, so both are exactly the same size on every phone.

### ✨ Everything else in this release
**Reading**
- **Remembers your last page**: the app reopens on the page you stopped at, even after it was closed.
- **Bookmark**: long-press any ayah to **copy it** or **save it as the bookmark** (one bookmark, a new one replaces the old). The bookmark icon next to the gear jumps back to that ayah and highlights it.
- **Page side indicator**: an open-book icon next to the page number shows the side of the Mushaf spread: odd pages are the right page, even pages the left page.
- Navigation button with **Surahs / Page / Juz** tabs; page buttons are now `←` (next) and `→` (previous).
- Surah names use the dedicated surah-name font in the original decorative frame; plain white background like a printed Mushaf.

**Recitation**
- One microphone button for **start / pause / resume**: green mic (ready), amber pause icon (recording), blue play icon (paused). Pausing really stops the microphone and resuming continues from the same word.
- **إظهار الآية** reveals the current ayah; each further press reveals the next one, continuing onto the next page when needed, without ending the session. **تلميح** reveals the next word.
- Hidden words show one thin, continuous gray underline across the unrevealed words of each line; revealed words stay in place with full diacritics.
- All controls are 48 dp, and the Quran page keeps exactly the same size and position in reading and recitation.

**App**
- New logo, app name **تسميع**, and a short Basmala welcome page (0.5 s).

### 📱 Install
1. Download **`Quran-Tasmee3-v0.4.2.apk`** below.
2. Open it on your phone and allow *Install unknown apps* if asked.
3. Requires **Android 8.0 or newer** (arm64-v8a, armeabi-v7a, x86_64). About 100 MB because the offline speech model is included.
4. Grant the microphone permission on the first recitation.

> This APK is signed with a debug key. A later build signed with a different key cannot update it; uninstall first in that case.

### ⚠️ Known limitations
- Speech recognition is **approximate**; the app does not judge Tajweed. Words listed *للمراجعة* are uncertain or skipped words, not confirmed mistakes.
- Surah 1 (الفاتحة) has no glyph in the surah-name font and is written in the Quran font.
- Moving to another page with the arrows or *إظهار الآية* during recitation does not carry the previous page's progress into the summary.
- A status message can briefly cover the bottom of the page during recitation.
- The font and Quran data licences have not been legally cleared; see `THIRD_PARTY_NOTICES.md`.
