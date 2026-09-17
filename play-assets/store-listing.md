# Play Console store listing — copy and paste

Everything below is written to Play's length limits. Paste each block into the
matching field in **Play Console → Grow → Store presence → Main store listing**.

---

## App name (max 30 characters)

```
CatGuard - Cat Deterrent
```

*(24 characters)*

---

## Short description (max 80 characters)

```
Turn an old phone into an AI camera that spots cats and scares them off.
```

*(71 characters)*

---

## Full description (max 4000 characters)

```
CatGuard turns a spare Android phone into a standalone camera that watches for
cats and plays a deterrent sound when one appears.

Point the phone at the area you want protected, press START GUARD, and leave it
on a charger. When a cat enters the area, CatGuard plays a dog bark or another
startling sound, then goes quiet and keeps watching.

EVERYTHING HAPPENS ON YOUR PHONE
The detection model runs entirely on the device. No internet connection is
needed after installation, and there is no account to create. CatGuard does not
even request internet permission, so it cannot upload anything - not video, not
images, not detections.

HOW IT WORKS
• An on-device AI model examines the camera a few times a second
• A cat must appear in several frames in a row before anything happens, which
  keeps false alarms down
• Only cats inside the detection zone you draw will trigger it
• After a sound plays, a cooldown stops it repeating at the same animal

CHOOSE YOUR SOUND
Seven sounds are built in: four real dog barks, plus a vacuum cleaner, thunder
and fireworks. Tick one or tick several and play them together. Save your
favourite mixes as named combinations and switch between them in one tap. You
can also add your own audio file from your phone.

CONTROL THE LOUDNESS
Play on the alarm channel for maximum volume, set how long the sound lasts from
2 seconds to a full minute, and optionally have the phone turn itself up for the
duration and put the volume straight back afterwards. The sound stops early once
the animal has left, so a long setting does not mean a noisy house.

PICK WHAT TO WATCH FOR
Cats by default, but the model recognises 80 different categories - dogs, birds,
people and more - and any of them can be set to trigger the deterrent. Useful for
testing the setup without waiting for a cat.

RUNS FOR DAYS
Designed for unattended use on an old handset: a low, adjustable analysis rate,
automatic slow-down if the phone gets warm, bounded storage, and a persistent
notification while monitoring is active.

NIGHT USE
Optional low-light capture and a scheduled camera light. CatGuard will also warn
you on screen when the scene is too dark for reliable detection.

DAILY TOTALS
See how many animals were detected today and how many actually triggered a
sound, plus a day-by-day history for the last two weeks.

WHAT TO EXPECT
CatGuard is a camera app, not magic. It needs visible light - if you cannot
clearly see a cat in the preview, neither can the app. Detection can also be
missed in heavy shadow, at long range, or when an animal is mostly hidden.
Leaving a lamp on in the area makes a large difference at night.

Android only allows background camera access to be started while an app is on
screen, so monitoring must be started by pressing the button in the app. It then
continues with the screen off.

The deterrent is a startle sound at a volume you control. It is not designed to
harm animals, and the volume is always restored to where you left it.
```

---

## App category

- **Category:** Tools
  *(House & Home is also defensible; Tools fits the utility framing better.)*
- **Tags:** suggest *Utilities*, *Home automation*

## Contact details

- **Email:** muzamilkhalid54321@gmail.com
- **Website:** optional
- **Phone:** optional

## Privacy policy URL

Required. Host `PRIVACY-POLICY.md` somewhere public and paste the URL. See
`docs/publishing-to-play.md` for the quickest free way to do that.

---

## Data safety form answers

Play asks this separately from the listing. For CatGuard every answer is the
simple one:

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | n/a — nothing is transmitted |
| Do you provide a way for users to request that their data is deleted? | n/a — nothing is collected. History is deletable in-app and removed on uninstall. |

If the form insists on a justification, the substance is: the camera feed is
processed in memory on the device and discarded; settings and a detection log
stay in app-private storage; the app has no `INTERNET` permission.

---

## Content rating questionnaire

Answer honestly; expect **Everyone / PEGI 3**.

- No violence, sexual content, profanity, drugs, gambling
- No user-generated content, no sharing, no chat
- No location sharing, no personal information collection
- **Does the app contain ads?** No
- **Does the app have in-app purchases?** No

---

## Sensitive permission declarations

Play will require written justification for two things.

**Camera permission:**

```
CatGuard is a camera-based animal deterrent. The camera feed is analysed on the
device by a bundled TensorFlow Lite model to detect when a cat has entered a
user-defined area, so the app can play a deterrent sound. Frames are processed
in memory and discarded immediately. No image or video is stored or transmitted,
and the app does not request internet permission.
```

**Foreground service type `camera`:**

```
The app monitors the camera continuously while the user has explicitly started
guarding, including when the screen is off. Monitoring is always started by the
user from a visible activity, an ongoing notification is displayed for the whole
session, and the user can stop it from that notification at any time. Camera
access is the core, user-initiated function of the app.
```
