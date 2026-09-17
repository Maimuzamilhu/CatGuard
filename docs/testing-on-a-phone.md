# Checking CatGuard on a real phone

Nothing in this app has run on hardware yet — it builds and its logic is unit
tested, but the camera, the model, the speaker and the foreground service have
never been exercised on a device. This is the order to check them in, so that
when something fails you know which piece failed.

---

## 0. Get the phone talking to the PC

On the phone:

1. **Settings → About phone** → tap **Build number** seven times. It will say
   "You are now a developer".
2. **Settings → System → Developer options** → turn on **USB debugging**.
3. Plug it into the PC with a **data** cable. Charge-only cables are the single
   most common cause of "no device".
4. Pull down the USB notification and choose **File transfer / MTP**, not
   "Charging this device".
5. A dialog appears on the phone: **Allow USB debugging?** Tick *Always allow*
   and press **Allow**.

Then, from the project folder:

```powershell
powershell -ExecutionPolicy Bypass -File .\check-on-phone.ps1
```

That builds, installs, launches, and starts streaming the app's logs. It also
tells you what to do if the phone is not detected or not authorised.

Useful flags:

```powershell
.\check-on-phone.ps1 -SkipBuild        # reuse the existing APK
.\check-on-phone.ps1 -NoLogs           # do not tail logcat
.\check-on-phone.ps1 -AllDetections    # log every detection, very chatty
```

### If you would rather do it by hand

```bat
D:\android-toolchain\sdk\platform-tools\adb.exe devices
D:\android-toolchain\sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
D:\android-toolchain\sdk\platform-tools\adb.exe logcat -s CatGuard:V CatGuardService:V CatGuardAudio:V AndroidRuntime:E
```

### No PC cable at all

Copy `app\build\outputs\apk\debug\app-debug.apk` to the phone (USB, email to
yourself, cloud drive) and tap it in a file manager. Android will ask to allow
installing unknown apps for that file manager. You lose the logs, so only do this
once the basics work.

---

## 1. Does it open and see anything?

Launch CatGuard. Expect: a camera permission prompt, then a live preview filling
most of the screen.

| Symptom | Likely cause |
| --- | --- |
| App closes immediately | Look for `AndroidRuntime: FATAL` in the logs |
| Permission prompt never appears | Already denied twice; Settings → Apps → CatGuard → Permissions |
| Black rectangle, no image | Camera held by another app; close other camera apps |
| "The camera could not be opened" | Same, or a vendor camera service in a bad state — reboot |

Log to expect: `CatGuardCamera` with no errors, then `CatGuard: Guard started`
only after you press START.

---

## 2. Does the model load?

**Settings → What we detect**. The top line should read
`Model: efficientdet_lite0`.

If instead you see an error about the model not being bundled or not loading,
the `.tflite` did not make it into the APK. Check:

```powershell
# should list assets/efficientdet_lite0.tflite at ~4.4 MB
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::OpenRead("app\build\outputs\apk\debug\app-debug.apk").Entries |
  Where-Object { $_.FullName -match 'tflite' }
```

---

## 3. Does the speaker work?

**Settings → Deterrent sound → TEST SOUND.** You should hear a dog bark.

This is worth getting right *before* testing detection, because if the audio is
broken you will later think detection failed when it did not.

- Nothing at all → check the volume for the **channel you selected**. Choosing
  *Alarm* and then pressing the volume keys (which adjust *media*) is the usual
  mistake.
- Too quiet → switch the channel to **Alarm**, raise **repeats**, and turn on
  **Maximise volume when barking**.
- Try each of the four bundled sounds. `CatGuardAudio` logs which one played.

Then test a custom import: **ADD YOUR OWN SOUND**, pick any MP3 on the phone,
and press TEST SOUND again. Watch `CatGuardSounds` in the logs for the import.

---

## 4. Does detection actually fire?

The hard part is producing a cat on demand. In rough order of convenience:

**A. Use a person.** Settings → What we detect → tick **Person**, untick Cat.
Press START GUARD and walk in front of the camera. This exercises the entire
chain — detect, confirm, zone, bark, cooldown — in about two seconds, and person
detection is the most reliable category in the model. Put it back to Cat after.

**B. Use a picture of a cat.** Put a large, well-lit photo of a cat on a laptop
or tablet screen and hold it in front of the camera, filling a good part of the
frame. This does work, but a small phone-screen photo at a distance often will
not — the cat needs to be reasonably large in frame.

**C. Wait for the real cat.** The honest test, but a slow feedback loop.

Expected state sequence in the status card:

```
MONITORING  ->  CAT DETECTED  ->  CAT CONFIRMED  ->  BARKING  ->  COOLDOWN 19s  ->  MONITORING
```

Expected log line on a trigger:

```
CatGuard: Encounter confirmed: cat 0.87 hits=2/3 deterrent=true
```

If boxes appear on the preview but nothing ever fires, in order:

1. Is the animal **inside the detection zone**? Outside is dimmed. Press ZONE →
   *Use full frame* to rule it out.
2. Is the score on the box **above the threshold**? The box shows the percentage.
   Lower the confidence threshold to just under what you see.
3. Is the category **ticked** in What we detect?
4. Are you still in a **cooldown**? The status card counts it down.

---

## 5. Does it survive the screen going off?

This is the one that decides whether the app is actually useful, and the one most
likely to behave differently on your specific phone.

1. Press START GUARD.
2. Check the notification shade shows **CatGuard — Monitoring for cats**.
3. Lock the screen. Leave it on a charger.
4. Wait at least 30 minutes.
5. Unlock. Is the notification still there? Does History show anything that
   walked past in the meantime?

If monitoring stopped:

- **Settings → OPEN BATTERY OPTIMISATION SETTINGS** and exempt CatGuard.
- Add CatGuard to the manufacturer's own protected-apps / auto-start list
  (Xiaomi, Huawei, Oppo, Vivo, Samsung all have one, in different places).
- Re-test. If it still stops on this specific phone with the screen fully off,
  that is a vendor restriction — keep the screen on at minimum brightness.

A useful check while it is locked, from the PC:

```bat
adb shell dumpsys activity services com.catguard
```

---

## 6. Does it hold up overnight?

Leave it running overnight on a charger with a light on in the area.

Next morning check:

- **History** — does the day-by-day table have sensible numbers?
- **Detections vs barks** — a large "silent" count means encounters landed in
  cooldowns or the sound is off.
- Is the phone **hot**? The status card will say if it throttled itself.
- Any false alarms? Turn on `-AllDetections` logging for a session and look at
  what the model reported at those timestamps.

---

## What to send me if something breaks

The log output around the failure is worth more than a description. Capture it:

```powershell
D:\android-toolchain\sdk\platform-tools\adb.exe logcat -d > catguard-log.txt
```

Plus: the phone model, the Android version, and which of the steps above was the
first to fail.
