# Showdist: Google Play listing (draft)

Everything below is ready to paste into Play Console. Character limits are Play's; counts are given where they're tight.

---

## Store listing

**App name** (30 max): `Showdist`

**Short description** (80 max, this one is 79):

```
Measure distances with your camera in AR, plus a bubble level. No ads, offline.
```

**Full description** (4000 max):

```
Showdist is a tape measure and a bubble level in your pocket. It opens straight into the camera: aim at a floor, wall or table, tap to drop points, and read the distance as you go. When you need to hang a shelf straight, switch to the level.

MEASURE IN AR
• Aim the dot and tap the capture button to place points. Each segment shows its length, and a running total adds them up.
• Chain up to 24 points to trace a room, a counter or a garden bed, or switch to Group to measure separate lines, such as a room's length, width and height.
• Tag a group's lines as length, width and height to get floor area and volume.
• Points lock on plain walls as well as floors and tables.
• Feet and inches to the nearest half inch, or metres and centimetres. Switch any time, even mid-measurement.

SAVE, REVISIT, SHARE
• Hold anywhere on the screen to save a measurement to History.
• Each saved measurement keeps its shape as a thumbnail and opens to show every segment length.
• View in AR: project a saved measurement back into the room to check it against the real thing.
• Export a clean PDF report of one or many measurements, with a scaled diagram of each shape, or copy and share them as text.

LEVEL
• Tube level: hold the phone against a wall, frame or shelf. Works upright and on either side.
• Bullseye level: lay the phone flat to check a surface on two axes at once.
• A readout to a tenth of a degree, and a zero button to calibrate against a surface you trust.

BUILT FOR ONE HAND
• Every control sits in one row at the bottom, within reach of your thumb, with a left-handed option.
• High-contrast black, white and cyan, readable in direct sunlight.
• AR pauses itself after a minute idle to save battery. Tap to continue.
• No account, no ads, no tracking. Your measurements stay on your phone.

Showdist needs a phone that supports Google Play Services for AR (ARCore). AR measurements are estimates: for the best results, use good light, move the phone slowly, and calibrate the scale against something you know in Settings. For work where fractions of an inch matter, confirm with a physical tape.
```

**App category:** Tools

**Tags** (pick up to 5 in Console): Measuring, Tools, Augmented reality, Home improvement, Productivity

**Contact email:** `sh.run.configs@gmail.com` (Play Console only; since v1.4 the app shows no email, just the X handle @shcarlosconfigs in About)

**Website:** https://github.com/carlosantoniocruz/showdist-android (public source).

**Privacy policy URL:** https://carlosantoniocruz.github.io/showdist-privacy/ (GitHub Pages, from the public `carlosantoniocruz/showdist-privacy` repo; the page is the text below).

---

## Graphics checklist

| Asset | Requirement | File |
|---|---|---|
| App icon | 512 × 512 PNG, 32-bit, ≤ 1 MB | `graphics/icon-512.png`: the adaptive icon (black background, cyan dimension line, white end points on the ticks) |
| Feature graphic | 1024 × 500 JPG or 24-bit PNG, no transparency | `graphics/feature-graphic-1024x500.png`: the app mark large beside the SHOWDIST wordmark, the tagline beneath, on black |
| Phone screenshots | 2–8, each side 320–3840 px, **long side ≤ 2 × short side** | `graphics/screenshot-*.png`: captured on the Pixel (1080 × 2424, 2.24:1), fitted to 1080 × 2160: system-bar strips painted over where they sit on a flat background, flat top/bottom bands cropped (keeping a 48 px margin), and any remaining height scaled with the edge colours extended to fill the sides. The AR shot first has 170 px of camera image cropped off the top (`screenshot-1-measure.png:170`). |

The graphics are rendered by `Render.java` (run with `java Render.java <out-dir> [in.png out.png[:crop-top]]…`), so they can be regenerated after a palette change.

Screenshot order for the listing (v1.5; numbers match the file names). Raw captures are in `raw/`; camera shots are framed by cropping (`:216`), never scaled, so their edges don't smear:
1. A chained measurement in progress on a tiled floor: segment labels, the live line, the running total, the five-control row.
2. Group measuring live on a desk: three lines and the Length / Width / Height chips.
3. The group's screen: three tagged lines with floor area and volume.
4. Tube level, upright.
5. Bullseye level.
6. History.

