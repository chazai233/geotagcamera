<picture>
  <source media="(prefers-color-scheme: dark)" srcset="assets/banner-dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="assets/banner-light.svg">
  <img alt="GeoTag Camera" src="assets/banner-light.svg">
</picture>

# GeoTag Camera

No ads. No paywall. We know, it's suspicious.

Free, open source, ad free Android camera app that stamps every photo with GPS location, address and timestamp, and signs it on-device so anyone can later check it hasn't been edited. The core capture flow works with no signal at all. Built for field surveyors, college students and NGO teams who need to show where and when a photo was taken, without paying for it or handing over their data.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://developer.android.com)
[![Build](https://github.com/konkomaji/geotagcamera/actions/workflows/android-ci.yml/badge.svg)](https://github.com/konkomaji/geotagcamera/actions/workflows/android-ci.yml)
[![Release](https://img.shields.io/github/v/release/konkomaji/geotagcamera?label=release)](https://github.com/konkomaji/geotagcamera/releases/latest)

Website: https://konkomaji.github.io/geotagcamera/

## Why this exists

Most geotagging camera apps on the Play Store make you sit through ads before you can even open the camera, lock basic stamp fields behind a subscription, ask for permissions that have nothing to do with taking a photo, and stop working the moment you lose signal. GeoTag Camera fixes that, in the open, so anyone can read the code and check it for themselves.

## Features

**Capture**
- Clean camera capture built on CameraX, with flash, front/back switch, pinch and rail zoom, tap-to-focus, a rule-of-thirds grid and 4:3 / 16:9 / 1:1 aspect ratios
- A live stamp preview in the viewfinder, drawn by the same code that burns the stamp into the saved photo, so what you frame is what you get
- GPS coordinates, address and timestamp stamped directly onto the photo, using one of three templates (Card, Bar, Minimal) at any of nine positions
- GPS data also written into the photo's own EXIF metadata, so GIS and photo tools pick it up automatically
- Every capture is saved straight to your phone's Photos/Gallery app (Pictures/GeoTagCamera). Nothing leaves your phone unless you choose to share it

**Built to actually work in the field**
- Capturing, stamping, signing and verifying all work with no network connection
- Addresses are cached on-device, so once a place has been looked up the stamp keeps showing it with no signal
- Every capture fetches a fresh location instead of reusing a stale cached one
- Only camera and location are requested at runtime on Android 10+; Android 9 and older also needs storage access to save into the gallery

**Made for your organization**
- Choose exactly which fields show on the stamp: coordinates, address, timestamp, altitude, accuracy, compass bearing, Plus Code, GMT offset and more
- Add your college, company or project name and logo to the stamp, free, no paywall

**Trust and verification**
- Every photo is hashed with SHA-256 and signed on-device with a key kept in the Android Keystore. See [How photo verification works](#how-photo-verification-works) for exactly what that does and does not prove
- Verify any photo that carries a GeoTag Camera proof, including one someone else sent you, from the "Verify a photo" screen or via Share → Verify with GeoTag Camera
- Field workers can add a hand-drawn signature onto the photo before saving, useful for inspection reports and muster-roll style documentation

**Optional extras, off by default**
- A small map thumbnail on the stamp, using Stadia Maps tiles drawn from OpenStreetMap data
- A current-weather chip, using Open-Meteo

Both need an internet connection, both are off until you turn them on, and a capture never waits on either of them.

**No ads, no tracking, ever**
- No ad SDKs, no analytics SDKs, no crash reporters
- No account, no cloud sync, and no server of ours collecting anything, because there is no server

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- CameraX for capture
- Fused Location Provider (Google Play services) for GPS
- Android's built-in Geocoder for reverse geocoding (no third party API key)
- Room and DataStore for local storage
- Android Keystore for on-device photo signing
- Coil for gallery thumbnails

## Project status

Version 1.1.0 is released. It was a full redesign rather than a feature bolt-on: a new design system and brand mark, a proper stamp with a card and an optional map thumbnail, a live stamp preview in the viewfinder, a share button, real camera controls, and portable verification so you can check a photo that was taken on someone else's phone. See [CHANGELOG.md](CHANGELOG.md) for the full list.

Download the APK from [GitHub Releases](https://github.com/konkomaji/geotagcamera/releases/latest). Each release lists the SHA-256 of the APK so you can check the download.

## Roadmap

**Later**
- Video geotagging
- Project and site folders to organize captures
- Batch export to PDF report
- CSV, KML and GeoJSON export for GIS workflows
- Voice notes attached to photos
- Before and after photo pairing
- Quick capture home screen widget
- Biometric app lock
- Manual local backup and restore
- Material You dynamic theming

## How photo verification works

When you take a photo, the app:

1. Renders the stamp onto the image and saves it as a JPEG.
2. Computes a SHA-256 hash over a canonical view of that JPEG: the image data itself (quantization and Huffman tables, frame header and the compressed scan), with every APPn metadata segment left out. That is where EXIF and XMP live, so the proof can be written back into the file without changing the hash. See [`JpegCanonical.kt`](app/src/main/java/com/geotagcamera/geotagginglocationonphoto/exif/JpegCanonical.kt).
3. Signs that hash with an ECDSA (P-256, SHA256withECDSA) key generated on the device in the Android Keystore. The private key can't be exported from the Keystore. On most phones it is held in secure hardware, but that depends on the device.
4. Embeds the hash, the signature and the matching public key in the photo's EXIF UserComment, mirrored in an XMP segment.

Verifying recomputes the canonical hash from the file, checks that it matches the embedded hash, and checks the signature against the embedded public key. No network and no account are involved, so any copy of the app can check any photo.

**What a "verified" result tells you:** the image pixels (which include the burned-in stamp) have not changed since they were signed by the key whose public half is embedded in the file.

**What it does not tell you:**
- **Who signed it, or on which device.** The public key travels inside the photo and is not tied to a person, a phone, or a certificate authority. Someone who edits a photo can strip the proof and re-sign the edited version with a key of their own, and it will verify. If you need to know that a photo came from a particular person, get their public key from them through a channel you trust and compare it.
- **That the location or time is true.** The app records what the phone reported. A spoofed GPS fix or a wrong system clock will be stamped and signed like any other.
- **Anything about the metadata.** EXIF and XMP are deliberately outside the hash, so the EXIF GPS tags and timestamps can be changed without breaking verification. The burned-in stamp is part of the pixels, so that is the part that is protected.

Re-encoding the photo, for example when a messaging app compresses it, changes the pixels and the photo will no longer verify, even though nobody meant to tamper with it. Share the original file when verification matters.

## Privacy

Short version: everything stays on your phone unless you share it or turn on one of the optional online features. The full policy is in [PRIVACY.md](PRIVACY.md).

**Stored on your device only**
- Your photos, in Pictures/GeoTagCamera
- A small database of your captures (file location, time, coordinates, altitude, accuracy, bearing, address, organization label, hash and signature)
- A cache of addresses you have looked up, and a cache of map tiles if the map is on
- Your settings and, if you upload one, your organization logo
- The signing key, inside the Android Keystore

Android backup is turned off for the app, so none of this is copied to a cloud backup. "Delete all data" on the About & legal screen removes the app's database, settings, logo and caches. Photos you already saved to the gallery stay there until you delete them.

**What leaves your device, and when**
- **Map thumbnail (off by default):** the app downloads map tiles from Stadia Maps. Each request contains the tile's x/y/z coordinates at zoom 16, which pins down your location to within a few hundred metres, plus the API key built into the app. Stadia also sees your IP address, as any server does.
- **Weather (off by default):** the app sends your latitude and longitude to Open-Meteo to get the current temperature and conditions. The current code sends them to five decimal places (around a metre), so treat this as your precise location. Open-Meteo also sees your IP address.
- **Address lookup:** the app uses Android's own Geocoder. On most phones that service is provided by Google Play services and needs a network connection, so the coordinates you look up go to Google under Google's terms, not ours. Results are cached on the device.
- **Location:** GPS fixes come from Google Play services' Fused Location Provider, which may use Wi-Fi and cell data depending on your phone's location settings.
- **When you share a photo:** the file carries its EXIF data, including the exact GPS coordinates, altitude and time, as well as the burned-in stamp and the verification proof. Whoever receives the file can read all of it.

There are no ads, analytics, crash reporting or tracking SDKs in the app.

## Permissions

| Permission | Why it's needed |
|---|---|
| Camera | To take the photo |
| Location (fine and coarse) | To read GPS coordinates for the stamp and EXIF data. Only while the app is in use, never in the background |
| Internet | Only for the optional map thumbnail (Stadia Maps) and weather chip (Open-Meteo), both off by default. Android's Geocoder may also use the network on its own |
| Storage (Android 9 / API 28 and below only) | To save the finished photo into the gallery. Android 10+ does this without any storage permission |

That's the complete list. No broad storage access on modern Android, no contacts, no background location.

## Building from source

You need:
- JDK 17
- The Android SDK with platform 36 (Android Studio Narwhal or newer installs this for you)

```
git clone https://github.com/konkomaji/geotagcamera.git
cd geotagcamera
./gradlew lintDebug testDebugUnitTest assembleDebug
```

The debug APK ends up in `app/build/outputs/apk/debug/`. Minimum SDK is 26 (Android 8.0), target SDK is 36.

**Map thumbnail (optional).** The map needs a Stadia Maps API key. Without one the app builds and runs normally and the stamp simply leaves the map out. To enable it, get a free key from [Stadia Maps](https://stadiamaps.com/) and add this line to `local.properties` in the project root (the file is gitignored):

```
stadiaMaps.apiKey=YOUR_KEY_HERE
```

The key is compiled into the APK, and anyone who has the APK can pull it out. Restrict it in the Stadia dashboard and don't publish an APK built with a key you care about. The APKs on GitHub Releases are built without a key, so the map thumbnail is not available in them.

## Contributing

Issues and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first, and if you're planning a larger change, open an issue so we can talk through the approach before you put the work in. Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

Found a security problem, such as a way to make an edited photo verify or a location leak? Please don't open a public issue. See [SECURITY.md](SECURITY.md).

Release history is in [CHANGELOG.md](CHANGELOG.md). Fonts, map data and libraries used by the app are credited in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## License

GeoTag Camera is licensed under the [GNU General Public License v3.0 or later](LICENSE). You're free to use, study, modify and redistribute it under the same terms.

## Author

Built by [Konko Maji](https://github.com/konkomaji).
