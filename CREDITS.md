# Third-party assets

## Deterrent sounds

The four bundled dog sounds in `app/src/main/res/raw/` are real recordings from
Wikimedia Commons, used under Creative Commons Attribution-ShareAlike licences.

| File in this app | Original work | Author | Licence |
| --- | --- | --- | --- |
| `dog_bark_short.ogg` | [Barking of a dog.ogg](https://commons.wikimedia.org/wiki/File:Barking_of_a_dog.ogg) | [Amada44](https://commons.wikimedia.org/wiki/User:Amada44) | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| `dog_bark_double.ogg` | [Barking of a dog 2.ogg](https://commons.wikimedia.org/wiki/File:Barking_of_a_dog_2.ogg) | [Amada44](https://commons.wikimedia.org/wiki/User:Amada44) | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| `dog_bark_alert.ogg` | [Perro ladrando.ogg](https://commons.wikimedia.org/wiki/File:Perro_ladrando.ogg) | [Armartinell](https://commons.wikimedia.org/wiki/User:Armartinell) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| `dog_bark_sustained.ogg` | [Barking dog in Rome.ogg](https://commons.wikimedia.org/wiki/File:Barking_dog_in_Rome.ogg) | [Nicholas Gemini](https://commons.wikimedia.org/wiki/User:Nicholas_Gemini) | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| `deterrent_vacuum.ogg` | [Het aanstaan van een stofzuiger](https://commons.wikimedia.org/wiki/File:Het_aanstaan_van_een_stofzuiger_-_SoundCloud_-_Beeld_en_Geluid.ogg) | Beeld en Geluid | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| `deterrent_thunder.ogg` | [Thunder 01.ogg](https://commons.wikimedia.org/wiki/File:Thunder_01.ogg) | [Amuzujoe](https://commons.wikimedia.org/wiki/User:Amuzujoe) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| `deterrent_fireworks.ogg` | [Part of fireworks.ogg](https://commons.wikimedia.org/wiki/File:Part_of_fireworks.ogg) | Stephan (pdsounds.org) | Public domain |

### Changes made

Every file was re-mastered for a phone speaker:

- silence trimmed from the start so the sound is instant, and internal gaps
  capped, so a bark does not begin with dead air
- high-passed at 260 Hz — a phone speaker radiates almost nothing below that, so
  the low end was only eating headroom
- 1.2 / 2.5 / 4.2 kHz lifted, where a phone speaker is efficient and human and
  feline hearing are most sensitive
- driven into a peak limiter to roughly −8 to −12 LUFS, which is 4–9 dB louder
  than the originals
- downmixed to mono and resampled to 44.1 kHz
- re-encoded as Ogg Vorbis (quality 5-6)
- each clip trimmed to its loudest, most useful section

**ShareAlike notice:** the CC BY-SA originals stay under their own licence after
modification — CC BY-SA 3.0 for the three 3.0-licensed works and for the vacuum
recording, CC BY-SA 4.0 for `dog_bark_alert.ogg` and `deterrent_thunder.ogg`.
`deterrent_fireworks.ogg` is public domain and carries no conditions. This
applies to the audio files themselves; the rest of CatGuard is not a derivative
work of them.

This attribution is also shown inside the app under **Settings → Deterrent sound
→ Sound credits**.

### Sounds you add yourself

Custom sounds imported through **Add your own sound** are copied into the app's
private storage on your device. They are not redistributed and are not covered by
anything above — you are responsible for having the right to use them.

---

## Detection model

`app/src/main/assets/efficientdet_lite0.tflite` is **EfficientDet-Lite0**, trained
on the COCO dataset and published by Google for TensorFlow Lite, obtained from:

```
https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite
```

Licensed under the Apache License 2.0.

---

## Libraries

Standard AndroidX / Jetpack Compose / CameraX components (Apache 2.0) and
TensorFlow Lite Task Vision (Apache 2.0). See `gradle/libs.versions.toml` for
exact versions.
