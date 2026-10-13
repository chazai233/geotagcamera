# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows [Semantic Versioning](https://semver.org/).

## [1.3.0] - 2026-10-13

A redesign release: a compact stamp, a map that works without a key and offline, a calmer camera, and a choice between a simple and a professional setup. Still a preview: not yet tested on a range of real phones.

### Changed
- **Compact stamp.** Bar is now an inset card that takes about a sixth of a portrait photo instead of more than a third. Every line is a single line (a long address is shortened with an ellipsis instead of wrapping), latitude and longitude share a line, and accuracy, altitude, bearing and weather share the last line with the SIGNED and GEOTAG marks. The map sits beside the card at the card's height.
- The field worker's signature now sits inside the stamp card next to the logo, instead of a large box over the photo.
- **Camera layout.** On phones the camera screen stays upright like the stock camera app: rotating the phone turns only the icons and the live stamp, and the stamp previews along the edge that will be the bottom of the saved photo. Tablets and unfolded foldables keep the side-rail layout.
- Changing the aspect ratio now animates the frame to its new shape and keeps the last frame on screen while the camera switches, instead of flashing black.
- Design system: colours, spacing, corner radii and motion now come from one set of tokens, the two different "verified" greens are one rule, and button and tab labels use Poppins (they were showing in the monospace font by mistake).
- APKs are split per CPU type (arm64-v8a for most phones, armeabi-v7a for older ones, x86_64 for emulators and Chromebooks) plus a universal APK. A typical phone download is about 15 MB.

### Added
- **Map without an API key.** The stamp map is drawn on the phone from OpenStreetMap vector tiles (OpenFreeMap, rendered by MapLibre). It is on by default and now works in the published APKs. Places you shoot in stay available offline.
- **Offline maps** (Settings, More): save a 2, 5, 15 or 30 km area before going somewhere without signal. Shows the expected size, warns before using mobile data, can be paused, resumed and deleted, and resumes by itself if the app was closed mid-download.
- **Field and Professional profiles**, chosen on first launch and changeable in Settings. Field keeps things simple; Professional turns on altitude, accuracy and bearing on the stamp and a required signature, and shows every setting.
- No GPS fix when you press the shutter no longer loses the photo: it is held and saved as soon as a fix arrives (within 60 seconds), or you can keep waiting or discard it.
- Clear messages when storage is almost full or another app takes the camera.

### Fixed
- The location chip no longer keeps saying "Location locked" after the fix goes stale or location is switched off.

### Removed
- Stadia Maps and its build-time API key.

## [1.2.0] - 2026-10-12

A robustness and usability release. Still a preview: not yet tested on a range of real phones.

### Security
- Location and time signing: the signature now also commits to the capture latitude, longitude and time, and verification checks them against the photo's EXIF. Previously the hash ignored all metadata, so EXIF GPS/time could be rewritten without failing verification. Photos taken with 1.1.0 (older proof format) still verify, but only their pixels are covered.
- A photo whose image matches its proof but whose GPS/time metadata is missing (stripped by a sharing app) now shows "Image intact, location not checked" instead of passing.
- Verification wording makes clear that a pass means "unchanged since signing", not proof of who held the phone.

### Changed
- Bar is now the default stamp, showing every enabled field (place, country, address with Plus Code, coordinates, date, time and GMT offset, accuracy/altitude/bearing/weather chips, map, logo, org name, signed mark) and reflowing as fields are turned on or off. The Card layout is removed; Bar and Minimal remain.
- The stamp is moved only by holding and dragging it on the viewfinder. Bar snaps to the top or bottom edge, Minimal to nine positions.
- Every screen is edge-to-edge and padded by the real system bar, cutout and keyboard insets; nothing sits under the status or navigation bar any more.
- All emoji and symbol glyphs replaced with Material icons. Title bars, back arrows, grouped Settings with a live stamp preview.
- The camera adapts to the phone: zoom presets from the real lens range (0.5x only with an ultra-wide), flash only when the camera has one, the phone maker's Auto/Night/HDR/Portrait/Retouch modes when available, exposure compensation after tap-to-focus, and a new Full ratio.
- The saved photo is cropped by CameraX to exactly the on-screen frame for every ratio, and stays upright in any orientation, with or without auto-rotate. Front-camera photos match the mirrored preview.
- The stamp scales by the photo's shorter side, so it covers the same share of portrait and landscape photos.
- Toolchain: compile/target SDK 36, AGP 8.13, Kotlin 2.3, Gradle 8.14, all dependencies updated, version catalog.

### Added
- Delete photos from Gallery (multi-select) and Photo Detail, with the system consent prompt where Android requires it. Retake now discards the photo it replaces.
- Photo Detail: open the location in a maps app, copy coordinates, verify again.
- Verification shows the signing key's fingerprint and whether it is this device's key.
- EXIF: orientation, time-zone offset, GPS accuracy and direction, software tag.
- Gallery groups photos by day and drops entries whose file was deleted elsewhere.
- Open-source project files: CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, PRIVACY, THIRD_PARTY_NOTICES, issue/PR templates, dependabot, release workflow, F-Droid metadata.

