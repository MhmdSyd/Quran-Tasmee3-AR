# AGENT_2_UI_HANDOFF — Recitation controls and ayah reveal

## Changed files
- `recitation/RecitationTracker.kt` — `showAyah()` (reveal every word of the ayah at the cursor, cursor -> first word after it; hidden/skipped words become `Revealed` with `ReviewReason.Manual`, already-recognised words keep their state; `scopeStart` is lowered if the ayah began before it) and `continueOnPage(page, basmallah)` (like `begin`, but keeps segment ids / consumed-audio bookkeeping so audio already handled is not re-matched on the new page). No matching code was touched.
- `recitation/RecitationMatcher.kt` — `ShowAyahResult {Shown, PageEnd}`; `showAyah()` and `continueOnPage()` on the interface, `TrackingRecitationMatcher` (synchronized) and `NoOpRecitationMatcher`.
- `di/AppContainer.kt` — `RecitationSession.currentPage` (page on screen; `page` stays the route page, used for the resume check).
- `ui/components/Icons.kt` — `PauseIcon`, `PlayIcon`, `EyeIcon` (Canvas).
- `ui/screens/RecitationScreen.kt` — mic toggle, control row, "إظهار الآية".
- `test/.../recitation/ShowAyahTest.kt` (new).

## Behaviour
- **One toggle** (`MicButton`, driven by `EngineState`): idle = green + mic icon; Listening = amber + **pause** icon; Paused = slate blue + **play** icon. The old green glow ring is gone. `onMic()` still calls `engine.start/pause/resume` (real capture stop/start); the matcher position is untouched by pause/resume. Resume now also restarts the session timer.
- **Row (physical left -> right, forced LTR):** `<` `>` | hint | إظهار الآية | mic. The "إنهاء" button is removed.
- **`<` / `>` on the recitation screen** switch the recited page (`continueOnPage`), keeping the microphone/session running. Progress of the page that was left is not carried into the summary.
- **إظهار الآية:** press 1 reveals the current ayah, each further press the next ayah. Words come from the page's canonical words. Layout unchanged. If nothing is left on the page it moves to the next page (same session, mic keeps running) and reveals the ayah there (an ayah that continues across the page break is completed on the next page). Showing the page's last ayah does **not** end the session (`finishedByShow`). On page 604 with nothing left, the session finishes normally (summary).
- Speech after a manual reveal continues from the new cursor (`afterManualChange` already drops consumed audio).

## Notes / caveats
- With "إنهاء" gone, a session now ends by: reciting/showing to the end of the Quran, or Back (abandons, no summary). Say if you want an explicit finish action elsewhere (e.g. a long-press or the header).
- Shown words appear in the summary's "for review" list as "أظهرتَها يدويًا".
- **Not verified:** no Android SDK/network in the authoring sandbox — the project was **not compiled**, tests were **not run**, and nothing was committed/pushed (no git repo or remote was in the upload). Run `./gradlew testDebugUnitTest assembleDebug` and check the control row on a 320–360dp screen.
