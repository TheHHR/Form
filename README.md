# Form — Training & Nutrition

An offline-first training and nutrition tracker for Android. Plan routines, log workouts with built-in rest timers and a visual exercise library, track meals and macros, and review progress — all your data stays on your device in your own storage folder.

## Features

- **Training** — scheduled routines, a guided active-workout screen with rest timers, set/rep/weight logging, supersets and secondary exercises
- **Exercise library** — hundreds of exercises with animated demos, categories, and custom exercise support
- **Nutrition** — meal logging with a food database, macro/calorie tracking, water intake
- **Progress** — training history, charts, and body-metrics tracking
- **Own your data** — everything lives in a plain-text vault (.md) in a folder you pick on your device; view and edit it in Obsidian or any editor, export/import anytime. No servers, no accounts, no tracking.

## Download

[![Download APK](https://img.shields.io/badge/Download-APK-3ddc84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/TheHHR/Form/releases/latest)

Grab the latest signed APK from [Releases](https://github.com/TheHHR/Form/releases/latest) — every push to `main` rebuilds it automatically. Requires Android 10+.

## Screenshots

| | |
|---|---|
| ![Screenshot 1](docs/screenshots/screenshot-1.png) | ![Screenshot 2](docs/screenshots/screenshot-2.png) |
| ![Screenshot 3](docs/screenshots/screenshot-3.png) | ![Screenshot 4](docs/screenshots/screenshot-4.png) |

## Donate

If Form helps your training, consider supporting development:

[![Donate](https://img.shields.io/badge/Donate-Crypto-f7931a?style=for-the-badge&logo=bitcoin&logoColor=white)](https://thehhr.github.io/Form/)

## Built with

- Vanilla HTML/CSS/JS web app, wrapped with [Capacitor](https://capacitorjs.com)
- Custom SAF vault plugin for secure user-picked storage
- Screen wake lock during active workouts via `@capacitor-community/keep-awake`

## Credits

- [exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset) by [@hasaneyldrm](https://github.com/hasaneyldrm) — the exercise library: 1,324 exercises with animation GIFs, thumbnails, categories, and muscle data
- [MuscleMapJS](https://github.com/abdofallah/MuscleMapJS) by [@abdofallah](https://github.com/abdofallah) — interactive human body muscle map visualization

## Repository

- `www/` — the web app (source of truth)
- `android/` — native Android project (Gradle)
- `.github/workflows/android-build.yml` — CI: builds the signed release APK and publishes it to the [latest release](https://github.com/TheHHR/Form/releases/latest)
- **Versioning** — edit `version.properties` (semver, e.g. `3.0.2`) and push; `versionCode` is derived as `major×10000 + minor×100 + patch`