### Fixed
- Crash when saving a photo on high-resolution cameras (several full-size bitmap copies, no error handling). Capture is capped at about 12 MP, draws in place, and any failure shows a message instead of crashing.
- Crash or hang when changing stamp settings with a large organisation logo; logos are now downscaled on pick and on load.
- Every Capture to Settings round trip stacked another camera screen; tabs now share one back stack.
- The photo was stamped with the location from when the camera opened. Location now streams while the camera is visible and a fix older than 30 seconds is never used.
- The capture time was taken after the signature sheet closed instead of at the shutter press.
- Capture could hang forever offline on Android 13+ (geocoder error never reported).
- The map was sent your location by default despite being described as off by default. It is now off by default.
- Weather lookups send the position rounded to about 1 km.
- A required signature could be skipped by swiping the sheet away.
- Approximate-only location permission blocked the camera; a permanently denied permission now links to system Settings.
- Two photos in the same minute could overwrite each other on Android 8 and 9.
- Rotating the screen re-opened Verify for a shared photo; share intents with file:// or this app's own URIs are rejected.
- Typing in the organisation name could drop characters; settings writes are now atomic.
- Location history is excluded from cloud backup and device-to-device transfer.

## [1.1.0] - 2026-07-31

A full UI rebuild against a new design system, plus portable verification.

### Added
- Live WYSIWYG viewfinder: the stamp overlay is drawn by the exact same code that burns into the saved photo, so what you frame is what you get. Long-press and drag the stamp to snap it across nine anchor positions.
- Camera controls: pinch and rail zoom, tap-to-focus, flash auto/on/off, front/back switch, aspect ratio 4:3 / 16:9 / 1:1 (a true center crop), and a rule-of-thirds grid.
- Portable photo verification: capture now embeds the hash, signature and public key in EXIF (mirrored in XMP), so any device with the app can verify any photo, offline, even one it never took. New "Verify a photo" screen and an Android share target ("Share → Verify with GeoTag Camera").
- Post-capture review screen with Share as the primary action, an address-derived filename, and a SIGNED · SHA-256 chip.
- Optional OpenStreetMap map thumbnail (via Stadia Maps) and current-weather chip (via Open-Meteo). Both are off by default, on demand, cached, and never block a capture.
- Redesigned stamp: Card / Bar / Minimal templates, country chip, Plus Code, GMT offset, altitude / accuracy / bearing chips, org label and logo.
- Gallery rebuild with per-photo verified/tampered dots, long-press multi-select batch share, and a new Photo Detail screen that reads metadata from live EXIF.
- Restructured Settings: template and position pickers, four surfaced stamp fields with the rest behind "All 14 fields", org logo upload, and capture defaults.
- Launch screen, first-run permission primer, and an About & legal screen (privacy/data-safety copy, licenses, and a type-to-confirm delete-all-data path).
- New brand mark (lens-frame-and-aperture-dot), Poppins + Roboto Mono typography, and a new accent palette.
- Landscape support: orientation unlocked, with rotation-aware layouts for the capture and review screens.

### Changed
- The app now declares the INTERNET permission for the first time, used only by the optional map-tile and weather features. The core capture flow remains fully offline.
- Verification is no longer limited to the capturing device.

### Notes
- Building from source: the map thumbnail needs your own free Stadia Maps API key in `local.properties` as `stadiaMaps.apiKey`. Without it, the stamp reflows without the map.

## [1.0.0] - 2026-07-31

### Added
- Project scaffold: Gradle build, package structure, adaptive app icon
- Room database for captured photos and offline geocode cache
- Fresh-fix location provider on top of Fused Location Provider
- Offline-first reverse geocoding using the device Geocoder with a local cache fallback
- User-toggleable stamp fields (coordinates, address, timestamp, altitude, accuracy, bearing) with DataStore-backed preferences
- Photo stamp renderer (coordinates, address, timestamp, custom organization label)
- EXIF GPS and timestamp writer
- Tamper-evident photo signing: SHA-256 hash signed with an Android Keystore key generated on-device
- Project website with feature showcase, FAQ and SEO/AEO metadata, deployed via GitHub Pages
- Capture screen: CameraX preview, runtime permission gate, shutter-to-saved-row pipeline (fresh GPS fix, reverse geocode, stamp render, EXIF write, keystore sign)
- Captures publish to the device's real Photos/Gallery app via MediaStore (Pictures/GeoTagCamera) instead of an app-private folder
- Gallery screen: grid of captures with a detail dialog for date, coordinates, address and tamper-check verification
- Settings screen: per-field stamp toggles and organization label, backed by DataStore
- Bottom-nav shell wiring capture, gallery and settings
- Field-worker signature overlay: optional draw-to-sign pad shown after capture, burned into the photo before EXIF write and keystore signing
