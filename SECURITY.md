# Security Policy

## Supported versions

Only the latest release gets security fixes. If you're on an older version, update first and check whether the problem still happens.

| Version | Supported |
|---|---|
| Latest release (currently 1.2.x, a pre-release) | Yes |
| Anything older | No |

## Reporting a vulnerability

Please report security problems privately through GitHub's private vulnerability reporting:

**[Report a vulnerability](https://github.com/konkomaji/geotagcamera/security/advisories/new)** (Security tab → Advisories → Report a vulnerability)

Don't open a public issue or pull request for a security problem until a fix is out.

A useful report includes:
- The app version and Android version you tested on
- What you did, step by step, and what happened
- A sample file or proof of concept if you have one (for example a photo that verifies when it shouldn't)
- What you think the impact is

This is maintained by one person in their spare time, so there is no bug bounty and no guaranteed response time. I aim to acknowledge reports within a week and will keep you updated in the advisory. Once a fix is released, the advisory is published and you're credited unless you'd rather not be.

## In scope

- **Signature and verification bypass.** Any way to make an edited image show as verified, to make an unedited signed image fail in a way that could be abused, or to crash or confuse the verifier with a crafted file. Before reporting, read [How photo verification works](README.md#how-photo-verification-works): stripping the proof and re-signing with your own key is a documented limit, not a bug (the key fingerprint shown on verification is there so people can spot it), and so is editing EXIF fields other than the signed latitude, longitude and time (for example altitude or accuracy), which are outside the proof. A way to change the EXIF GPS latitude, longitude or time and still get a pass is in scope.
- **Location and EXIF leakage.** Location, address or other personal data leaving the device in a way that isn't documented in [PRIVACY.md](PRIVACY.md) (for example the map or weather making requests while switched off, weather requests carrying more than the rounded position, or location being read when the camera screen isn't visible), or being written into the stamp when the user turned that field off.
- **Share intent handling.** The app accepts `image/*` through Android's share sheet for verification. Crafted intents or files that make the app read files it shouldn't, crash, hang, or leak data are in scope.
- Anything else that lets another app or a remote party read the app's private data or the signing key.

## Out of scope

- Spoofed GPS or a wrong device clock being stamped. The app records what the phone reports.
- Attacks that need a rooted or already compromised device.
- Issues in Android, Google Play services, MapLibre, OpenFreeMap or Open-Meteo themselves. Report those upstream.
