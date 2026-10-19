# notes

A personal Android app I'm building for my own use: prayer times, reminders, notes, books with a vocabulary list, and a simple wallet with savings goals, all in one place.

> **Status:** work in progress. This is a personal project, built for my own needs. It is **not published** anywhere (no Play Store or other release), and this repository is the only public place it appears. I plan to publish it once the remaining features are done.

You're welcome to read the code for inspiration or practice, but expect rough edges and changes without notice.

## What it does

- **Prayer times:** times from [Vaktija.ba](https://vaktija.ba) saved for offline use, a live countdown to the next prayer, a persistent notification, tracking and a monthly overview.
- **Reminders:** daily, weekly, monthly and yearly, with "done" and "snooze" actions.
- **Notes:** search and tags, with optional Gemini help to tidy, summarize or translate.
- **Books and vocabulary:** reading progress and saved words with translation, definition and example.
- **Wallet and savings:** spent, earned and set-aside entries, savings goals, and a pace estimate showing how long a goal will take.
- **Reports:** weekly, monthly, yearly or custom period, with PDF export.
- **Hadith of the day:** Arabic and English text, with an optional Bosnian machine translation.

The interface is in Bosnian.

## Tech

Kotlin, Jetpack Compose (Material 3), Room, DataStore, AlarmManager. Everything is stored on the device.

## Setup

Open the project in Android Studio, or run `./gradlew assembleDebug`. Gemini features need your own API key, entered in the app under Profil → Gemini API ključ. The key is stored encrypted on the device and is never part of this repository.

## Credits

- Prayer times: [Vaktija.ba](https://vaktija.ba)
- Hadith text: [AhmedBaset/hadith-json](https://github.com/AhmedBaset/hadith-json), data originally from [Sunnah.com](https://sunnah.com). Hadith grades are shown only for collections where they are known, and machine translations are labelled as such. They are not a substitute for a verified translation or a scholar's explanation.
- Fonts: Newsreader and DM Sans (SIL Open Font License).

## License

No license has been chosen yet, so all rights are reserved for now.
