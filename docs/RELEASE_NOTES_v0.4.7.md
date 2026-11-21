## تسميع — Quran Tasmee3 v0.4.7

- **Juz/Hizb label follows the end of the page:** the top bar now uses the *last* ayah on the page. If a new hizb quarter starts inside the page, the label shows that new quarter, with its juz (`جزء N · حزب N`).
- **Auto-pause:** recitation pauses by itself after 1 minute without a newly detected word.
- Screenshots in the README replaced with current ones (reader, navigation, recitation, live reveal, summary, settings).
- **Smaller APK:** only the 64-bit ARM (`arm64-v8a`) native library is bundled (was arm64 + armeabi-v7a + x86_64), cutting roughly 40 MB. Requires a 64-bit ARM phone (virtually all devices since ~2019); 32-bit-only phones and x86 emulators are no longer supported.
