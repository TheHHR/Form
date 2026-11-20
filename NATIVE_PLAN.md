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
- `android-beta.yml` (Form-Beta only) runs the tests, builds `assembleBetaRelease` and publishes the latest release.
- `android-build.yml` is guarded to `TheHHR/Form` and still ships the web app until cutover.

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

## Not ported yet (next betas)
1. AI: bring-your-own-key settings, the meal macro estimate, the review stream and the routine generator.
2. Text import and export for routines, meals and logs, plus clear data.
3. A tags editor and filters, plus the custom-exercise create, edit and delete UI. The data is already preserved in `config.md`.
4. The dashboard month and all-time scopes. (The muscle map is done: MuscleMapJS data, exercise detail and weekly heatmap.)
5. The update check. (Background rest notification, set timer, reminders, shortcuts, tile, widget, Health Connect and barcode lookup are done.)
6. Fonts: Space Grotesk and Roboto still need to be bundled as resources.
7. Cutover: compare 3.6.x and native feature by feature, test an in-place upgrade on a real phone with real data, then ship prod 4.0.0 and retire `android/` and `www/`.
