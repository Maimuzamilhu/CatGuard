# CatGuard — Privacy Policy

**Last updated: 17 September 2026**

CatGuard is an offline camera app that detects cats and plays a deterrent sound.
This policy explains what it does and does not do with your information.

**Short version: CatGuard collects nothing, sends nothing, and has no way to.**

---

## What CatGuard does not collect

CatGuard does **not** collect, transmit, sell or share any personal data. In
particular it does not collect:

- photos, video or any camera imagery
- audio recordings
- your location
- contacts, messages, files or phone identifiers
- advertising identifiers
- crash or analytics data
- any account or login details — there are no accounts

There is no server. There is no cloud component. There is no third-party
analytics, advertising or tracking SDK in the app.

## Why the app cannot send anything

CatGuard does not request the Android `INTERNET` permission. Without that
permission, Android will not allow the app to open a network connection at all.
This is a technical guarantee enforced by the operating system, not merely a
promise in a document. You can verify it yourself in Android's app info screen,
or by inspecting the app's manifest.

## What the camera is used for

The camera feed is analysed **entirely on your device**, in memory, by a
detection model bundled inside the app.

- Frames are examined and immediately discarded.
- No frame is ever saved to storage, and none is ever transmitted.
- The app writes no images or video anywhere, at any time.

## What is stored on your device

Two small files, in the app's own private storage area:

1. **Settings** — your preferences: sensitivity, cooldown, which animals to
   watch for, sound choices, detection zone.
2. **Encounter history** — for each confirmed detection: the time, which animal,
   the confidence score, and whether a sound played. No images. Limited to the
   most recent 500 entries and automatically deleted after 14 days.

Optionally, if you use **Add your own sound**, the audio file you choose is
copied into the same private storage area so it keeps working reliably.

None of this leaves your phone. Other apps cannot read it. All of it is deleted
when you uninstall CatGuard. You can also erase the history at any time from
**History → Clear**.

Automatic cloud backup is disabled for this app (`allowBackup="false"`), so your
history is not swept into a Google backup.

## Permissions and why each is needed

| Permission | Why |
| --- | --- |
| `CAMERA` | The entire function of the app — watching for animals. Analysed on-device only. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA` | Lets monitoring keep running with the screen off. Android requires apps to declare the reason for background camera use; ours is camera monitoring. |
| `POST_NOTIFICATIONS` | Shows the ongoing "Monitoring for cats" notification so it is always visible when the camera is active. Declining it only hides the notification. |
| `WAKE_LOCK` | Keeps the processor running with the screen off. Without it, detection stops shortly after the display turns off. |

CatGuard requests **no** internet, location, storage, contacts, microphone or
phone-state permission.

Adding your own deterrent sound uses Android's system file picker, which grants
access to the single file you select and nothing else — CatGuard never receives
broad storage access.

## Children

CatGuard is a household utility with no content directed at children, no data
collection, no advertising and no in-app purchases.

## Third-party components

The app includes open-source software components (AndroidX, Jetpack Compose,
CameraX and TensorFlow Lite) and audio clips licensed from Wikimedia Commons.
All run locally. None of them transmit data from this app, and none receive any
information about you. Attribution is shown in the app under
**Settings → Privacy → Sound credits and licences**.

## Changes to this policy

If this policy changes, the updated version will be published at this address and
the date at the top will change. Any future version that collects data would
require a new app permission, which Android would show you before granting.

## Contact

Questions about this policy or the app:

**muzamilkhalid54321@gmail.com**
