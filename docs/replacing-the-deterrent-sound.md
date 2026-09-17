# Deterrent sounds

CatGuard ships with **four real dog recordings** from Wikimedia Commons, in
`app/src/main/res/raw/`:

| File | Length | Character |
| --- | --- | --- |
| `dog_bark_short.ogg` | 2.6 s | One sharp bark |
| `dog_bark_double.ogg` | 4.0 s | Two close barks (the default) |
| `dog_bark_alert.ogg` | 5.4 s | An agitated burst |
| `dog_bark_sustained.ogg` | 12 s | Continuous barking |

All four were loudness-normalised to about −12 LUFS with a −1.5 dBTP ceiling,
downmixed to mono and re-encoded as Ogg Vorbis, so they are consistently loud
through a phone speaker. Licences and attribution are in `CREDITS.md` and in the
app under **Settings → Privacy → Sound credits and licences**.

You can pick one in **Settings → Deterrent sound**, or choose **Rotate through
all sounds** so a different bark plays each time.

---

## Adding your own sound — no rebuild needed

The easiest route is inside the app:

**Settings → Deterrent sound → ADD YOUR OWN SOUND**

This opens the system file picker. Pick an MP3, WAV, OGG or M4A under 30 seconds
and under 10 MB. CatGuard **copies** it into its own private storage, so it keeps
working even if you later move or delete the original, or the file lived on an SD
card you removed. The imported sound appears in the list under **Your sounds**
and is selected automatically. **Remove** deletes the copy.

Imported sounds stay on the phone. They are never uploaded, and they are removed
when you uninstall the app.

---

## Adding a sound to the build instead

To ship a sound inside the APK rather than importing it on the device:

1. Put the file in `app/src/main/res/raw/` with a name made only of lowercase
   letters, digits and underscores, e.g. `dog_bark_mine.ogg`.
2. Add an entry to `SoundLibrary.bundled` in
   `app/src/main/java/com/catguard/deterrent/DeterrentSound.kt`:

   ```kotlin
   DeterrentSound.Bundled(
       id = "bark_mine",
       displayName = "My bark",
       description = "Recorded from next door's dog.",
       durationMs = 3_000,
       resourceName = "dog_bark_mine",   // file name without extension
       credit = "Recorded by me",
   ),
   ```
3. `gradlew.bat assembleDebug`

To replace one of the existing clips, overwrite the file and keep the same name.
Do not have two files called `dog_bark_short` with different extensions — two
resources with the same name collide and the build fails with a
duplicate-resource error.

If a listed sound's file is missing the app does not crash: it logs a warning,
skips that sound, and the status card explains that the selected sound is
unavailable.

---

## Making it louder

In **Settings → Deterrent sound → Loudness**:

- **Volume** — relative level, on top of the system volume.
- **Play it this many times** — repeats the clip back to back, up to 5×.
- **Audio channel** — *Media* follows the normal volume keys. *Alarm* is usually
  louder on Android and is not ducked by other audio, but follows the alarm
  volume slider rather than the media one.
- **Maximise volume when barking** — turns the chosen channel up to full for the
  length of the bark, then puts it straight back where you had it. This is the
  loudest option. It can be blocked by Do Not Disturb policy, in which case the
  bark still plays at the current volume.

The volume is always restored afterwards, and CatGuard never changes it except
during a bark with that setting enabled.

---

## Notes on technique

`SoundPool` decodes the whole clip into memory once and replays it with no
per-trigger allocation, which is why clips are kept short. Only the sounds that
could actually play next are loaded — one, or all of them in rotate mode — so
memory tracks your selection rather than the whole library.
