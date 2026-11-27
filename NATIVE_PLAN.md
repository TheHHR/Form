# Form native app: status and plan

Form's native Android app is written in Kotlin with Jetpack Compose and Material 3 Expressive. It does not use a WebView. The code lives in `native/`.

## Ground rules
- **Same app identity.** The app ids are `com.thehhr.form` (prod) and `com.thehhr.form.beta` (beta). The launcher class is still `com.thehhr.form.MainActivity`.
  - Prod must be signed with the existing release key. Beta uses the Form-Beta key.
- **Version codes.** The version starts at 4.0.0, so the versionCode is 40000 or higher; the web app is 3.x (30603).
  - CI adds the run number, which means each beta build upgrades the one before it.
- **Same data.** The 5 vault `.md` files stay the source of truth. Native output is byte-identical to web 3.6.3.
  - This is tested against the web app's own serializers: `native/tools/web-vault-roundtrip.js` writes the expected files in `native/app/src/test/resources/vault-web-out/`.
  - The test fixtures are synthetic. Never commit real vault data, because this repo merges into the public one.
- **The folder grant carries over.** The SAF tree URI is read from the same SharedPreferences (`saf_vault`/`tree_uri`) as the old `SafVaultPlugin`, so upgrading in place keeps access to the folder.
- **Not carried over:** data the web app kept only in WebView localStorage, namely the AI provider key, an in-progress workout, and AI insights. Accent and units are restored from `config.md`.

## Layout
- `native/` is its own Gradle root, separate from the Capacitor `android/` app. It uses AGP 9.5.1, Gradle 9.6, Kotlin 2.4, compileSdk 37.2, minSdk 29, and Material 3 1.5.0.
- Exercise data and stills are copied at build time from `www/data` and `www/images`. Videos (`www/videos`) are bundled in prod only.
- `android-build.yml` (since 2026-11-27) builds the **native** prod flavor (`com.thehhr.form`, release key `FORM_KEYSTORE_*`, version from `native/version.properties`) as the Latest release and updates the landing page. Bump `native/version.properties` for each release. The Capacitor `android/` app is no longer built.
- `android-beta.yml` is manual (workflow_dispatch): a "Form Beta" prerelease signed with the beta key (`FORM_BETA_*` secrets).

## Done (4.0.0-beta)
- Vault: parse and write all 5 files, the folder picker and first-run seeding.
  - Writes go through one coalescing writer, which fixes the dropped-save bug.
  - Each file is backed up to app storage (`files/backups`) before its first overwrite in a session.
  - When the app resumes, it reloads files that were edited outside it.
- Workout: today's scheduled routine or any routine, a 10-minute warm-up, and set rows for reps and weight or for time, distance and weight.
  - Sets can be checked or added. Removing a set takes the last pending one, which fixes a bug.
  - Exercises can be skipped, done next, replaced or added.
  - Rest uses the web app's superset rules and gives a beep or vibration.
  - Pause and resume, finish and discard, keep-screen-on, and persistence across restarts. A workout is kept for 18 hours, which fixes the after-midnight loss.
- Exercise library: search (tokens, plurals, `#id`), category and liked filters, a detail screen (still image, plus the GIF in prod), instructions, PR, history, add to routine, and manual log.
- Plans: create a routine, edit it (sets, reps, timed sec or min, weight, distance, reorder, supersets), rename, like and delete it. Also the weekly schedule and the meal library CRUD.
- Fuel: day navigation, calories and macros against targets, water, logging from the library or manually, and delete.
- Progress: weekly sessions, sets and volume, a day chart, and the history with delete.
- Settings: accent, units (locked once logs exist), week start, rest settings, body profile, targets, and changing the vault folder.

## Fixes over web 3.6.3 (also covered by tests)
- Diary meals without an id each get their own id. The web app gave them all id 0, so deleting one deleted all of them.
- Unknown diary categories are kept instead of being dropped.
- Routine reps above 60 survive a reload.
- Weights on timed logs are written out.
- The custom-exercise id regex is anchored, which closes the HTML-injection hole.
- `findChild` errors are no longer mistaken for missing files, which forked files as "routines (1).md".
- `allowBackup=false`.

## Done since beta.10 (2026-11-27)
- Workout: fullscreen rest/set timer from the clock pill, tap any exercise to do it next (the turn returns on complete, skip or a second tap), one open card, Restore.
- Live notification: set N/M, next exercise, set progress, Log set / Skip / Skip rest / +30s / Pause, opens the Workout tab. Live Update (promoted ongoing) on Android 16; One UI 8 shows Live Updates in the Now Bar (not verified on a Samsung phone yet).
- Haptics per event (tick, set, exercise, PR, workout, rest over) with `VibrationAttributes`.
- System: Glance widget (same `FormWidget` receiver), Workout QS tile, 4 launcher shortcuts plus water, onboarding with Health Connect weight/height import.
- Theme: light/dark/system and wallpaper colours (device-only prefs), animated colour roles, light-safe accent text roles (unit-tested 4.5:1).
- Parity: custom exercises, tags, share/import routines and meals, skip meal (web `Skipped` placeholder), editable logs, meal photo for vision models, AI tasks that survive tab switches.
- Reminder: one switch, config.md `workout-reminder`, shared with the web app.

## Decisions
- The vault stays the 5 `.md` files (no Room): they are the source of truth and byte-compatible with the web app.
- Device prefs stay in SharedPreferences (no DataStore migration): small, synchronous, already working.
- No per-app language picker yet: there are no translated strings. RTL layout was checked with a Persian app locale.

## Not done yet
1. Translations (then `localeConfig` for per-app language).
2. Real-device checks: Samsung Now Bar, barcode scanner module download (the test emulator has no Play Store), haptic feel.
3. The update check, and bundling Roboto.
4. Cutover (approved 2026-11-27): prod 4.0.0 ships from `native/`. Still to do: retire `android/` and the web-only parts of `www/` (Gradle bundles `www/data`, `www/images`, `www/videos`).
