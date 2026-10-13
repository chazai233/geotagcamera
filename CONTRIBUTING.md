# Contributing to GeoTag Camera

Thanks for wanting to help. This is a small project, so the process is light, but there are a few rules that keep the app what it is.

## Before you start

- For bug fixes and small improvements, go ahead and open a pull request.
- For anything bigger (a new screen, a new dependency, a change to how photos are signed or verified), open an issue first so we can agree on the approach before you spend time on it.
- Security problems go through [SECURITY.md](SECURITY.md), not public issues.
- Everyone taking part follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Setting up

You need JDK 17 and the Android SDK with platform 36. Android Studio Narwhal or newer handles the SDK for you. The project builds with Gradle 8.14 (through the wrapper), Android Gradle Plugin 8.13 and Kotlin 2.3, and all dependency versions live in the version catalog at `gradle/libs.versions.toml`. Minimum SDK is 26, target SDK is 36.

```
git clone https://github.com/konkomaji/geotagcamera.git
cd geotagcamera
./gradlew assembleDebug
```

### Map

The stamp map needs no API key: it draws OpenFreeMap vector tiles with MapLibre (`location/map/`). The emulator needs network access the first time; after that, tiles come from the on-device cache.

## Code style

- Kotlin follows the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) (`kotlin.code.style=official`). The repo's `.editorconfig` sets indentation and a 120 character line limit; Android Studio picks it up automatically.
- Keep functions small and name things for what they do.
- Comments should explain why, not restate what the code says.
- Pure logic (hashing, EXIF encoding, parsing) belongs in plain Kotlin with no Android dependency where possible, so it can be unit tested on the JVM.

## Before you open a pull request

Run these and make sure they pass:

```
./gradlew lintDebug testDebugUnitTest
```

CI runs the same tasks plus `assembleDebug` on every pull request.

If you changed anything a user can see, test it on a real device or emulator, and include a screenshot in the PR. If you touched signing, verification or EXIF handling, add or update unit tests in `app/src/test`.

## Commits and pull requests

- Keep commits small and focused on one change. A PR that fixes a bug and also reformats three unrelated files is hard to review.
- Write commit messages in the imperative ("Fix stamp overflow on small screens"), with a short body if the reason isn't obvious.
- Add a line to the `Unreleased` section of [CHANGELOG.md](CHANGELOG.md) for user-visible changes (create the section if it isn't there).
- Fill in the pull request template.

## Licensing of contributions

GeoTag Camera is licensed under the GNU General Public License v3.0 or later. By submitting a contribution you agree that it is licensed under the same terms (inbound = outbound). There is no CLA. Only submit code you wrote yourself or that is under a license compatible with GPL-3.0-or-later, and say where any third party code came from.

## What we won't accept

These are not up for negotiation, because they are the reason the app exists:

- No ads or ad SDKs.
- No analytics, telemetry, crash reporting or tracking of any kind, including "anonymous" usage stats.
- No dependencies that only work through Google Play or that make the app unusable without it (for example Firebase, Play Billing, Play Integrity). The existing Fused Location Provider dependency is a known exception we'd like to make optional, not a precedent.
- No new network calls that are on by default. Anything that talks to the internet must be opt-in, must not block capture, and must be documented in [PRIVACY.md](PRIVACY.md).
- No new permissions without a clear reason in the PR and an update to the permissions table in the README.- No closed source or non-free binaries.
