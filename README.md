# AA-ContactMerger

<p align="center">
  <img src="res/drawable/icon.png" alt="AA-ContactMerger icon" width="128" />
</p>

<p align="center">
  <strong>Find duplicate contacts, review them with confidence, and merge them on your terms.</strong>
</p>

AA-ContactMerger is a lightweight Android application that analyses the device contact list to identify likely duplicates. It gives you a clear list to review before merging anything, so your address book stays tidy without losing control over your data.

## A project with a story

The original ContactMerger application was created by [René Treffer](https://github.com/rtreffer) and published as [ContactMerger](https://github.com/rtreffer/ContactMerger).

This repository continues that work. It modernises the original application for current Android devices while preserving its straightforward purpose: helping people clean up duplicate contacts locally, simply, and respectfully.

The current refreshed edition is maintained by [Alfly-Alyx](https://github.com/Alfly-Alyx).

## AA-1.0-beta_3

AA-1.0-beta_3 continues the road to version 1.0 with a clearer welcome screen, French progress messages, and a refined analysis experience.

- **Android 6 to Android 16 support** — minimum API level 23, target API level 36.
- **System language support** — key messages follow the language selected on the device.
- **Light and dark themes** — the interface automatically follows the Android system theme.
- **Clearer analysis flow** — an always-available analysis button and more reliable progress handling.
- **Merge history** — review past merge actions and return easily to the main screen.
- **A renewed About page** — credits the original author, explains the project’s evolution, and shows the installed version.
- **A friendlier clean result** — when no duplicates are found, the app confirms it with its icon and name.

## How it works

1. Open the application and grant contact permission.
2. Tap **Analyze now** to index the contact list locally.
3. Review the suggested duplicate groups.
4. Choose the contacts to merge, or keep them separate.
5. Consult the merge history if you need to review previous actions.

The application analyses contacts on the device. It does not require an online account or a cloud service to do its job.

## Project status

This is a **beta release**. The goal of AA-1.0-beta_3 is to validate the refreshed application on Android 6 through Android 16 before the stable AA-1.0 release.

Compatibility with Android 17 has not yet been validated.

## Build from source

The project uses Gradle and requires:

- JDK 17
- Android SDK Platform 36
- Android Build Tools 36

Create a local `local.properties` file that points to your Android SDK, then run:

```bash
./gradlew assembleDebug
```

The generated APK is available under `build/outputs/apk/debug/`.

> `local.properties`, SDK folders, Gradle caches, backups, and generated APKs are intentionally excluded from Git.

## Credits and licence

- Original project and original creator: [René Treffer](https://github.com/rtreffer/ContactMerger)
- Current modernised edition: [Alfly-Alyx](https://github.com/Alfly-Alyx/AA-ContactMerger)

AA-ContactMerger is available under the [Apache License, Version 2.0](LICENSE.txt).
