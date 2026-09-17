# Publishing CatGuard to Google Play

What is already done, what you have to do, and the parts that will bite.

---

## Read this first — two decisions you cannot undo

### 1. The package name is permanent

CatGuard is currently `com.catguard`. **Once you publish under a package name it
can never be changed** — not renamed, not reused, not even after deleting the
app. A different name means a different listing with zero installs.

`com.catguard` is a generic name you do not own. Convention is a reverse domain
you control. Consider something like:

```
com.muzzamilkhalid.catguard
```

If you want to change it, say so and I will do it in one pass (it touches the
Gradle config, the manifest and the Kotlin package). **Do it before your first
upload, not after.**

### 2. The signing key is irreplaceable

I generated one at `keystore/catguard-upload.jks`, with its password in
`keystore.properties`. Both are excluded from version control.

**Back both up now, somewhere that is not this PC.** If you lose them you cannot
publish an update to your own app — you would have to start a new listing and
lose every install and review. A copy in your own Google Drive or a password
manager is enough.

When you first upload, enrol in **Play App Signing** (Play offers this and it is
on by default for new apps). Google then holds the real distribution key and your
key becomes only the *upload* key, which Google can reset if you lose it. It
turns a catastrophe into an inconvenience — take it.

---

## What is already built

| Item | Where |
| --- | --- |
| **Release App Bundle** (this is what you upload) | `app/build/outputs/bundle/release/app-release.aab` |
| Release APK (for testing on your own phone first) | `app/build/outputs/apk/release/app-release.apk` |
| Crash-report mapping file | `app/build/outputs/mapping/release/mapping.txt` |
| App icon, 512×512 | `play-assets/icon-512.png` |
| Feature graphic, 1024×500 | `play-assets/feature-graphic.png` |
| Privacy policy | `play-assets/PRIVACY-POLICY.md` |
| Listing text, data-safety and permission answers | `play-assets/store-listing.md` |

Rebuild any time with:

```bat
gradlew.bat bundleRelease assembleRelease
```

The release build is minified and signed automatically, and is about **19 MB** as
a bundle versus 44 MB for the debug APK, because Play splits it per device.

---

## Step 1 — Test the release build before you upload it

**Do not skip this.** The release build is minified by R8, which strips unused
code. That is a different binary from the debug one you have been testing, and
R8 breaking a reflection-loaded class is a classic way to ship an app that
crashes on first launch.

I have verified that the TensorFlow Lite entry points survived minification and
that the model and all seven sounds are inside the bundle — but it has never
been *run*.

```bat
adb install -r app\build\outputs\apk\release\app-release.apk
```

Then check, in this order: the preview appears, Settings shows the model name,
TEST SOUND plays, and START GUARD detects something. If the release build
crashes where the debug one worked, it is a missing ProGuard keep rule — send me
the logcat and it is a quick fix.

Note the release APK has a different signature from the debug one, so uninstall
the debug version first if the install is refused.

---

## Step 2 — Create the Play Console account

<https://play.google.com/console>

- **One-off fee: 25 USD**, paid by card.
- Choose a **personal** account unless you have a registered business.
- Identity verification is required and typically takes 1–3 days. For personal
  accounts you will need government ID, and your legal name and address become
  publicly visible on your app's listing.

**Personal accounts created after November 2023 must also run a closed test with
at least 12 testers who stay opted in for 14 continuous days before you can apply
for production access.** This is the single biggest surprise for new publishers —
budget two weeks and line up twelve people (friends, family, an Android testing
community). Plan for it now rather than discovering it at the end.

---

## Step 3 — Host the privacy policy

A privacy policy URL is **mandatory**, even for an app that collects nothing.

The quickest free route:

1. Create a GitHub account if you do not have one.
2. Make a new public repository, e.g. `catguard-privacy`.
3. Upload `play-assets/PRIVACY-POLICY.md` as `README.md`.
4. Settings → Pages → deploy from `main` branch.
5. Your URL becomes `https://<username>.github.io/catguard-privacy/`.

