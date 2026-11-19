# Form native: Kotlin + Jetpack Compose + Material 3 Expressive

Goal: replace the Capacitor web app with a native app that **upgrades in place** with no data loss.

## Ground rules
- **Same app identity.** `com.thehhr.form` (prod) and `com.thehhr.form.beta` (beta). Prod keeps the same release keystore. The first native version is 4.0.0 with versionCode 40000, which is above the current 30603.
- **The data format stays.** The 5 vault files in the user's SAF folder (`routines.md`, `meals.md`, `training_logs.md`, `nutrition_diary.md`, `config.md`) remain the source of truth, byte-compatible with the web app. Text import/export formats stay the same as well.
- **No data migration for the vault.** The native app reads the persisted tree URI from SharedPreferences `saf_vault`/`tree_uri`. That is the grant `SafVaultPlugin` already holds, so a user upgrading in place keeps their folder.
- **The web app is the spec.** `www/app.js` defines behaviour. Port the logic and don't redesign the rules (rest timer, supersets, replacement chain, history seeding).
- **Don't port known bugs.** See "Fix while porting".

## Stack (kept small on purpose)
- Kotlin with Compose BOM, `material3` 1.5.x for the Expressive APIs (`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`), and `MaterialExpressiveTheme`.
- Navigation Compose. ViewModel and StateFlow. kotlinx.serialization for JSON. DataStore for device-only prefs.
- Coil for images, plus `coil-gif` for the prod videos.
- AI uses `HttpURLConnection` with SSE line reading. The providers are OpenAI-compatible plus Gemini, so no extra HTTP library is needed.
- minSdk 29, target/compile 36, Java 21. These match the current build.

## Layout
- `android/native/` is a new app module that sits beside the Capacitor `android/app` until parity.
  - The Capacitor app keeps shipping prod from the main repo, untouched.
  - The native module builds the **beta** flavor in Form-Beta CI from phase 2.
- At parity: delete the Capacitor module and `www/`, except `www/images` (see Media), and move the native module to `android/app`. Prod then ships native 4.0.0.

## Data layer (port first, test hardest)
- `VaultParser` and `VaultWriter` port the lenient line-based `.md` parsers and writers from `app.js` (about lines 880-1400).
  - JVM unit tests check a round trip, parse(write(x)) == x, with golden files from a real vault folder.
  - Golden tests also check the native writer against the web writer's output. This is the one place where tests are required.
- `SafVault` uses `DocumentFile`/`DocumentsContract` reads and writes. Write a temp document and rename it, so files are never truncated. "Not found" and "error" stay distinct (see fixes).
- Exercise catalogue: a build script converts `www/data/exercises.js` to `assets/exercises.json`. Images are copied into assets, and so are videos for prod only.
- localStorage-only keys migrate once through a hidden WebView on `https://localhost`, through `WebViewAssetLoader`, the same origin Capacitor used, then go to DataStore. The keys are `form-active-workout`, `form-ai-config`, `form-ai-insights`, `form-accent`, `form-units`, `form-tab-labels` and `form-welcome-seen`.
  - This is the only part that needs on-device verification against a real 3.6.x install.

## Screens (M3 Expressive mapping)
| Web | Native |
|---|---|
| Floating bottom tabs (Plans / Workout / Dashboard) | `ShortNavigationBar` / floating toolbar |
| Drawers and sheets (routine, fuel manager, log meal) | `ModalBottomSheet` |
| Settings overlay | Full screen with grouped list sections |
| Import popups, confirm, clear data | `AlertDialog` / `BasicAlertDialog` |
| Rest pill + fullscreen ring | Expressive `LoadingIndicator`-style progress, `FloatingActionButtonMenu` actions |
| Filter pill rows | `FilterChip` rows / `ButtonGroup` |
| Steppers (sets/reps/weight) | Expressive split/toggle buttons |
| Toast | `Snackbar` |

Theme:
- Dynamic color stays off by default to keep the brand look. The accent presets become seed colors, the current `#c6f432` is primary, `#ff5449` is error, and the surface ramp comes from `styles.css`.
- Space Grotesk and Roboto are bundled as font resources.
- Dark-only to start, matching the web app.

## Phases
Each phase ends with a beta release from Form-Beta CI.
1. **Scaffold:** set up the module, theme, nav shell and CI, and move the beta flavor to native. The Settings shell picks the vault folder or reuses the existing one.
2. **Data:** the parsers and writers, SafVault, the exercise catalogue, and tests. Read-only screens show the user's real data.
3. **Workout:** start today's routine, the active workout, the rest timer, keep-screen-on, and pause/resume/finish. It writes `training_logs.md`.
4. **Plans:** the routine library and editor (reorder, supersets, timed/weighted/distance), the weekly schedule, and the meal library.
5. **Dashboard and Fuel:** history, charts, the muscle heatmap, the diary, water, targets and profile.
6. **The rest:** custom exercises, tags, import/export text, AI (BYOK settings, meal estimate, review stream, routine generator), clear data, the update check, and localStorage migration.
7. **Parity QA, then prod:** a side-by-side checklist against 3.6.x, an in-place upgrade test on a real phone with real data, then cutover to 4.0.0.

## Fix while porting (from the 2026-11-20 Haiku review)
- A save requested while another save is running is dropped (`app.js:1636`). The native version must queue a follow-up write.
- `findChild` treats any error as "missing", which forks the files as "routines (1).md" (`SafVaultPlugin.java:227`).
- `"wt"` truncate-then-write can leave an empty file (`SafVaultPlugin.java:264`).
- Meals without `id:` all get id 0, so deleting one deletes all of them (`app.js:4676`, `7809`).
- An active workout is discarded after midnight (`app.js:512`).
- The routine reps cap differs between the parser (60) and the editor (100) (`app.js:897` vs `592`).
- A pending in-app routine edit is overwritten by a vault merge (`app.js:1512`).
- The custom exercise id regex is unanchored (`app.js:95`).
- Replacing an exercise leaves the old exercise's logs behind (`app.js:3556`).
- AI keys go into backups (`allowBackup`). Exclude them, or store them in EncryptedSharedPreferences-like storage outside the backup.
- `ai.js:821` calls a missing function, and `ai.js:291`/`813` reassign a `const`.

## Out of scope until asked
Light theme, tablets and foldables, Wear OS, Play Store, notifications for the rest timer while the app is backgrounded, and Persian or other locales.
