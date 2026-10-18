# Aladin Keyboard

**Version:** 0.1.0-beta.1  
**Android application ID:** `in.aladin.keyboard`  
**Status:** Free, open-source beta. Not yet store-reviewed.

Aladin Keyboard is an Android input method with word suggestions, optional on-device learning, selectable themes, voice typing through the device's speech-recognition provider, and optional same-Wi-Fi PC learning-count sync.

## Download

Get the APK from the [latest GitHub release](https://github.com/codingbotlab/AladinKeyboard-Android/releases). Android 8.0 (API 26) or newer is required. This is a beta build; install only if you understand the limitations below.

## What's included

- Responsive settings screen with a centered, readable layout on wide displays.
- Keyboard key rows adapt to shorter screens and landscape orientation.
- Five themes: Midnight, Oasis, Royal, Paper, and Korean Cafe.
- Optional local learning; users can pause or clear learned data.
- Export learned word/pair counts as JSON.
- Optional Supabase cloud sync: when enabled, merges learned word-frequency and word-pair counts to/from the user's isolated cloud account on app open/resume (at most once per 15 minutes), plus a manual sync button.
- Optional PC sync of learned counts over a private local network.
- Voice typing delegated to the installed Android speech-recognition provider.

## Build

Open this project in Android Studio and let it install the Android Gradle Plugin and Android SDK components requested by the project. Select the `app` configuration and run it on an Android 8.0+ device or emulator.

The project uses Android Gradle Plugin and Kotlin Gradle Plugin declarations in the root `build.gradle`. A Gradle wrapper is not included in this beta snapshot; use Android Studio's supported Gradle integration.

## Privacy and security notes

- The keyboard stores its learning data locally.
- PC sync sends learned word-frequency counts, word-pair counts, and a device identifier only; it does not intentionally send the raw keystroke stream.
- Supabase cloud sync is off by default and requires explicit opt-in. It uploads learned word-frequency entries and word-pair entries (these may contain individual words the user typed), but not raw keystroke events, whole typed sentences, or passwords. Data is scoped to a per-install anonymous Supabase account and cannot automatically be recovered on another device unless account linking/sign-in is added later.
- Supabase Auth must have Anonymous Sign-Ins enabled in the project settings for cloud sync to authenticate. If it is disabled, sync will show an authentication error and local learning remains available.
- PC sync currently uses plain HTTP and a shared beta token. **Do not enable PC sync on public/untrusted Wi-Fi or expose the PC receiver to the internet.** This sync design needs hardening before a production release.
- Voice typing is handled by the device's configured speech-recognition provider; its offline/network behavior depends on that provider and Android settings.
- Password fields are excluded from the keyboard's local learning flow.
- Review the consent screen and privacy notice before use. Do not use this beta for sensitive typing.

See [PRIVACY.md](PRIVACY.md) for the beta privacy notice.

## Store availability

The APK is available from GitHub. Other app stores have their own submission, metadata, policy and review processes; a GitHub release does not mean the app is listed in those stores.

## License

Released under the [MIT License](LICENSE).
