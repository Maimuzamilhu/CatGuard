# AI image prompts for CatGuard

Ready to paste into Midjourney, DALL·E, Imagen, Firefly, Stable Diffusion, etc.

**Before you start — what AI images may and may not be used for:**

| Asset | AI-generated? |
| --- | --- |
| App icon | Yes |
| Feature graphic (1024×500) | Yes |
| Promo / marketing art, website header | Yes |
| Backdrop *behind* a real screenshot | Yes |
| **The screenshots themselves** | **No — must be real captures of the running app** |

Google requires store listings to represent the app accurately. A convincing
fake screenshot is a policy violation, not a shortcut.

**Two practical rules for all of these:**

1. **Never ask the model for text.** Image models garble lettering. Generate the
   artwork clean, then add "CatGuard" and any wording afterwards — the script in
   `tools/make-store-graphics.ps1` already does exactly that, and I can adapt it
   to composite text onto any image you generate.
2. **Keep the brand palette** so the store page looks like one product:
   - Deep navy background `#10161F` → `#1B2430`
   - Mint accent `#4DD0A7`
   - Off-white `#F3F6F9`
   - Alert red `#FF5252` (use sparingly)

---

## 1. App icon — 512 × 512

Square, must read clearly at 48 px, no text.

### 1a. Flat vector (matches the app as built)

```
Minimal flat vector app icon of a stylised cat head silhouette, facing forward,
two sharp triangular ears, simple rounded face, two small solid eyes, no mouth,
no whiskers. Off-white cream silhouette (#F3F6F9) centred on a deep navy
background (#1B2430) with a subtle darker vignette toward the corners. Clean
geometric shapes, thick solid forms, no gradients on the cat itself, no outline
strokes, no text, no letters. Centred composition with generous even margins.
Modern mobile app icon, crisp edges, flat design.
```

### 1b. Same but with a guardian / radar feel

```
Minimal flat vector app icon: a stylised cream cat head silhouette with
triangular ears, centred on a deep navy background, framed by two thin concentric
mint-green (#4DD0A7) arc lines suggesting a radar sweep or detection field.
Geometric, symmetrical, flat design, no gradients on the subject, no text, no
letters, generous margins, crisp vector edges. Modern security app icon.
```

### 1c. Softer, friendlier alternative

```
Rounded flat illustration app icon of a calm cat face in cream on a deep navy
rounded-square background, thick soft shapes, gentle geometry, single mint-green
accent arc beneath the chin. Minimal, friendly, no text, no letters, no
whiskers, centred with even padding, flat vector style, no photorealism.
```

**Settings:** square 1:1, 1024×1024 then downscale to 512×512.
Midjourney: append `--ar 1:1 --style raw --no text, letters, watermark, mockup`

---

## 2. Feature graphic — 1024 × 500

This sits at the top of your Play listing. Google may crop the edges, so keep
everything important in the middle and **leave the right half emptier** for the
title text you add afterwards.

### 2a. Abstract backdrop (safest — text goes on top)

```
Wide abstract background banner, deep navy (#10161F to #1B2430) diagonal
gradient, with a soft mint-green (#4DD0A7) radial glow positioned in the left
third. Faint geometric grid lines and two thin concentric detection arcs
emanating from the glow, very subtle, low contrast. Lots of clean negative space
in the right two thirds. Dark, modern, technical, minimal. No text, no letters,
no logos, no characters, no objects.
```

### 2b. With a cat silhouette

```
Wide banner illustration on a deep navy gradient background. On the left third, a
large flat cream-coloured stylised cat head silhouette with triangular ears,
simple and geometric, lit by a soft mint-green glow behind it. The right two
thirds are clean empty dark space. Flat vector style, high contrast, no text, no
letters, no watermark, cinematic dark palette.
```

### 2c. Scene-based, night doorway

```
Wide minimal illustration of a dim indoor hallway at night seen from a low fixed
camera angle, deep navy and teal palette, a soft pool of warm light on the floor
near a doorway, a small dark cat silhouette entering from the left edge, thin
mint-green detection rectangle framing the cat. Flat illustration, muted, quiet,
atmospheric, plenty of dark negative space on the right. No text, no letters, no
people, no photorealism.
```

**Settings:** aspect 1024:500 (≈2.05:1).
Midjourney: `--ar 41:20 --style raw --no text, letters, watermark, signature, ui, mockup`

---

## 3. Backdrop for your real screenshots

Generate this, then place your actual phone screenshots on top. This is the
legitimate way to make the screenshot strip look designed.

```
Vertical background panel for a mobile app store screenshot, deep navy gradient
(#10161F to #1B2430), a soft mint-green radial glow in the upper area, very
subtle geometric grid texture, completely empty centre with no objects. Clean,
dark, minimal, generous negative space. No text, no letters, no phone, no device
frame, no UI elements.
```

**Settings:** aspect 9:16, 1242×2208 or larger.

Generate **one** backdrop and reuse it behind all your screenshots — a
consistent strip looks far more professional than eight different backgrounds.

---

## 4. Promo art for a support page or social post

```
Illustration of a smartphone mounted on a small tripod in the corner of a dim
tiled room, screen glowing faint mint-green, watching an empty doorway at night.
Deep navy and teal palette, flat vector illustration with soft ambient light,
calm and technical rather than menacing. No text, no letters, no people, no
brand logos.
```

```
Flat isometric illustration of a small camera device emitting a translucent
mint-green cone of detection across a tiled floor, a stylised cat silhouette
pausing at the edge of the cone. Deep navy background, minimal geometric style,
soft shadows, no text, no letters, no photorealism.
```

---

## What to avoid asking for

- **Text of any kind.** Add it afterwards.
- **Anything that looks like a real app screenshot.** A rendered "phone showing
  the CatGuard interface" is fine as *promo art* but must never be uploaded as a
  screenshot.
- **Distressed, frightened or injured animals.** Beyond being unpleasant, it
  misrepresents what the app does and invites poor reviews and policy attention.
  Keep any cat in the artwork calm and neutral — it is walking away, not scared.
- **Realistic photos of a specific phone model.** Brand trade dress in store
  graphics is a needless risk.
- **Google Play badges, Android robot, or any Google branding.**

---

## After you generate them

Send me the files and I can:

- composite the title and tagline onto your chosen feature graphic, correctly
  positioned and in the app's own palette
- resize and pad the icon to exactly 512×512 PNG with no alpha (Play rejects
  transparency in the icon)
- build the screenshot strip — your real captures placed on the generated
  backdrop at the right resolution

`tools/make-store-graphics.ps1` already does the text compositing for the current
graphics; pointing it at a new background image is a small change.