Optional extras once the AR shot is done: "View in AR" projecting a saved shape, and a PDF report page (the report isn't an app screen, so show it open in a PDF viewer on the phone).

---

## Privacy policy (published)

> **Showdist Privacy Policy**
> Effective date: 16 November 2026
>
> Showdist ("the app") is developed by Carlos Cruz. This policy explains what the app does with your information.
>
> **Information the app uses.** The app uses your device's camera to show the augmented-reality view and to measure distances (on phones that support it, ARCore also estimates depth from the camera so points can lock on plain walls), and its motion sensors for the levels and to pause the camera when the phone sits idle. Camera images, depth and sensor readings are processed on your device in real time. The app does not record, save or transmit photos, video, depth or sensor data.
>
> **Measurements you save.** Measurements you choose to save (their points, lengths, date and unit, and any names, labels or Length, Width and Height tags you give them) are stored only in the app's private storage on your device, as are your settings and calibration values. They are never uploaded by the app. They are deleted when you delete them in the app, clear the app's data or uninstall it. If you export a PDF or share a measurement as text, it goes only to the app or person you choose in Android's share sheet.
>
> **No accounts, ads or analytics.** The app has no sign-in, shows no ads, and contains no analytics or tracking code. It has no network code of its own: the internet permission Android lists for it comes from the ARCore libraries. The developer does not collect, sell or share any personal information.
>
> **Google Play Services for AR.** This application runs on Google Play Services for AR (ARCore), which is provided by Google LLC and governed by the Google Privacy Policy (https://policies.google.com/privacy).
>
> **Children.** The app is not directed at children and collects no personal information from anyone.
>
> **Changes.** If this policy changes, the updated version will be posted at this address with a new effective date.
>
> **Contact.** sh.run.configs@gmail.com

---

## Data safety form

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (nothing is collected) |
| Do you provide a way for users to request that their data is deleted? | Not applicable; saved measurements stay on the device and can be deleted in the app |

Why "No" holds: "collected" means sent off the device. Showdist has no network code of its own. Camera frames are processed on-device; saved measurements live in private app storage; exporting and sharing are started by the user through the system share sheet, which Play doesn't count as collection. The merged manifest does include `INTERNET`, but it comes from the ARCore/SceneView libraries, not the app.

**Permissions to expect on the listing:** Camera (declared by the app); Internet and Vibrate (added by the ARCore and SceneView libraries).

---

## Content rating (IARC questionnaire)

- Category: **Utility, Productivity, Communication or Other**
- Violence, sexuality, language, controlled substances, gambling: **No** to all
- Users can interact or exchange content with other users: **No**
- Shares the user's location with others: **No**
- Allows purchases of digital goods: **No**

Expected result: Everyone / PEGI 3.

---

## App content declarations

- **Target audience:** 13+ (or 18+). Don't include under-13 age groups, which would pull the app into the Families policy.
- **Ads:** No ads.
- **App access:** All functionality is available without special access (no login).
- **Government app / financial features / health:** No.

---

## Graphics refreshed for v1.5 (16 Nov 2026)

Icon, feature graphic, all screenshots and the full description were redone for the v1.5 interface. Not yet captured: a saved measurement's tray and Settings (add them as 7 and 8 if wanted).

## Before you submit

1. **Upload an App Bundle, not an APK.** Play requires `.aab` for new apps: `./gradlew bundleRelease` produces `app/build/outputs/bundle/release/app-release.aab`, signed with the same release keystore. The 1.3.1 bundle (version code 5) is built and verified (same certificate as the installed APK, SHA-256 `43:B8:ED:39:…:7F:B2`); a copy is at `~/Showdist-1.3.1.aab`. The 1.3 bundle (version code 4) is at `~/Showdist-1.3.aab`. The 1.2 bundle (version code 3) is at `~/Showdist-1.2.aab`.
2. **Play App Signing.** When you create the app, let Google manage the app signing key. The `showdist-release.jks` you already have becomes your **upload key**. Keep it and its password backed up.
3. **Closed test first (new personal accounts).** Personal developer accounts created after November 2023 must run a closed test with at least 12 testers opted in for 14 consecutive days before they can apply for production access. Check the current requirement in Play Console; if it applies, plan to recruit testers early.
4. **Device targeting.** The manifest requires `android.hardware.camera.ar`, so Play will only offer the app to ARCore-supported phones. That's intended.
5. **ARCore disclosure.** Google's ARCore terms ask apps to tell users they run on Google Play Services for AR. The privacy policy above includes the standard sentence; check the current ARCore terms to see whether it also needs to appear in the app itself (the About or Licenses screen would be the natural place).
