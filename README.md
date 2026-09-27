# Form — Training & Nutrition

An offline-first training and nutrition tracker for Android. Plan routines, log workouts with built-in rest timers and a visual exercise library, track meals and macros, and review progress — all your data stays on your device in your own storage folder.

## Features

- **Training** — scheduled routines, a guided active-workout screen with rest timers, set/rep/weight logging, supersets and secondary exercises
- **Exercise library** — 1,324 exercises with animated demos, categories, and custom exercise support
- **Nutrition** — meal logging with a food database, macro/calorie tracking, water intake
- **Progress** — training history, charts, and body-metrics tracking
- **AI (bring your own key)** — weekly/daily training reviews, meal macro estimates from a description, and an AI routine builder. Works with OpenRouter, OpenAI, Gemini, or a custom endpoint; your key stays on your device and requests go straight to your provider
- **Own your data** — everything lives in a plain-text vault (.md) in a folder you pick on your device; view and edit it in Obsidian or any editor, export/import anytime. No servers, no accounts, no tracking.

## Download

<div align="center">

<a href="https://github.com/TheHHR/Form/releases/latest"><img src="docs/badges/get-it-on-github.png" alt="Get it on GitHub" height="60" /></a>
&nbsp;
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.thehhr.form%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FTheHHR%2FForm%22%2C%22author%22%3A%22TheHHR%22%2C%22name%22%3A%22Form%22%7D"><img src="docs/badges/get-it-on-obtainium.png" alt="Get it on Obtainium" height="60" /></a>

</div>

Grab the latest signed APK from [Releases](https://github.com/TheHHR/Form/releases/latest) — every push to `main` rebuilds it automatically. Requires Android 10+.

## Screenshots

| | |
|---|---|
| ![Screenshot 1](docs/screenshots/screenshot-1.png) | ![Screenshot 2](docs/screenshots/screenshot-2.png) |
| ![Screenshot 3](docs/screenshots/screenshot-3.png) | ![Screenshot 4](docs/screenshots/screenshot-4.png) |

## Donate

If Form helps your training, consider supporting development:

[![Donate](https://img.shields.io/badge/Donate-Crypto-f7931a?style=for-the-badge&logo=bitcoin&logoColor=white)](https://thehhr.github.io/Form/#donate)

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
