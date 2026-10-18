# Aladin Keyboard (Beta)

**Version:** 0.1.0-beta.1  
**Android application ID:** `in.aladin.keyboard`  
**Status:** Early beta — testing only, not yet a store-reviewed release.

Aladin Keyboard is an Android keyboard with local word suggestions, optional local learning, and an optional same-Wi-Fi PC learning-count sync feature.

## Install

Download `AladinKeyboard-0.1.0-release.apk` from the GitHub pre-release assets. Android may ask you to allow installation from the browser or file manager. Only install if you trust this beta build. Android 8.0 or newer is required.

## Privacy and data

- Typed text and passwords are not intentionally uploaded by the keyboard's PC learning-sync feature; that sync sends learned word counts, word-pair counts, and a device identifier.
- Local learning is optional and can be paused in the app settings.
- PC sync is optional and should only be used on a trusted private Wi-Fi network. This beta uses plain HTTP for local-network sync, so do not use PC sync on public Wi-Fi.
- Voice typing uses Android's speech-recognition intent; speech processing behavior depends on the speech-recognition provider and device settings.
- The app requests microphone permission for voice typing and network permission for optional PC sync.
- This is a beta. Review these behaviors and the app's consent screen before using it for sensitive work.

## Beta limitations

This pre-release is intended for manual testing. F-Droid, IzzyOnDroid, Google Play, and other stores have separate metadata, policy, signing, and review requirements; this GitHub release does not mean the app is listed in those stores.

## Checksums

The release notes include the SHA-256 checksum for the APK.