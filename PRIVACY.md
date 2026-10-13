# Privacy Policy

**App:** GeoTag Camera (`com.geotagcamera.geotagginglocationonphoto`)
**Developer:** Konko Maji, https://github.com/konkomaji
**Last updated:** 14 October 2026

GeoTag Camera is a camera app that puts the location and time on your photos. This policy explains, in plain terms, what the app does with your data. The short version: we don't collect anything. There's no account, no server of ours, no ads and no analytics. Your photos and their locations stay on your phone unless you share them or turn on one of the optional online features described below.

## What the app stores on your phone

All of this stays on your device:

- **Your photos.** Saved to your gallery in Pictures/GeoTagCamera. They're ordinary files you can view, share or delete with any gallery app.
- **A list of your captures.** For each photo: where the file is, when it was taken, the coordinates, altitude, accuracy, compass bearing, address, your organization label, and the hash and signature used for verification.
- **An address cache.** Addresses you've already looked up, so with no signal the stamp can use the nearest address already on the phone.
- **Map tiles**: a cache of up to about 150 MB, plus any areas you saved in Offline maps.
- **Your settings**, and your organization logo if you uploaded one.
- **A signing key**, created on your phone and kept in the Android Keystore. The private part can't be exported.

Android backup is turned off for this app, and the app's data is also excluded from cloud backup and device-to-device transfer, so none of this (including the location history in your capture list and address cache) goes into a Google or device-maker cloud backup or gets copied to a new phone.

## What leaves your phone

Nothing is sent to the developer. The only network traffic comes from the features below.

### Stamp map (on by default, can be turned off)

The map on the stamp is drawn on your phone from OpenStreetMap vector tiles downloaded from **OpenFreeMap** (no account, no API key). Each request names a tile by its x, y and zoom numbers. Those correspond to an area a few kilometres across around your location, so OpenFreeMap can work out roughly where you are, and it also sees your IP address. See [openfreemap.org](https://openfreemap.org/).

Tiles are cached on your phone (up to about 150 MB), so the same place isn't downloaded twice. Areas you save under Settings, Offline maps are downloaded in one go and kept until you delete them. Turn the map off in Settings, Stamp fields to stop all map requests.

### Weather (off by default)

The weather chip is off until you turn it on in Settings. If you turn it on, the app sends your latitude and longitude to **Open-Meteo** to get the current temperature and conditions. The coordinates are rounded to two decimal places before they are sent, which is roughly 1 km, so Open-Meteo learns your approximate area but not your exact spot. Open-Meteo also sees your IP address. See the [Open-Meteo terms and privacy information](https://open-meteo.com/en/terms).

### Address lookup

To turn coordinates into an address, the app uses the geocoder built into your phone's Android system. The app does not choose who provides it: on most phones this service is provided by Google Play services and needs an internet connection, which means the coordinates being looked up are sent to Google and handled under Google's privacy policy. The app caches the result so it doesn't need to ask again for the same place. If no geocoder is available or there's no connection, the app uses the nearest cached address or leaves the address off. The Plus Code on the stamp is calculated on the phone and never needs the network.

### Location

The app gets your location from Google Play services (the Fused Location Provider). Depending on your phone's location settings, Google Play services may use Wi-Fi and mobile network information to improve accuracy. That is controlled by your phone's settings, not by this app. GeoTag Camera only receives location updates while the camera screen is open and visible. It stops as soon as you leave the camera screen or the app, and it never uses location in the background.

### When you share a photo

When you share a photo, the file you send contains:
- the stamp burned into the image (whatever fields you chose to show),
- EXIF metadata with the exact GPS coordinates, altitude, accuracy, compass direction, time and time-zone offset,
- the verification proof (a hash, a signature and a public key).

Anyone who receives the file can read all of this. If you don't want to share your precise location, don't share the original file.

### Problem reports (only when you send one)

Report a problem (in About & legal) opens your own email app with a message addressed to konkomaji@gmail.com. Nothing is sent until you press send there. The draft contains what you typed and, if you leave the box ticked, basic device facts: app version, Android version, phone model, CPU type and screen size. It never includes photos, locations or your capture list. If you choose "Attach app log", a short log of the app's own recent messages is attached; you can read it before sending.

If the app crashes, the crash details are kept on the phone only. The next time you open the app it offers to send them the same way; if you decline, they are deleted.

## What we don't do

- No ads.
- No analytics, telemetry or automatic crash reporting. Crash details are only sent if you choose to email them.
- No account or sign-in.
- No selling or sharing of data, because we don't have any.
- No background location. Location is only used while the camera screen is visible.

## Permissions

- **Camera:** to take photos.
- **Location:** to stamp and tag photos with where they were taken, only while the camera screen is visible.
- **Internet and network state:** only for the stamp map, offline map downloads and the optional weather feature above (and the Android geocoder, which may use it on its own). Network state is used to warn you before a large download on mobile data.
- **Photo location (Android 10 and newer):** so Verify can read the GPS data inside a photo you pick and check it against the signature. Without it Android hides that data from the app.
- **Storage (Android 9 and older only):** to save photos to the gallery.

## Deleting your data

- The **Delete all data** option on the About & legal screen removes the app's capture list, address cache, map tile cache, settings and organization logo. Saved offline map areas are deleted from Settings, Offline maps.
- Uninstalling the app removes all of the above and the signing key.
- Photos already saved to your gallery are yours. They stay until you delete them yourself.

Because we never receive your data, there's nothing for us to delete on our side.

## Children

The app doesn't collect personal data from anyone, including children.

## Changes to this policy

If the app's behaviour changes in a way that affects your privacy, this file will be updated in the same release and the change will be noted in the [changelog](CHANGELOG.md). The history of this file is public in the repository.

## Contact

Questions about this policy: email konkomaji@gmail.com, or open an issue at https://github.com/konkomaji/geotagcamera/issues. For anything you'd rather not post publicly, use a [private security advisory](https://github.com/konkomaji/geotagcamera/security/advisories/new).
