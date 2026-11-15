# Publishing a release (v0.4.0 example)

## 0. Before you publish (5 minutes on a real phone)
The release notes promise these things, so check them once on a device with the APK you are about to publish:
- [ ] Welcome page appears, then the reader opens; logo and name **تسميع** on the launcher.
- [ ] Navigation button → Surah / Page / Juz all jump correctly; `<` goes to the next page, `>` to the previous.
- [ ] Recitation: mic → pause icon → play icon → resume continues from the same word.
- [ ] **إظهار الآية** reveals the current ayah, then the next, including across a page end.
- [ ] Hidden-word underline is thin, gray and connected; the page is the same size in reading and recitation.

## 1. Version (already done in this repo)
`app/build.gradle.kts`: `versionCode = 4`, `versionName = "0.4.0"`.

## 2. Push the code and let CI build the APK
```bash
git add -A
git commit -m "v0.4.0: navigation, recitation controls, connected underline, surah font, welcome page, new logo"
git push origin main
```
Open **Actions → Android CI**. Wait until the **build** job is green, then open the run and download the artifact
**`Quran-Tasmee3-debug`** (a zip). Unzip it; inside is **`Quran-Tasmee3-debug.apk`**.
If you want the version in the file name, rename it to `Quran-Tasmee3-v0.4.0.apk`.

## 3. Create the release on GitHub (web)
1. Repository page → **Releases** (right side) → **Draft a new release**.
2. **Choose a tag** → type `v0.4.0` → *Create new tag: v0.4.0 on publish*. Target: `main`.
   (Pushing a `v*` tag also starts the workflow; the optional signed-release job is simply skipped when the signing secrets are not set.)
3. **Release title:** `تسميع — Quran Tasmee3 v0.4.0`
4. **Description:** paste the whole content of [`RELEASE_NOTES_v0.4.0.md`](RELEASE_NOTES_v0.4.0.md).
5. Drag the APK into **Attach binaries by dropping them here** and wait for the upload bar to finish.
6. Tick **Set as a pre-release** if you have not finished the device checks above (recommended for the first public APK), otherwise tick **Set as the latest release**.
7. **Publish release**.

## 3b. Same thing with the GitHub CLI
```bash
gh release create v0.4.0 Quran-Tasmee3-v0.4.0.apk \
  --title "تسميع — Quran Tasmee3 v0.4.0" \
  --notes-file docs/RELEASE_NOTES_v0.4.0.md \
  --target main --prerelease
```

## 4. After publishing
- Open the release page on your phone, download from there, and install once more to confirm the published file works.
- Keep the same file name pattern for the next versions (`v0.5.0`, ...) and bump `versionCode` each time (Android only updates to a higher `versionCode`).

## Notes
- The CI artifact is a **debug-signed** APK. It installs fine, but updates only work between builds signed with the same key. For a stable update path, add the four signing secrets described in the README and attach the `Quran-Tasmee3-release` APK instead.
- GitHub allows release files up to 2 GB, so the ~100 MB APK is fine.
- Before wide distribution read `THIRD_PARTY_NOTICES.md`: the font and Quran data redistribution rights were not independently verified.
