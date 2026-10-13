<picture>
  <source media="(prefers-color-scheme: dark)" srcset="assets/banner-dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="assets/banner-light.svg">
  <img alt="GeoTag Camera" src="assets/banner-light.svg">
</picture>

# GeoTag Camera

No ads. No paywall. We know, it's suspicious.

Free, open source, ad free Android camera app that stamps every photo with GPS location, address and timestamp, and signs it on-device so anyone can later check it hasn't been edited. Capturing, stamping, signing and verifying work with no signal at all. Built for field surveyors, college students and NGO teams who need to show where and when a photo was taken, without paying for it or handing over their data.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://developer.android.com)
[![Build](https://github.com/konkomaji/geotagcamera/actions/workflows/android-ci.yml/badge.svg)](https://github.com/konkomaji/geotagcamera/actions/workflows/android-ci.yml)
[![Release](https://img.shields.io/github/v/release/konkomaji/geotagcamera?include_prereleases&label=release)](https://github.com/konkomaji/geotagcamera/releases)

Website: https://konkomaji.com/geotagcamera/

## Why this exists

Most geotagging camera apps on the Play Store make you sit through ads before you can even open the camera, lock basic stamp fields behind a subscription, ask for permissions that have nothing to do with taking a photo, and stop working the moment you lose signal. GeoTag Camera fixes that, in the open, so anyone can read the code and check it for themselves.

## Features

**Capture**
- Camera built on CameraX that adapts to the phone it runs on: zoom presets come from the real lens range (0.5x only appears when there is an ultra-wide lens), the flash button only appears when the camera has a flash, and the phone maker's own Auto, Night, HDR, Portrait (bokeh) and Retouch modes are offered when the phone provides them through CameraX Extensions
- Tap-to-focus with exposure compensation, pinch zoom, a rule-of-thirds grid, and front/back switch. Front-camera photos match the mirrored preview
- 4:3, 16:9, 1:1 and Full aspect ratios, with a smooth switch between them. The saved photo is cropped to exactly the frame you see on screen, and stays upright however you hold the phone. On phones the camera screen itself stays upright like the stock camera: only the icons and the live stamp turn. Captures are capped at about 12 MP
- A live stamp preview in the viewfinder, drawn by the same code that burns the stamp into the saved photo, so what you frame is what you get
- Two stamp styles. **Bar** (the default) is a full-width strip along the top or bottom edge with every field you have turned on: place name and country code, address with Plus Code, coordinates, date and time with GMT offset, chips for accuracy, altitude, bearing and weather, a map, and your organization logo, name and signature. Every line stays on one line, so the stamp covers about a sixth of a portrait photo. **Minimal** is a small coordinates-and-time label
- Move the stamp by long-pressing and dragging it on the viewfinder. Bar snaps to the top or bottom edge, Minimal to one of nine positions
- GPS position, altitude, accuracy, compass direction, capture time and time-zone offset are also written into the photo's EXIF metadata, so GIS and photo tools pick them up automatically
- Every capture is saved straight to your phone's Photos/Gallery app (Pictures/GeoTagCamera). Nothing leaves your phone unless you choose to share it
- After each shot a review screen lets you Retake (which discards that photo), Share, or finish with Done. It can also close on its own if you turn that on in Settings

**Built to actually work in the field**
- Capturing, stamping, signing and verifying all work with no network connection
- The stamp map is drawn on the phone from OpenStreetMap data, with no API key. Places you have shot in stay available offline, and Settings, Offline maps lets you save a whole area (2 to 30 km) before a trip
- No GPS fix when you press the shutter? The photo is held and saved as soon as a fix arrives, instead of being lost
- Location is only tracked while the camera screen is visible, and a fix older than 30 seconds is never used for a photo
- Addresses come from the phone's built-in Geocoder. With no signal, the stamp falls back to the nearest address already cached on the phone. The Plus Code is worked out on the device, so it is always there
- Only camera and location are requested at runtime on Android 10+; Android 9 and older also needs storage access to save into the gallery

**Gallery**
- Photos grouped by day, with a badge showing whether each one still verifies or has been edited
- Select several photos to share or delete them together. Android shows its own confirmation prompt where it requires one
- The photo detail screen can share, delete, open the location in a maps app, copy the coordinates and verify the photo again

**Made for your organization**
- Pick **Field** (students, NGO and field staff: simple and quick) or **Professional** (surveyors and inspectors: altitude, bearing, required signature, every setting) on first launch. You can switch in Settings any time
- Choose exactly which fields show on the stamp, with a live preview in Settings
- Add your college, company or project name and logo to the stamp, free, no paywall

**Projects, exports and reports**
- Shoot into a project (a site, job or assignment) picked under the viewfinder; filter the gallery by project and move photos between projects
- A sticky note on the stamp (for example "Pole 12, north wall") and an optional fixed custom line (contract number, inspector)
- Export the locations of a project or a selection as CSV, KML or GeoJSON, with time, place, note and each photo's SHA-256
- Build a PDF site report: overview map with numbered pins, an index, and two photos per page with their details and a fresh verification result

**Trust and verification**
- Every photo is hashed with SHA-256 and signed on-device with a key kept in the Android Keystore. The signature also covers the capture latitude, longitude and time, so rewriting the photo's GPS or time EXIF fails verification. See [How photo verification works](#how-photo-verification-works) for exactly what that does and does not prove
- Verify any photo that carries a GeoTag Camera proof, including one someone else sent you, from the "Verify a photo" screen or via Share → Verify with GeoTag Camera. The result shows the signing key's fingerprint and whether it is this phone's key
- Field workers can add a hand-drawn signature onto the photo before saving, useful for inspection reports and muster-roll style documentation. Settings can make the signature required

**Optional extra, off by default**
- A current-weather chip, using Open-Meteo. It needs an internet connection and a capture never waits on it.

**No ads, no tracking, ever**
- No ad SDKs, no analytics SDKs, no crash reporters
- No account, no cloud sync, and no server of ours collecting anything, because there is no server

## Tech stack

- Kotlin, Jetpack Compose, Material 3 and Material icons
- CameraX for capture, with CameraX Extensions for the phone maker's camera modes
- Android's own location services (no Google Play services)
- Android's built-in Geocoder for reverse geocoding
- Room and DataStore for local storage
- Android Keystore for on-device photo signing
- Coil for gallery thumbnails
- MapLibre Native with OpenFreeMap vector tiles for the on-device map and offline areas

## Project status

Version 1.4.0 is the first full release. It adds projects, notes on the stamp, CSV/KML/GeoJSON export and PDF site reports, drops Google Play services, and includes in-app legal pages and problem reporting. Please [report what you find](https://github.com/konkomaji/geotagcamera/issues), or use Report a problem in the app. See [CHANGELOG.md](CHANGELOG.md) for the full list.

## Install

1. Download the APK for your phone from the [latest release](https://github.com/konkomaji/geotagcamera/releases/latest). Most phones from the last several years want `geotagcamera-v1.4.0-arm64-v8a.apk`; older or budget phones may need `armeabi-v7a`. Not sure? Take `geotagcamera-v1.4.0-universal.apk`, which works everywhere but is bigger.
2. Optional: check the download against the SHA-256 listed on the release page.
3. Open the APK on your phone. Android asks you to allow installs from your browser or file manager the first time; allow it for that app only.
4. Open GeoTag Camera and allow camera and location when asked.

Coming from 1.3.0 or older? Those builds were signed with a development key, so **uninstall the old version first**, then install 1.4.0. Photos already in your gallery stay there and still verify. From 1.4.0 on, every release is signed with the same permanent key and updates in place. Android 8.0 or newer is required; Google Play services are not needed.

## Using the app

1. **Frame the photo.** The live stamp on the viewfinder is exactly what will be burned into the photo. Pick a ratio (4:3, 16:9, 1:1 or Full) from the top bar.
2. **Move the stamp if you need to.** Long-press it and drag. Bar snaps to the top or bottom edge, Minimal to any of nine spots.
3. **Take the photo.** Wait for "Location locked" in the corner of the frame; it shows the GPS accuracy.
4. **Review.** Share it straight away, keep it with Done, or Retake (which deletes that photo).
5. **Projects and notes.** Tap the row under the viewfinder to pick or create a project and set a note for the stamp. Both stay until you change them.
6. **Later:** the Gallery tab shows every photo with a verified badge, filtered by project. Open one to share it, delete it, open its location in a maps app or copy the coordinates. The menu exports locations (CSV, KML, GeoJSON) or builds a PDF report of what is shown or selected.
7. **Check a photo someone sent you:** Settings, then Verify a photo, or share the photo to GeoTag Camera from any app.

Settings is where you switch between Field and Professional, choose the stamp style and fields, add your organization's name and logo, save areas for offline maps, and turn on the optional weather.

## Roadmap

**Next**
- Quick-capture shortcut from the home screen
- Batch mode with auto-numbered notes
- Proof pack: photos, a manifest and signatures in one zip
- A web page that verifies a photo without the app
- Before and after photo pairing
- Hindi and Bengali

**Later**
- Video geotagging
- Track log and distance/area tool
- Voice notes attached to photos
- Before and after photo pairing
- Home screen widget
- Biometric app lock
- Manual local backup and restore
- Material You dynamic theming

## How photo verification works

When you take a photo, the app:

1. Renders the stamp onto the image and saves it as a JPEG.
2. Computes a SHA-256 hash over a canonical view of that JPEG: the image data itself (quantization and Huffman tables, frame header and the compressed scan), with every APPn metadata segment left out. That is where EXIF and XMP live, so the proof can be written back into the file without changing the hash. See [`JpegCanonical.kt`](app/src/main/java/com/geotagcamera/geotagginglocationonphoto/exif/JpegCanonical.kt).
3. Signs that hash together with the capture latitude, longitude and time using an ECDSA (P-256, SHA256withECDSA) key generated on the device in the Android Keystore. The private key can't be exported from the Keystore. On most phones it is held in secure hardware, but that depends on the device.
4. Embeds the hash, the signed latitude/longitude/time, the signature and the matching public key in the photo's EXIF UserComment, mirrored in an XMP segment, and writes the same latitude, longitude and time into the normal EXIF GPS fields.

The verification result also shows a short fingerprint of the signing key (four groups of four hex characters) and says whether it is this phone's own key. Comparing that fingerprint with one you got from the photographer is the simple way to check who signed a photo.

Verifying recomputes the canonical hash from the file, checks that it matches the embedded hash, checks the signature against the embedded public key, and checks that the photo's EXIF GPS latitude, longitude and time still match what was signed. No network and no account are involved, so any copy of the app can check any photo.

**What a "verified" result tells you:** the image pixels (which include the burned-in stamp) and the EXIF GPS latitude, longitude and time have not changed since they were signed by the key whose public half is embedded in the file.

If the image matches but the copy you have carries no readable GPS or time metadata (sharing apps often strip it), the result is "Image intact, location not checked" rather than a pass. Photos taken with version 1.1.0 carry the older proof format, which covers the pixels only.

**What it does not tell you:**
- **Who signed it, or on which device.** The public key travels inside the photo and is not tied to a person, a phone, or a certificate authority. Someone who edits a photo can strip the proof and re-sign the edited version with a key of their own, and it will verify. If you need to know that a photo came from a particular person, get their public key from them through a channel you trust and compare it.
- **That the location or time is true.** The app records what the phone reported. A spoofed GPS fix or a wrong system clock will be stamped and signed like any other.
- **The rest of the metadata.** Only latitude, longitude and capture time are signed. Other EXIF fields (altitude, accuracy, direction, camera details) and the XMP segment are outside the proof and can be changed without breaking verification.

Re-encoding the photo, for example when a messaging app compresses it, changes the pixels and the photo will no longer verify, even though nobody meant to tamper with it. Share the original file when verification matters.

## Privacy

Short version: everything stays on your phone unless you share it or turn on one of the optional online features. The full policy is in [PRIVACY.md](PRIVACY.md).

**Stored on your device only**
- Your photos, in Pictures/GeoTagCamera
- A small database of your captures (file location, time, coordinates, altitude, accuracy, bearing, address, organization label, hash and signature)
- A cache of addresses you have looked up, a cache of map tiles (up to about 150 MB) and any areas you saved in Offline maps
- Your settings and, if you upload one, your organization logo
- The signing key, inside the Android Keystore

Android backup is turned off for the app, and its data is also excluded from cloud backup and device-to-device transfer, so your location history is not copied off the phone that way. "Delete all data" on the About & legal screen removes the app's database, settings, logo and caches. Photos you already saved to the gallery stay there until you delete them.

**What leaves your device, and when**
- **Stamp map (on by default, can be turned off):** the map is drawn on your phone from OpenStreetMap vector tiles downloaded from OpenFreeMap. No API key and no account. Each request names a tile by its x, y and zoom numbers, which corresponds to an area a few kilometres across around you, and OpenFreeMap also sees your IP address. Tiles are kept on the phone (up to about 150 MB) and areas you save in Offline maps are stored until you delete them, so places you have already been need no network at all.
- **Weather (off by default):** the app sends your latitude and longitude to Open-Meteo to get the current temperature and conditions, rounded to two decimal places (about 1 km). Open-Meteo also sees your IP address.
- **Address lookup:** the app uses the phone's built-in Android Geocoder. On most phones that service is provided by Google Play services and needs a network connection, so the coordinates you look up go to Google under Google's terms, not ours. Results are cached on the device.
- **Location:** GPS fixes come from Android's own location services (no Google Play services), which may use Wi-Fi and cell data depending on your phone's location settings. The app only asks for location while the camera screen is visible.
- **When you share a photo:** the file carries its EXIF data, including the exact GPS coordinates, altitude and time, as well as the burned-in stamp and the verification proof. Whoever receives the file can read all of it.

There are no ads, analytics, crash reporting or tracking SDKs in the app.

## Permissions

| Permission | Why it's needed |
|---|---|
| Camera | To take the photo |
| Location (fine and coarse) | To read GPS coordinates for the stamp and EXIF data. Only while the camera screen is visible, never in the background. If you grant approximate location only, the camera still works |
| Internet, network state | For the stamp map and offline map downloads (OpenFreeMap), and the optional weather chip (Open-Meteo). Network state lets the app warn before a big download on mobile data. Android's Geocoder may also use the network on its own |
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

The debug APK ends up in `app/build/outputs/apk/debug/`. Minimum SDK is 26 (Android 8.0), target SDK is 36. The build uses Gradle 8.14 through the wrapper, Android Gradle Plugin 8.13 and Kotlin 2.3.

No API keys are needed: the map uses OpenFreeMap, which has none. The build produces one APK per CPU type plus a universal one.

## Contributing

Issues and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first, and if you're planning a larger change, open an issue so we can talk through the approach before you put the work in. Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

Found a security problem, such as a way to make an edited photo verify or a location leak? Please don't open a public issue. See [SECURITY.md](SECURITY.md).

Release history is in [CHANGELOG.md](CHANGELOG.md). Fonts, map data and libraries used by the app are credited in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## License

GeoTag Camera is licensed under the [GNU General Public License v3.0 or later](LICENSE). You're free to use, study, modify and redistribute it under the same terms.

## Author

Built by [Konko Maji](https://github.com/konkomaji).