A Google Doc published to the web, or any free static host, works equally well.
It must be publicly reachable without a login.

---

## Step 4 — Create the app and fill the listing

In Play Console: **Create app** → name, language, App (not Game), Free.

Then work through the tasks Play lists for you:

| Task | Source |
| --- | --- |
| Store listing text | `play-assets/store-listing.md` |
| App icon | `play-assets/icon-512.png` |
| Feature graphic | `play-assets/feature-graphic.png` |
| Phone screenshots | **You must take these** — see below |
| Privacy policy URL | From step 3 |
| Data safety | Answers in `store-listing.md` — everything is "No" |
| Content rating | Questionnaire; expect Everyone / PEGI 3 |
| Target audience | 13+ (avoids the extra child-safety requirements) |
| Ads | No |
| Government app | No |
| Financial features | None |

### Screenshots — the one thing I cannot make for you

Play requires **at least 2** phone screenshots (up to 8), between 320 px and
3840 px on each side. They must be real, so take them on the phone:

1. Set the app up nicely — start the guard, get a detection on screen if you can.
2. Press **Power + Volume Down** to screenshot.
3. Worth capturing: the main screen while monitoring, the main screen with a
   detection box visible, the sound settings with combinations, the "What we can
   detect" list, and the history screen.
4. Transfer them to the PC and upload.

Avoid screenshots with a dark empty preview — they look broken. Point the camera
at something well lit.

---

## Step 5 — Upload and release

1. **Testing → Closed testing → Create new release.**
2. Upload `app-release.aab`.
3. Play will offer **Play App Signing** — accept it.
4. Add release notes (for the first release: "First release of CatGuard.").
5. Add your 12+ testers by email list, and keep them opted in for 14 days.
6. After that, **Production → Create new release**, promote the build, and apply
   for production access.

First review typically takes a few days; it can take longer for a camera app.

---

## What review will most likely ask about

Camera and foreground-service apps get more scrutiny than average. Prepared
answers are in `play-assets/store-listing.md`; the substance is:

- **Camera permission.** Core function; on-device analysis; nothing stored or
  transmitted; no internet permission.
- **`FOREGROUND_SERVICE_CAMERA`.** Always started by the user from a visible
  screen, ongoing notification for the whole session, stoppable from that
  notification.
- **Data safety accuracy.** This is the most common rejection reason across all
  apps — a form that says "no data collected" while the manifest asks for
  internet or location. CatGuard's manifest genuinely asks for neither, so the
  form and the binary agree. Keep it that way.

One thing in your favour: I removed three permissions (`READ_PHONE_STATE`,
`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`) that the TensorFlow Lite
library was silently having Android imply into the manifest. Those would have
been awkward to justify on a form declaring no data collection.

---

## Things to fix before you publish, honestly

Worth knowing rather than finding out from a one-star review:

1. **Nothing has been tested on a wide range of devices.** Camera behaviour with
   the screen off varies a lot between manufacturers. Expect reports from Xiaomi,
   Oppo and Vivo users that monitoring stops; the in-app battery-optimisation
   link helps but does not always solve it.
2. **The package name** — decide before uploading (see the top of this file).
3. **`versionCode` is 1.** Every upload to Play needs a higher `versionCode` than
   the last. Bump it in `app/build.gradle.kts` for each release; Play rejects a
   repeat.
4. **No crash reporting.** You will not find out about crashes except through
   Play's own vitals, which only cover a sample. That is the trade for having no
   analytics SDK, and it is the right trade for a privacy claim this strong —
   just know it is the trade you made.
5. **The app has no tutorial beyond the first-run card.** Most support questions
   will be "it is not loud enough" and "it stopped overnight". Both are answered
   in the app, but consider putting the troubleshooting section of the README in
   the listing description or a support page.

---

## Updating later

```bat
:: 1. bump versionCode (and versionName) in app/build.gradle.kts
:: 2.
gradlew.bat bundleRelease
:: 3. upload the new app-release.aab to a new release in Play Console
```

Keep `keystore/` and `keystore.properties` safe for as long as the app exists.
