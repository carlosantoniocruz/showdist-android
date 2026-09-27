# Showdist — Functional & Visual Specification

A complete description of the Showdist Android app (currently Flutter + a native ARCore layer), written so it can be rebuilt natively in Kotlin without reference to the existing source.

All sizes are in density-independent pixels (dp; for text, sp). "Alpha" is opacity from 0 to 1. Angles are in degrees unless noted. Where this spec disagrees with the README, the spec follows the actual code (see §13).

---

## 0. App identity & platform

| Item | Value |
|---|---|
| App name (launcher label) | Showdist |
| Application ID / namespace | `com.showconfigs.showdist` |
| Version | 1.1 (version code 2) |
| Min SDK | 24 (ARCore requires 24+) |
| Target / compile SDK | Latest stable (was Flutter's default, platform 36) |
| JVM target | 17 |
| AR library | Google ARCore `com.google.ar:core:1.56.0`, **required** (manifest meta-data `com.google.ar.core = required`) |
| Permissions | `CAMERA` |
| Required features | `android.hardware.camera.ar`, OpenGL ES 2.0 |
| Orientation | Activity locked to **portrait**, except the tube Leveler, which allows portrait, landscape-left and landscape-right while it is open, then restores portrait-only on exit |
| System UI | Immersive sticky (status and navigation bars hidden; they reappear briefly on swipe) |
| Screen timeout | Screen kept on (`FLAG_KEEP_SCREEN_ON`) while the app is in the foreground |
| Theme | One fixed night-sky theme (true black, near-white text, bright accents; §5), after the Android 17 Easter egg. It ignores the system light/dark setting |
| License | MIT, © 2026 carlosantoniocruz |

---

## 1. Screens and navigation

### 1.1 Screen map

```
Launch screen (black field)
  └─ Main Menu (landing; carries the About details)
       ├─ Measure (AR)  ──► History ──► Measurement detail sheet
       │        └─► "Saved" sheet (after hold-to-save)
       ├─ History (only shown once ≥1 measurement is saved) ──► Measurement detail sheet
       │        └─► "View in AR" ──► Measure, projecting it (§2.11)
       ├─ Export PDF (only shown once ≥1 measurement is saved; shares every saved measurement)
       ├─ Tube Leveler
       ├─ Bullseye Leveler
       └─ Settings (gear, top-right)
             └─ Licenses
                  └─ License detail (one package)
```

Every screen is a full-screen page pushed on a back stack. The system Back button/gesture and each app bar's back arrow (`arrow_back_rounded`, tooltip "Back") pop one level. The main menu is the root; Back there exits the app.

### 1.2 Shared app-bar style (Settings, Licenses, License detail, History, Leveler)

- Background black, no elevation, no surface tint. Foreground (icons, title) near-white (text).
- Title centred and set in the **Caption** style (§6.3) in near-white: "SETTINGS", "LICENSES", "LICENSE", "HISTORY", "TUBE LEVELER", "BULLSEYE LEVELER".
- Leading: back arrow.

### 1.3 Launch screen

- Pre-Android 12: the window background is a plain **black (#000000)** field. No logo.
- Android 12+: the system splash screen shows the app icon on `windowSplashScreenBackground` = black.
- The window background behind the app while it starts is also black, so nothing flashes white.

### 1.4 Main Menu (landing screen)

Background black, with the night-sky motif (§6.4) behind everything. Content sits inside the system safe area, in a column with 24 dp side padding and 24 dp bottom padding. The column scrolls only if its content is taller than the screen (large font sizes); otherwise the header sits at the top and the tools are centred in the height left below it. The screen reads as information-dense: no large empty regions.

**1. Header block** (upper left, read as one section):
- A row, 8 dp top padding: the **SHOWDIST wordmark** (§6.2); 14 dp gap; the **dimension mark** (the app icon's motif, §8.1) 56 × 16 dp; flexible space; the **Settings** button (outlined gear `settings_outlined` in textMuted, tooltip "Settings"), which opens Settings.
- 14 dp gap, then "An AR tape measure and a level." (13 sp, text, line height 1.5).
- 14 dp gap, then **HOW TO USE** in the Caption style (11 sp, start-aligned), a 6 dp gap, and one row per task. Each row has 2 dp vertical padding: a 76 dp-wide label (11 sp, weight 700, letter-spacing 1, in the tool's accent) and its text (12 sp, textMuted, line height 1.45), verbatim:

  | Label | Accent | Text |
  |---|---|---|
  | MEASURE | green | Move slowly until the reticle's dots turn solid, then tap the green button to drop points. Each span is labelled; the total shows at the top. Undo, × clears, M / FT switches units. |
  | SAVE | green | With two or more points, hold anywhere until the ring fills. |
  | HISTORY | green | Share, copy, or View in AR: tap where the first point goes, turn to line up, tap. Select several for one PDF. |
  | TUBE | violet | Hold an edge to a wall or beam, upright or sideways. Green within 0.3° of level. |
  | BULLSEYE | violet | Lay flat. X is side to side, Y front to back. |
  | ZERO | violet | On an uneven base, the target icon sets level here; reset returns to true level. |

- 10 dp gap, then the **About** lines (11 sp, textMuted, line height 1.5): "v{versionName} · Carlos Cruz · sh.run.configs@gmail.com", then "Created with AI assistance" in italic. The version is read from the installed package.

**2. Tools**, 16 dp below the header:
- The **MEASURE** card (full width).
- Only when ≥1 recording is saved (updating live), two **sub-links** under it:
  - **HISTORY** ("N saved"): opens History without starting the camera.
  - **EXPORT PDF** ("1 measurement" / "all N"): writes one PDF report (§2.12) of every saved measurement, newest first, and opens the share sheet.
- 16 dp gap, then a row of two half-width **tiles** with a 12 dp gap: **TUBE** and **BULLSEYE**. Each opens its Leveler directly.

**Tool card** (MEASURE): 28 dp corners, fill surfaceHigh at alpha 0.92, 1 dp border in green at alpha 0.7, ripple, padding L18 T16 R20 B16. Leading: the dimension mark (44 dp), 16 dp gap; then "MEASURE" (18 sp, weight 700, letter-spacing 2, text), a 4 dp gap, "Distances with the camera." (12 sp, textMuted, line height 1.35); 12 dp gap; `arrow_forward_rounded` (22 dp, green).

**Tool tile** (TUBE, BULLSEYE): the card's shape, fill and ripple with a violet border, padding L18 T16 R14 B16. A row with the 44 dp picture and `arrow_forward_rounded` (20 dp, violet) at the far end; 12 dp gap; the label (15 sp, weight 700, letter-spacing 2); 4 dp gap; the description (11 sp, textMuted): TUBE "Walls, posts, beams.", BULLSEYE "Flat surfaces."
- **Tube picture:** a stadium 44 dp wide and 16 dp tall, 1.5 dp line outline, with a white diamond star (half-diagonal 2.5 dp) 3 dp beyond a violet bubble (radius 5 dp) on each side.
- **Bullseye picture:** a 44 dp circle and a ring at 55% of its radius, both in line, a white diamond star at the four rim points, a violet centre bubble (radius 5 dp).

**Sub-link:** indented 40 dp; a vertical rule 1 dp × 44 dp in green at alpha 0.6 at its left edge; a tappable pill-shaped row, padding L14 T12 R8 B12: icon (18 dp, green: `history_rounded` / `picture_as_pdf`), 10 dp gap, label (13 sp, weight 700, letter-spacing 1.5, text), 10 dp gap, detail (12 sp, textMuted, fills the width), `chevron_right_rounded` (20 dp, textMuted).

The main menu never touches the camera or the AR session.

### 1.5 Settings

A list with 8 dp vertical padding:

- Section header "UNITS" in the Caption style, textMuted, left-aligned, padding L16 T12 R16 B4.
- Option row "Imperial (ft, in)" (16 sp, text). Its trailing radio icon is `radio_button_checked_rounded` in **amber** when selected, or `radio_button_unchecked_rounded` in textMuted otherwise.
- Option row "Metric (m, cm)", the same, with **cyan** when selected. (Amber and cyan are the unit colours, §5.)
- 16 dp gap, then a 1 dp divider (grid colour, inset 20 dp on both sides).
- Section header "ABOUT".
- A row "Licenses" (text) with subtitle "ARCore, SceneView, AndroidX and JetBrains Mono (OFL-1.1)" (12 sp, textMuted) and a textMuted chevron. It opens Licenses.

Tapping an option sets the unit system immediately and persists it (§2.7). About's content (version, developer, "Created with AI assistance") is in the main menu header (§1.4); there is no About screen.

### 1.7 Licenses

- While loading, a centred circular progress indicator is shown in green.
- The screen shows every shipped component, sorted alphabetically. Each row shows the name in text with a textMuted chevron. Rows are separated by dividers (1 dp, grid colour, indented 20 dp).
- The license list is built once per app run and cached.
- Tapping a row opens **License detail**. Its padding is 24 dp on all sides. The component name is shown in **violet** at 24 sp, weight 700, followed by a 20 dp gap. Then comes each license paragraph in text at 13 sp with line height 1.5 and a 16 dp gap after each. Paragraphs marked "centred" are centre-aligned, and all others are start-aligned.
- The list is curated by hand in `settings/Licenses.kt` (§7.2).

### 1.9 History

- The list shows every saved measurement, newest first, separated by dividers (1 dp, grid colour, indented 72 dp). The list has 24 dp bottom padding.
- **Empty state:** centred text with 40 dp side padding, 14 sp, textMuted, line height 1.5:
  "No measurements yet.\nPlace two or more dots, then hold anywhere on screen to save."
- **Row** (left padding 16, right padding 4):
  - Leading: a 44×44 **shape thumbnail** (§2.8), drawn in text, or green when the row is selected.
  - Title: the total length (§2.5), 20 sp, weight 300, text.
  - Subtitle: "N points", 12 sp, textMuted. No date is shown anywhere.
  - Trailing (normal mode): a share-menu button (§2.7).
  - Trailing (selection mode): `check_circle_rounded` in green if selected, `circle_outlined` in textMuted if not, with 12 dp right padding.
  - A selected row is tinted green at alpha 0.12.
- **Normal mode:**
  - Tapping a row opens the **Measurement sheet** (§2.7) titled "MEASUREMENT".
  - Long-pressing a row enters selection mode with that row selected.
  - The app bar's overflow button ("More") is disabled when the list is empty. Its menu offers "Select" (enter selection mode with nothing selected) and "Delete all".
- **Selection mode:**
  - The leading icon is a close X (tooltip "Cancel selection"), which exits selection mode.
  - The title is "N selected" (16 sp, weight 400).
  - Actions:
    - "Select all" (`select_all_rounded`).
    - "Export PDF" (`picture_as_pdf`), disabled when nothing is selected. It exports the selection as one PDF report (§2.12).
    - "Copy" (`content_copy`) and "Share" (`ios_share`), each disabled when nothing is selected. They copy or share the selection as one text in list order (newest first).
    - "Delete selected" (`delete_outline_rounded`, **pink**), disabled when nothing is selected.
  - Tapping or long-pressing a row toggles it.
  - System Back first leaves selection mode and only pops the screen on the next press.
- **Confirmation dialog** (used for every delete):
  - Background surface, 28 dp corners.
  - Title in text, 18 sp, weight 400: "Delete 1 measurement?", "Delete N measurements?", or "Delete all N measurements?".
  - Body: "This can't be undone." in textMuted.
  - Buttons: "Cancel" (textMuted) and "Delete" or "Delete all" (text).
  - After a confirmed delete, selection mode exits and the items are removed.
- History can be reached from the main menu or from the Measure screen. Opening it from Measure **pauses** the AR session, and returning **resumes** it with all points intact.

---

## 2. Measurement (AR Measure screen)

### 2.1 Session lifecycle & error states

When the screen opens:

1. Start the device-orientation listener (§2.9) and register the memory-high callback (§2.10).
2. Show a centred Caption "WAKING THE CAMERA" (textMuted) on black.
3. Request camera permission.
   - **If denied**, show the Problem view:
     - Title: "Camera access needed"
     - Body: "Measuring uses the camera to track surfaces. Nothing is recorded or uploaded."
     - Button: "Allow camera" (asks again). If the denial is permanent, the button reads "Open settings" instead, opens the app's system settings page, and the app retries automatically when it returns to the foreground.
4. Check or install ARCore and create a session, if one doesn't exist yet. Configure it as follows:
   - Plane finding: horizontal **and** vertical.
   - Light estimation: disabled.
   - Update mode: latest camera image.
   - Focus: auto.
   - Depth mode: automatic, if the device supports it.
   - Instant Placement: disabled (v1.1).
5. Resume the session. The start routine returns one of these outcomes:

| Outcome | Title | Body | Button |
|---|---|---|---|
| ready | — (AR view shown) | | |
| install requested | Installing AR services | Google Play Services for AR is being installed. Come back here when it finishes. | none (auto-retries on return to foreground) |
| user declined install | AR services required | Showdist needs Google Play Services for AR to see surfaces. | Try again |
| device not compatible | Device not supported | This device does not support ARCore, which measuring depends on. | none |
| ARCore APK or SDK too old | AR services out of date | Update Google Play Services for AR from the Play Store, then try again. | Try again |
| camera not available | Camera unavailable | Another app is using the camera. Close it and try again. | Try again |
| any other error | Something went wrong | the raw error string (`error:<ExceptionClass>: <message>`) | Try again |

The ARCore install prompt is shown only on the first attempt. Later attempts don't re-prompt the user.

**Problem view** (centred, 36 dp side padding):
- A filled 40 dp circle in pink.
- 24 dp gap, then the title (22 sp, weight 300, text, centred).
- 12 dp gap, then the body (14 sp, textMuted, line height 1.4, centred).
- If there is an action: a 28 dp gap, then a pill-shaped outlined button with green text (weight 700) and a 1 dp green border.

**Lifecycle rules:**
- When the app goes to the background (activity paused), pause the GL view and the AR session. Resume both when the app returns, but only if a session was started.
- When another screen (History) is pushed over Measure, pause the session and the orientation sensor **in place**. Anchors survive. When the user returns, resume both.
- When the Measure screen is closed, stop and **close** the session. This discards all anchors and ARCore's map.
- When the app returns to the foreground, restart the orientation listener. Retry the start routine only if the current problem is flagged to retry on resume.

### 2.2 AR layout

The screen is a vertical column:

1. **Camera area** fills all the height above the control bar, so nothing measured is ever hidden behind the bar. It is a stack of layers, bottom to top:
   1. The camera feed: a full-screen textured quad from ARCore's external OES texture, transformed with ARCore's display-geometry mapping.
   2. The **overlay canvas** (§2.4, §3). Touches pass through it. There is no top scrim; the hint and total each sit on their own scrim pill.
   3. The **top info column**, top-centre, inside the safe area (excluding bottom), padded L60 T16 R60 so it stays clear of the menu button:
      - The **hint** line (§2.3).
      - When there are ≥2 points: a 6 dp gap, then the **running total** as a pill. The pill is scrim (§5), fully rounded, with 16 h × 4 v padding. The total text is 34 sp, weight 200, pure white, with tabular figures, formatted per §2.5.
   5. The **menu button**, top-left inside the safe area, 8 dp from the top and left: a 48 dp tap target around a 40 dp scrim disc holding a white `arrow_back_rounded` (22 dp, alpha 0.9). It returns to the main menu, closing the session like Back. Its content description is "Back to menu". It sits as far as possible from the control bar so it isn't hit by accident, and it turns with the bar icons (§2.9). A quick tap is needed; a long press still charges hold-to-save.
2. **Control bar** (§2.6).

The whole screen, camera area and bar alike, is covered by one **long-press** detector that drives hold-to-save (§2.6). It does not block taps on child controls.

### 2.3 Hint text

The hint is 13 sp pure white, letter-spacing 0.3, on a fully rounded scrim pill (§5) with 14 h × 6 v padding, so it reads outdoors. It cross-fades over 250 ms whenever the text changes. Its content depends on the tracking state:

| State | Hint |
|---|---|
| stopped | Starting camera… |
| lost/paused, reason too dark | Too dark. Find some light |
| lost/paused, reason excessive motion | Slow down |
| lost/paused, reason insufficient features | Point at a textured surface |
| lost/paused, reason camera unavailable | Camera unavailable |
| lost/paused, other or no reason | Tracking lost. Move slowly |
| tracking, 0 planes and no reticle hit | Move slowly to scan surfaces |
| tracking, not warmed up (§2.4), 0 points, no placed/aligning projection | Move slowly to scan surfaces (0 planes) / Move the phone slowly to get started |
| tracking, reticle hit, projection aiming (§2.11) | Tap where its first point goes |
| tracking, reticle hit, projection aligning | Turn it to line up, then tap |
| tracking, no reticle hit and (<2 points or projection aiming/aligning) | Aim at a surface |
| tracking, 0 points | Tap to place a point |
| tracking, 1 point | Place the next point |
| tracking, ≥2 points | Tap to add · hold anywhere to save |

### 2.4 Per-frame data & hit testing

Work runs only when ARCore delivers a **new camera image**, about 30 per second. The GL view itself redraws continuously at the display rate, drawing only the camera background on frames with no new image. For each new image:

- **Tracking status:** TRACKING, PAUSED (treated as "lost"), or STOPPED. The failure reason is none, bad state, insufficient light, excessive motion, insufficient features, or camera unavailable.
- **Plane count:** the number of planes that are TRACKING and not subsumed by another plane. It is recounted every 10th new image, about 3 times a second, and is used for the hint and the warm-up.
- **Reticle hit:** hit-test the **exact centre of the camera area**, then take, by priority (v1.1):
  1. the nearest Plane hit whose pose is inside the plane's polygon, else
  2. the nearest DepthPoint hit (when the device supports depth), else
  3. the nearest feature Point hit whose orientation mode is ESTIMATED_SURFACE_NORMAL, as a last resort.
  If none qualifies, there is no reticle hit. Hit testing only runs while the camera is TRACKING (so never with a tracking failure reason).
- **Warm-up (v1.1):** before the first point of a measurement (or a projection's first tap), the session must have found at least one plane and the camera must have moved at least **8 cm** from where tracking began, for parallax. The camera position comes from the view matrix (−Rᵀt). Once warmed up, it stays so for the session. Until then the main button is disabled and the hint says why (§2.3).
- **Placing a point (v1.1):** a tap on the main button (enabled only with a reticle hit and, for a first point, after warm-up):
  - **Snap:** if a visible point's screen position is within **24 dp** of the reticle, the new point is anchored at that point's current pose (the nearest such point). Projections don't snap.
  - **Otherwise sample:** the reticle hit's position is recorded on each of the next new camera images until **6** samples are taken (about 200 ms), within at most 12 images. Tracking must hold throughout, or the placement is dropped. With at least 3 samples, the point is anchored at their **component-wise median**; with fewer, nothing is placed. Further taps are ignored while sampling.
  - Each point gets its own session anchor. Distances are recomputed from the live anchor poses every new image, and the anchors are re-read when saving.
  - **Debug builds only:** each placement is logged (tag `ShowdistMeasure`) with the hit kind (Plane, Depth, Point or Snap), the tracking state, the sample count and the spread (largest distance of a sample from the median, in mm). Release builds contain none of this.
- **Screen projection:** project each world point with ARCore's camera matrices (near 0.05 m, far 100 m):
  - `clip = Projection × View × (x, y, z, 1)`
  - The point is behind the camera if `clip.w ≤ 1e-4`.
  - `screenX = clip.x/clip.w × 0.5 + 0.5`
  - `screenY = 1 − (clip.y/clip.w × 0.5 + 0.5)`
  - Both are normalised 0..1 with the origin at top-left.
  If the reticle hit projects behind the camera, it is treated as no hit.
- **Anchor visibility:** an anchor is "visible" when the camera is tracking, the anchor is tracking, and it projects in front of the camera. It may still fall outside the 0..1 range.

### 2.5 Points, distance, units, rounding

**Capturing a point:**
- The main button is enabled only while there is a reticle hit.
- Tapping it plays a light haptic tick ("selection click") and sets an "add requested" flag.
- On the **next new camera image**, if there is a reticle hit and fewer than **24** points exist, a new anchor is created at the hit pose. It is a **session (world-space) anchor**, not one attached to the plane, so the point does not drift as ARCore refines the plane.
- If the conditions aren't met, the request is silently dropped.
- The request flag is consumed either way.

**Distance calculation:**
- Each point is the anchor's current pose translation (x, y, z) in metres, in ARCore world space. Distances are **recomputed every frame** from the live anchor poses, so labels can change slightly as tracking refines.
- Segment length is the straight-line 3D distance between consecutive points:
  `d(a, b) = √((ax−bx)² + (ay−by)² + (az−bz)²)`
- Total = the sum of all consecutive segments, 1→2, 2→3, and so on. Points form an open polyline, not a closed shape. The total counts every segment, even ones whose endpoints are off-screen.
- The live distance is the 3D distance from the last point to the current reticle hit.

**Units:**
- There are two systems, **Imperial** (the default) and **Metric**.
- The choice is persisted (§2.6) and shared by the Settings screen and the in-AR M/FT toggle.
- Changing units reformats all numbers immediately.

**Formatting & rounding**, where `m` is metres:

- **Metric:**
  - `m < 0.1` → centimetres with one decimal: `(m×100)` to 1 dp + " cm", e.g. `3.4 cm`.
  - `0.1 ≤ m < 1` → whole centimetres: `round(m×100)` + " cm", e.g. `23 cm`.
  - `m ≥ 1` → metres with two decimals + " m", e.g. `1.23 m`.
  - Rounding is half away from zero, so for example 0.0999 m gives "10.0 cm" and 0.9996 m gives "100 cm".
- **Imperial**, rounded to the nearest **half inch**:
  - `inches = m / 0.0254`
  - `halves = round(inches × 2)`, clamped to ≥ 0
  - `wholeInches = halves ÷ 2` (integer); there is a half if `halves` is odd
  - `feet = wholeInches ÷ 12`, `rem = wholeInches mod 12`
  - Inches text is `"{rem} 1/2″"` when there is a half, otherwise `"{rem}″"`.
  - With feet > 0: `"{feet}′ {inchesText}"`. Otherwise just the inches text.
  - Examples: `4″`, `1′ 0″`, `4′ 7 1/2″`.
  - The glyphs are PRIME ′ (U+2032) for feet and DOUBLE PRIME ″ (U+2033) for inches, not ASCII quotes.

The rationale for this precision is that AR tracking is good to about 1 cm.

### 2.6 Control bar & actions: undo, clear, save (reset)

**Bar:**
- black fill with a **1 dp top border** in line (§5).
- Bottom safe-area inset applied, plus 24 dp vertical padding.
- The bar itself never rotates.

It is divided into **five equal-width columns**, each control centred in its own column. Left to right:

| # | Control | Details |
|---|---|---|
| 1 | M / FT unit toggle | Two tappable labels, "M" and "FT". The active one is weight 700 in its unit colour: **cyan** for M, **amber** for FT. The inactive one is textMuted at alpha 0.6, weight 500. Both are 12 sp with letter-spacing 1.5 and padding 7 h × 16 v. Tapping sets and persists the unit system. |
| 2 | Undo | `undo_rounded`. Tooltip "Undo last point". With no points left, steps a projection back a stage (§2.11). Disabled when there are no points and no projection. |
| 3 | Main button | 96×96. Place point on tap (§2.4). Enabled with a reticle hit and, before a first point, after warm-up. Shows the save charge. |
| 4 | Clear | `close_rounded`. Tooltip "Clear all points". Also removes a projection. Disabled when there are no points and no projection. |
| 5 | History | `history_rounded` icon. If ≥1 saved, a count badge appears (white, 11 sp, weight 700, top-right of the 52×52 hit area, at right 4 / top 6). Opens History (pausing the session). |

- The icon buttons are bare 24 dp icons centred in a 52×52 hit area. They are text at alpha 0.9 when enabled and 0.3 when disabled.
- Controls 1, 2, 4 and 5 each sit in a fixed 60×60 slot and **rotate** to stay upright (§2.9). The round main button doesn't rotate.
- The 96 dp main button may overhang its column evenly.

**Main button** (custom-drawn, 96×96). Its accent is green at full strength when enabled (reticle on a surface) and green at alpha 0.4 when disabled. It has:
- An outer ring with radius `48 − 2 = 46` dp and a 2 dp stroke in the accent colour.
- A filled centre dot with a 16 dp radius in the accent colour.
- When charge is above 0, a white arc with a 3 dp stroke, round caps and the same radius. It starts at 12 o'clock and sweeps clockwise through `360° × charge`.
- Accessibility label: "Place point. Hold anywhere on screen to save measurement".

**Undo:**
- Removes (detaches) the **last** anchor only.
- There is no confirmation and no redo.

**Clear:**
- Detaches **all** anchors immediately, with no confirmation and no save.
- The session and ARCore's map stay alive.

**Hold-to-save (the "reset" after a measurement):**
- A long press **anywhere on screen** starts a charge. The standard long-press recognition delay is about 500 ms, then charging begins.
- The charge starts only with ≥2 points and plays a selection-click haptic.
- It animates linearly from 0 to 1 over **800 ms**, filling the main button's ring.
- If the finger lifts or the gesture is cancelled before completion, the charge animates back to 0 over 200 ms. Nothing is saved.
- On completion:
  1. Play a medium haptic impact.
  2. Snapshot the current points' world positions into a **Recording** (§2.7).
  3. **Clear all anchors**. The session keeps running, so this is a fresh start for the next measurement without restarting AR.
  4. Add the recording to the front of the store and persist it.
  5. Reset the charge to 0.
  6. Show the **"SAVED" sheet** (§2.7) as a modal bottom sheet.

**Leaving the screen** discards any unsaved points without warning.

### 2.7 Recordings, persistence, sheets, sharing

**Recording** fields:
- `id`: the creation time in microseconds since the epoch, as a string.
- `createdAt`: an ISO-8601 local timestamp.
- `points`: an ordered list of `[x, y, z]` world positions in metres, from the point anchors. Their order is the segment order.
- `unit` (v1.1): `"Imperial"` or `"Metric"`, the unit setting when it was saved. Absent on older recordings.
- `total` (v1.1): the total length in metres, written on every save.

Derived values: segments (consecutive 3D distances) and total (their sum). The stored `total` is used only for a recording with fewer than 2 points.

**Migration:** files from before v1.1 have no `unit` or `total`; they load as they are (no unit, total from the points) and gain `total` on the next save. A recording with no `points` still loads.

**Store:**
- A single JSON file, `recordings.json`, in the app's private files directory. It holds an array of `{id, createdAt, points, total, unit?}` objects, **newest first**.
- Loaded once at app start.
- **Writes are atomic:** write `recordings.json.tmp` and flush it, then rename it over the original.
- If the file can't be parsed, rename it to `recordings.json.corrupt` and start empty.
- Operations: add (prepend), remove a set of ids, and clear all. Each one notifies observers (the main-menu link, the Measure badge, History) and saves.

**Units setting:** persisted in shared preferences under the key `showdist.unit_system`, value `"metric"` or `"imperial"`. If it's missing or unknown, it defaults to imperial.

**Measurement sheet** ("SAVED" after saving, "MEASUREMENT" from History):
- A modal bottom sheet with background surface, 28 dp top corners and a drag handle. It is scrollable, with a maximum height of 80% of the screen and padding L24 R24 B20.
- Contents:
  1. The title in the Caption style, textMuted.
  2. 10 dp gap, then the total (44 sp, weight 200, text).
  3. 4 dp gap, then "N points · M segments" (13 sp, textMuted).
  4. 18 dp gap, then one row per segment: a 32 dp-wide index "1", "2"… (13 sp, textMuted) followed by the length (15 sp, text, tabular figures). Rows have 5 dp vertical padding.
  5. 18 dp gap. From History only (not the "SAVED" sheet), a full-width filled **green** pill button "View in AR" (black, 14 sp, weight 700) that projects the recording in Measure (§2.11), then an 8 dp gap.
  6. An outlined pill button "Share" with a share icon (text, 1 dp border in line).
  7. 4 dp gap, then a text button "Copy text" with a copy icon (text).
  8. A text button "Done" (textMuted) that closes the sheet.
- Choosing View in AR, Share or Copy closes the sheet first, then performs the action.

**Share menu button** (used on History rows and in selection mode):
- A popup opened from a `ios_share_rounded` icon (22 dp; text when enabled, textMuted when disabled). Tooltip "Share or copy". Menu background surface.
- Items:
  - "Share" with a share icon.
  - "Copy text" with a copy icon.
  - Item icons are 18 dp, text at alpha 0.8, with a 12 dp gap before the label.

**Text** (v1.1; the same data as the PDF, in the current unit setting). Built in memory for each action and never written to storage; PDF is the only file export (§2.12). For one or more recordings, in list order, separated by a blank line:
```
#1 · YYYY-MM-DD HH:MM
Unit: metric            (or imperial)
1. {seg1}
2. {seg2}
…
Total: {total}

#2 · YYYY-MM-DD HH:MM
…
```

**Actions:**
- **Share**: open the Android share sheet with `ACTION_SEND`, type `text/plain`, the text as `EXTRA_TEXT` (not a file, not via the FileProvider). Subject: `Showdist: {total}` for one recording, `Showdist: {N} measurements` for more.
- **Copy text**: put the text on the clipboard, then show a snackbar reading "Copied to clipboard".
- **On failure**: show a snackbar reading "Could not share: {reason}" or "Could not copy: {reason}". The reason is the first line of the error message, cut to 117 characters plus "..." if it is longer than 120.

### 2.8 History shape thumbnail

This flattens a 3D polyline into a unit square:

1. Compute each axis's range (max − min) and pick the two axes with the largest ranges.
2. If world-up (y) is among the two, it becomes the vertical axis and the other chosen axis becomes horizontal. Otherwise, as for a floor measurement seen from above, the larger-range axis is horizontal and the other is vertical.
3. `span = max(range_u, range_v)`. If span < 1e-9, every point sits at (0.5, 0.5).
4. Normalise each point:
   - `u' = 0.5 + (u − min_u − range_u/2)/span`
   - `v' = 0.5 − (v − min_v − range_v/2)/span`
   World-up is drawn as up on screen.

Drawing within the 44×44 box, with 7 dp padding:
- An open polyline with a 1.2 dp stroke, round caps and joins, in the row colour at alpha 0.7.
- A diamond star (half-diagonal 3.2 dp) at each point, in the row colour.

### 2.9 Orientation handling in Measure (button rotation)

The screen stays portrait. Only the four small bar controls rotate.

The accelerometer is read at about **5 Hz**. Using raw device-frame `x` and `y` in m/s²:
- If `√(x² + y²) < 3.0` (the phone is roughly flat), keep the current value.
- Otherwise compute `deg = atan2(x, y)` in degrees, where 0 means upright portrait.
  - `|deg| < 35` → 0 turns
  - `55 < deg < 125` → +1 quarter-turn clockwise (the phone is on its left edge)
  - `−125 < deg < −55` → −1 (the phone is on its right edge)
  - Anything else, including upside-down and the 10° hysteresis bands around the 45° diagonals, keeps the current value.

Rotation animates over 250 ms with an ease-out curve. The listener stops while the app is backgrounded or History is on top.

### 2.10 Memory watchdog

- **Heap budget:** 1/16 of the device's total RAM, clamped to between 256 MB and 768 MB.
- **Check interval:** about every 3 s (every 90th new camera image). The first check after a session starts records a heap baseline, measured as native heap allocated plus Java heap used.
- **Trigger:** the system reports low memory, *or* available memory is below 1.5× the system threshold, *or* the heap has grown more than the budget above the baseline. When any of these holds, notify the UI **once per session**.
- **UI:** if the Measure screen is active and no prompt is already showing, show a dialog:
  - Background surface.
  - Title: "Start a new session?" (text, 18 sp, weight 400).
  - Body, when ≥2 points exist: "This session is using a lot of memory. Save this measurement and start fresh to keep things running smoothly."
  - Body otherwise: "This session is using a lot of memory. Start fresh to keep things running smoothly."
  - Buttons: "Not now" (textMuted) and "Save & start new" / "Start new" (green).
- **Confirm:** if ≥2 points exist, save the recording and show a "Saved to History" snackbar. Then close the session and restart it (the full start routine). This discards ARCore's map.
- **Not now:** keep measuring. The prompt won't reappear in this session.

### 2.11 Projecting a saved measurement

"View in AR" on a History measurement sheet opens Measure with that recording projected into the scene. If Measure is already open underneath History, it returns to it (keeping its points); otherwise Measure opens on top of History, and Back returns there. Picking again replaces the projection.

- **What's kept:** a recording's points are world positions from a session that has ended. A new session has its own origin and heading, so the shape can't reappear where it was measured. ARCore's world y axis is always up, so the shape's size, heights and slopes are kept exactly; only where it sits and which way it faces are set anew.
- **Placing it, two taps on the main button:**
  1. **Aiming:** the shape's first point follows the reticle hit, turned so that its reference line points away from the camera. A tap anchors the first point there (a session anchor).
  2. **Aligning:** the shape turns about its first point so that its reference line points at the reticle hit, once the hit is more than 5 cm away horizontally. A tap freezes that heading.
  - The **reference line** runs from the first point to the first later point that is more than 1 cm away horizontally. A recording with no such point (straight up and down) skips aligning.
- **Drawn** before the user's own points and labels (§3.2), in **violet**: lines 2.5 dp, dots radius 4.5 dp, with the usual overlay halos. While aiming or aligning the lines are dashed (dash 6 dp, gap 6 dp); once placed, solid. Each drawn segment gets a label with the recording's saved length and a violet border.
- **Measuring continues** as normal once placed: taps add the user's own pink points, and hold-to-save saves only those.
- **Undo** with no user points left steps back: placed → aligning → aiming. **Clear** removes the projection as well. Leaving Measure ends it.

### 2.12 PDF report

"Export PDF" in History's selection mode (§1.9) writes one report of the selection, in list order, and opens the share sheet. "EXPORT PDF" on the main menu (§1.4) does the same for every saved measurement.

- **Page:** US Letter, 612 × 792 pt, portrait, 54 pt (0.75 in) margins. Drawn with Android's `PdfDocument`, vectors only (lines, circles, text; no bitmaps), one page open at a time. White background.
- **Colours and type:** monochrome and print-friendly: black on a white page, no accent colours, no dark fills. Segments 1.5 pt black with round caps; endpoints black dots (radius 3 pt in the one- and two-cell tiers, else 2.5 pt); text black; cell borders 0.5 pt black. JetBrains Mono, Regular and Bold, embedded.
- **Header (page 1 only), 52 pt tall:** the app icon's mark (§8.1) drawn as vectors in black, 30 × 12 pt (a 1.5 pt rule between two 12 pt tick caps, a 2.5 pt dot on each end), then 10 pt later the wordmark "SHOWDIST" (Bold 16 pt, letter-spacing 0.15 em); below them "Measurement Report · Exported YYYY-MM-DD HH:MM" (10 pt).
- **Footer (every page):** 9 pt on the bottom margin line: "Measured with Showdist v{versionName}" (from `BuildConfig`) on the left, "Page X of Y" on the right. 24 pt is reserved above it.
- **Tiers** (cells fill the space between header and footer, 12 pt gaps):

  | Selected | Grid | Label size |
  |---|---|---|
  | 1 | 1 × 1 | 11 pt |
  | 2 | 1 column × 2 rows | 10 pt |
  | 3–6 | 2 columns × ⌈n/2⌉ rows | 9 pt |
  | 7+ | 3 columns × 4 rows, 12 per page, overflowing onto more pages (same grid) | 9 pt |

- **Cell:** a 0.5 pt border with 12 pt padding. Caption at the bottom (30 pt): "#N · total" (Bold 10 pt), then the date "YYYY-MM-DD HH:MM" (9 pt). N is the position in the selection, from 1.
- **Diagram:** the points projected onto their best-fit plane (PCA). No plane is stored per measurement, so this is always the fallback. On a steep plane (|normal · up| < 0.7, e.g. a wall) world-up stays up and across is horizontal in the plane; on a flat one (a floor) it's seen from above, its widest direction across. A straight line is drawn horizontal, or vertical if it's steep (|direction · up| > 0.7). The diagram is fitted at one uniform scale into the space above the caption, inset by 2.2 × the label size for labels, and centred. Only the geometry scales.
- **Labels:** each segment's true 3D length, formatted per §2.5 in the current unit setting, never below 9 pt. Each sits at its segment's midpoint, pushed 4 pt plus half its box along the segment's normal, on the side away from the diagram's centre, on a white box so it never sits on a line, and moved back inside the cell if it would cross its edge.
- **Crowded labels:** if any label box would overlap another label, come within 3 pt of a point, or cross a segment other than its own, the cell switches to a list: each segment is labelled with its number (1, 2, …, in drawing order) instead, and "N  length" lines are listed at the label size to the right of the diagram, vertically centred, wrapping into more columns if they don't fit (taking at most the right half of the cell). The diagram is refitted into the space left of the list.
- **No points:** a recording with fewer than 2 points is drawn as one horizontal line, 70% of the diagram's width, labelled with its stored total.
- **Output:** `<cache>/exports/showdist_YYYYMMDD_HHMM.pdf`, shared through the FileProvider with `ACTION_SEND`, type `application/pdf`, subject "Showdist — Measurement Report". No storage permissions. Each export first deletes files in `exports/` older than 24 hours. On failure, a snackbar "Could not export PDF: …".

---

## 3. AR reticle and overlay drawing

Everything is drawn on a transparent canvas over the camera feed. The canvas repaints when a new AR frame arrives, about 30 fps, not on every display frame, and nothing repaints while the session is paused. Screen positions are the normalised coordinates × canvas size.

### 3.1 Reticle

On this screen, legibility outdoors and indoors overrides the theme: strokes are brighter and thicker than elsewhere, and every mark sits on a dark halo.

- **Position:** always the **fixed centre of the camera area**, not the projected hit point.
- **Motif:** three small dots orbiting a **white** centre dot. The orbiting dots are in the **unit colour**: **amber** for imperial, **cyan** for metric (§5), following the current unit setting.
  - Ring radius (centre to each dot): **16 dp**.
  - Orbiting dot radius: **3.5 dp**. Centre dot radius: **2 dp**.
- **Rotation:**
  - `angle_k = −90° + 360° × t / 11 s + k × 120°` for k = 0, 1, 2.
  - Screen y points down, so increasing angle is **clockwise**. Dot 0 starts straight up.
  - One full turn every **11 seconds**.
  - `t` is the seconds since the Measure screen opened. It is sampled only when a camera frame arrives, so the spin freezes while paused and then jumps forward to the current time.
- **States:**
  - **On a surface** (reticle hit): the orbiting dots are **filled** at alpha 1, each with a soft **glow**: a 6 dp disc in the unit colour at alpha 0.55, Gaussian blur sigma 3. The centre dot is white at alpha 1.
  - **Searching** (no hit): the orbiting dots are **hollow** rings with a 1.4 dp stroke at alpha 0.6, with no glow. The centre dot is white at alpha 0.6.
- **Halo**, drawn first under every dot: a disc 1.5 dp larger than the dot, halo colour (§5), blur sigma 2. It keeps the reticle visible against bright surfaces.

### 3.2 Placed points, segments, live line

**Placed points and segment lines** (the icon's colours, §8.1):
- Placed points are filled circles in **pink**, radius 6 dp. They are drawn only when visible.
- Segment lines are **green**, 2.5 dp wide with round caps. One is drawn between consecutive points only if both endpoints are visible.

**Live line:**
- A dashed line from the last point to the reticle hit's projected screen position.
- It is **white**, 3 dp wide with round caps; its visible dashes and gaps are 6 dp each (the dash intervals are 3 dp on, 9 dp off, allowing for the caps).
- It is drawn only when there is a hit, ≥1 point, and the last point is visible.

**Overlay halo** (for points and lines): a copy drawn first, centred (no offset), in the halo colour with blur sigma 1.2; lines 3 dp wider (1.5 dp each side), dots 1.5 dp larger in radius.

**Draw order:**
1. A projected recording (§2.11)
2. Segment lines
3. The dashed live line
4. Point dots
5. The reticle
6. Labels (projected ones first)

### 3.3 Distance labels

- There is one label per drawn segment, placed at the segment's midpoint, and one **live** label at the midpoint of the dashed line.
- A label is **hidden** if its midpoint is off-canvas. Otherwise its centre is clamped so the whole pill stays at least 8 dp inside the canvas edges.
- **Text:** JetBrains Mono **Bold**, 14 sp, white, tabular figures, slashed zero.
- **Pill:**
  - Text size plus 20 dp horizontal and 10 dp vertical (10 dp side padding, 5 dp top/bottom).
  - Fully rounded, with radius half the pill's height.
  - Filled with **scrim** (§5).
- Each label has a 1.5 dp border: **green** on a placed segment's label, **white** on the live label, **violet** on a projected recording's label.

---

## 4. Leveler

### 4.1 Behaviour

- **Purpose:** two levels, each opened from its own tile on the main menu (§1.4). The **tube** is a single-axis bubble level for hanging shelves or frames, used by holding the phone flat against a wall. The **bullseye** is a two-axis level used by laying the phone face up on a surface.
- **Sensor:** the accelerometer at "game" rate, about 50 Hz (20 ms). If no accelerometer is present, errors are ignored and the bubble stays centred.
- **Smoothing:** a one-pole low-pass filter on `x/g`, `y/g` and `z/g`, with g = 9.80665 m/s² and α = 0.15.
  - `est = est + (sample − est) × 0.15`
  - The estimate starts at (0, 0, 1).
- **Roll (the tube's single axis)**, from the smoothed device-frame x and y. The z axis is ignored.
  - `θ = atan2(x, y)` in degrees
  - `roll = θ − round(θ / 90) × 90`
  - So roll is measured against the **nearest** of portrait, landscape-left or landscape-right. The same formula works in every orientation without asking which one the UI is currently in. It ranges from −45° to +45°.
  - Sign: rotating the phone clockwise (viewed from the front) produces a negative reading.
- **Tilt X / Y (the bullseye's two axes)**, for a phone lying face up:
  - `tiltX = atan2(x, z)`, `tiltY = atan2(y, z)` in degrees.
  - Positive when the right edge (X) or the top edge (Y) is the higher one.
- **Zero / tare:**
  - **Zero** (app-bar action, `center_focus_strong_rounded`, tooltip "Zero — null against an uneven surface") stores the current roll as the offset (the bullseye stores tiltX and tiltY as two offsets) and plays a selection-click haptic.
  - **Reset** (`restart_alt_rounded`, tooltip "Reset to true level") sets the offset back to 0, also with a haptic. It is disabled while the offset (both offsets, for the bullseye) is exactly 0.
  - The offset isn't persisted. It resets each time the screen opens.
  - `displayed = roll − offset`; for the bullseye, `displayedX = tiltX − offsetX` and `displayedY = tiltY − offsetY`.
- **Level threshold:** |displayed| ≤ **0.3°** counts as level; for the bullseye, √(displayedX² + displayedY²) ≤ 0.3°.
- **Full scale:** ±**15°** pushes the bubble to the end of the vial, or to the bullseye's rim.
- **Orientation:**
  - The bullseye Leveler stays portrait: lying flat, the phone can't sense which way is up on screen.
  - While the tube Leveler is open, the screen may rotate between portrait, landscape-left and landscape-right, but not upside-down.
  - On exit, the screen is forced back to portrait-only.
  - The vial always runs along the screen's **long** axis: vertical in portrait, horizontal in landscape.

### 4.2 Tube Leveler visuals

Drawn as a constellation on black: thin outlines, diamond-star scale marks joined by faint lines, and an accent-coloured bubble.

- **Background:** black. The app bar is the standard bar titled "TUBE LEVELER", with the Zero and Reset actions on the right.
- **Body** (inside the safe area):
  1. Instruction "HOLD FLAT AGAINST THE SURFACE" in the Caption style at 11 sp with letter-spacing 2, in textMuted, padding L32 T20 R32.
  2. The **vial** fills the remaining space and is centred.
  3. The **readout**: `|displayed|` to one decimal place followed by "°", e.g. `0.4°`. It is **64 sp, weight 700**, tabular figures, text colour, or **green** when level; large enough to read from about a foot away. 24 dp bottom padding. It shows no sign.
- **Vial**, a stadium-shaped rounded bar:
  - Thickness 84 dp.
  - Length = the long dimension of the available space minus 2 × 28 dp.
  - Corner radius 42 dp.
  - Filled white at alpha 0.04, with a 1.5 dp outline in line (§5).
- **Scale:** a row of diamond stars down each long side, 16 dp outside the outline, one per degree from −15° to +15°, each where the bubble's centre sits at that reading (`deg / 15 × travel` from centre). Every 5th is major (half-diagonal 3.5 dp, white at alpha 0.85); the others are minor (1.5 dp, white at alpha 0.4); the 0° star is violet, or green when level. Each row is joined by a 1 dp line in the grid colour. The 5°, 10° and 15° stars on one side are labelled "5°", "10°", "15°" (11 sp, textMuted, tabular), 10 dp further out: beside the star in portrait, under it in landscape.
- **Centre marks:** two lines across the vial, one on each side of centre.
  - Each sits `56/2 + 5 = 33` dp from centre along the vial axis.
  - Each spans ±(84/2 − 10) = ±32 dp across the vial.
  - Normally 1.5 dp in text at alpha 0.7. When level, 3 dp in **green**.
- **Bubble:**
  - A filled circle, diameter 56 dp: **violet**, or **green** when level, with a soft halo: an 8 dp stroke of the same colour at alpha 0.25 around its outline.
  - Its position comes from the bubble motion (§4.4). `travel = length/2 − 2 − 28`: the bubble's centre runs from the middle to where its edge touches the inner wall.
  - A positive reading moves the bubble down in portrait and right in landscape. So a clockwise rotation moves it up in portrait and left in landscape.

### 4.3 Bullseye Leveler visuals

- The same background, app bar actions and style as the tube, titled "BULLSEYE LEVELER".
- **Body:**
  1. Instruction "LAY FLAT ON THE SURFACE", in the tube's instruction style.
  2. The **bullseye** fills the remaining space and is centred.
  3. The **readouts** in one row with 24 dp bottom padding: "X" then |displayedX|, a 24 dp gap, "Y" then |displayedY|. Each value uses the tube's readout style at **48 sp** (two 64 sp values don't fit side by side), both green when level. Each letter is the Caption style at 16 sp in violet, letter-spacing 0, 8 dp before its value, sitting near the value's baseline.
- **Bullseye:** a disc of radius = half the shorter dimension of the available space − 28 dp.
  - Filled white at alpha 0.04, with a 1.5 dp outline in line.
  - Faint 1 dp rings (grid colour) where the bubble's centre sits at **5°** and **10°** of tilt.
  - Crosshairs: horizontal and vertical 1 dp lines (grid colour) from just outside the centre ring (8 dp gap) to the rim.
  - Diamond stars where the crosshairs meet the 5° ring (2 dp, white at alpha 0.5), the 10° ring (2 dp, alpha 0.7) and the rim (3.5 dp, alpha 0.9), plus four on the diagonals at 10° (2 dp, alpha 0.35).
  - **Centre ring:** radius 56/2 + 5 = 33 dp. Normally 1.5 dp in text at alpha 0.7; when level, 3 dp **green**.
- **Bubble:**
  - A filled circle, diameter 56 dp: **violet**, or **green** when level, with the tube's halo.
  - It moves toward the higher side: right for positive X, up the screen for positive Y.
  - Its position comes from the bubble motion (§4.4), in two dimensions. `travel = radius − 2 − 28`: the bubble's centre runs from the middle to where its edge touches the rim.

### 4.4 Bubble motion and shape

Both levelers share this.

- **Target:** `displayed / 15` along the vial (the tube), or `(displayedX, −displayedY) / 15` (the bullseye, screen y running down), where 1 means the bubble touches the wall. Past 1 the target is clamped back to the wall, except that the bubble may press on into the wall by up to 30% of its radius, growing with how far past full scale the reading is (×2, capped).
- **Motion:** a spring-damper toward the target, stiffness 90 s⁻² (about 1.5 Hz) and damping ratio 0.5, so the bubble eases in and settles with a slight wobble instead of snapping. It's stepped once per display frame (`withFrameNanos`) in substeps of at most 1/120 s, with a frame's time capped at 50 ms. The sensor input is already low-passed (§4.1). At the press limit the bubble stops and bounces back at 25% of its speed into the wall.
- **Shape:** drawn each frame as a 64-point polygon in one reused path, filled like the round bubble (violet, or green when level), with its halo.
  - **Velocity stretch:** an ellipse with the round bubble's area, radius × s along the direction of travel and radius ÷ s across it, where `s = 1 + min(speed / (25 × radius), 0.4)` with speed in px/s. At rest it's round.
  - **Wall contact:** any outline point past the wall's inner edge (the outline's inner edge plus a hairline, 2 dp in) is moved back onto it, so a bubble pressed against the vial's round end or the bullseye's rim flattens to follow that curve instead of overlapping it.

---

## 5. Colour palette

The palette is fixed and app-wide, after the Android 17 Easter egg: a night-sky field (true black), near-white text, and bright, saturated, playful accents. It replaces the earlier Sanzo Wada palette entirely. The accents are the Easter egg's fallback set; swap them in `ui/Palette.kt` if sampled from the egg. There is one theme only; no light/dark modes, and no default Material colour appears anywhere.

| Token | Hex | Used for |
|---|---|---|
| **black** | `#000000` | Background of every screen, app bars, the Measure control bar, launch/splash, app-icon tile. |
| **surface** | `#121212` | Dialogs, bottom sheets and popup menus. |
| **surfaceHigh** | `#1C1C1C` | The main menu's tool card and tiles (alpha 0.92). |
| **text** | `#F2F2F2` | Primary text, titles, bar icons (alpha 0.9 / 0.3), the Leveler readout and centre marks (alpha 0.7). |
| **textMuted** | `#B0B0B0` | Secondary text: descriptions, captions, subtitles, the how-to text, empty states, dialog bodies, cancel buttons; chevrons, the gear, unselected radios. |
| **white** | `#FFFFFF` | Over the camera: the reticle centre dot, live line, label text, hint, running total, charge arc. Diamond stars (alpha variants). |
| **grid** | white at 10% (`#1AFFFFFF`) | The menu's background grid, dividers, the Leveler's faint rings, crosshairs and scale lines. |
| **line** | white at 25% (`#40FFFFFF`) | Outlines that should read but stay quiet: the Leveler vial and dish, the control bar's top rule, outlined-button borders, the menu's constellation lines (alpha variant). |
| **green** | `#3DDC84` | Primary accent (the Material primary). Measure card, History/Export sub-links, the main button, segment lines and their label borders, the app-icon rule, "View in AR", selection in History, "level" on the Leveler (bubble, marks, ring, readout), Licenses spinner, Problem-view button. |
| **violet** | `#B388FF` | The Leveler: tiles, bubble, axis letters, 0° star; a projected recording (§2.11); License detail heading. |
| **pink** | `#FF6E9C` | Placed AR points, the app-icon end points, "Delete selected", the Problem-view circle. |
| **amber** | `#FFB300` | **Reserved for unit state: imperial (ft, in).** Reticle dots, the FT label when active, the selected Imperial radio. Never a general accent. |
| **cyan** | `#00E5FF` | **Reserved for unit state: metric (m, cm).** Reticle dots, the M label when active, the selected Metric radio. Never a general accent. |

**Scrims and halos** (derived colours):

| Name | Value | Used for |
|---|---|---|
| scrim | `#B8000000`, black 72% | Behind every piece of live text over the camera: distance labels, the running total, the hint, the menu button |
| halo | `#99000000`, black 60% | Under the reticle (blur 2) and every point and line (blur 1.2), §3 |

**Contrast** (on black): text 18.8:1, textMuted 9.7:1, green 11.8:1, violet 7.9:1, pink 8.0:1, amber 11.7:1, cyan 13.7:1. White on the scrim over a pure-white scene is 9.3:1.

---

## 6. Typography, wordmark, menu background

### 6.1 Font

- **JetBrains Mono** is the only typeface, used everywhere, including text drawn on the AR canvas.
- **Bundled faces:** Regular (400), Bold (700), Italic, and Bold Italic. The code also asks for weights 200, 300, 500, 600 and 800. With only 400 and 700 bundled, those render using the nearest bundled face.
- The OpenType **`zero`** feature (slashed zero) is enabled on **all** text.
- **Tabular figures** (`tnum`) are also enabled on the running total, distance labels, sheet segment lengths, the Leveler readout and its scale labels.
- Base Material type scale: Material 3, with text-colour text. Material 3 Expressive shapes are approximated with the theme's rounder corners (8 / 12 / 20 / 28 / 36 dp for extra-small to extra-large) and pill buttons; material3 1.4 has no `MaterialShapes`.

### 6.2 SHOWDIST wordmark

- The text "SHOWDIST": one word, all caps, JetBrains Mono, **22 sp**, weight 700, letter-spacing 3, text colour. Modest, slightly larger than body text.
- It sits at the top left of the menu's header block (§1.4), followed by the dimension mark.
- The app icon has no lettering.

### 6.3 Caption style

- 12 sp by default, weight 600, letter-spacing 3, centre-aligned.
- Colour is textMuted by default. App-bar titles override it to text.
- Used for app-bar titles, section headers, sheet titles, "WAKING THE CAMERA", "HOW TO USE" and the Leveler instruction (11 sp, letter-spacing 2).

### 6.4 Main-menu background (night sky)

Static, drawn behind the content:
- **Grid:** 1 dp lines in the grid colour every 32 dp, both ways, from the top-left.
- **Stars:** fifteen diamond stars (half-diagonal 3 dp), at fixed positions in grid cells from the top-left (a negative column counts from the right edge): four in the band above the header (cells (1.5, 1), (4, 1.6), (6.5, 0.8), (9, 1.4)), five down the left margin (column 0.2 / 0.45 at rows 5, 8, 11, 19, 23) and six down the right margin (column −0.2 / −0.45 at rows 4, 7, 13, 17, 22, 26). White ones are at alpha 0.45; one violet, one green and one pink at alpha 0.8.
- **Constellation lines:** 1 dp, line colour at alpha 0.14, joining neighbouring stars in each group.
- **Kept clear of text:** the header block and the tools report their bounds; no star, and no line, is drawn within 6 dp of them, so nothing sits behind anything to read, whatever the screen or font size.

---

## 7. About text & third-party licences

### 7.1 About text (verbatim)

On the main menu (§1.4):
- `v1.1 · Carlos Cruz · sh.run.configs@gmail.com` (version from the installed package)
- `Created with AI assistance` (italic)

In Settings: `Licenses`, with subtitle `ARCore, SceneView, AndroidX and JetBrains Mono (OFL-1.1)`.

### 7.2 Third-party components

In the current Flutter build, the Licenses screen lists every entry in Flutter's license registry. That covers the Flutter engine and SDK packages, every Dart package (direct and transitive), and the manually added JetBrains Mono entry. ARCore is named in the About subtitle but is **not** actually registered, because native Android libraries don't appear in Flutter's registry.

Direct dependencies of the current build:

| Component | Version | Purpose | License |
|---|---|---|---|
| Google ARCore SDK (`com.google.ar:core`) | 1.56.0 | AR session, hit tests, anchors | Google ARCore SDK terms; check the AAR's bundled notice |
| JetBrains Mono font | bundled TTFs | Only typeface | SIL OFL 1.1, © 2020 The JetBrains Mono Project Authors |
| Flutter SDK | 3.47.5 stable / Dart 3.13.4 | UI framework | BSD-3-Clause (not needed in Kotlin) |
| permission_handler | 12.0.3 | Camera permission | MIT (Kotlin: use AndroidX Activity Result API) |
| share_plus | 11.1.0 | Android share sheet | BSD-3-Clause (Kotlin: `Intent.ACTION_SEND` + `FileProvider`) |
| shared_preferences | 2.5.5 | Units setting | BSD-3-Clause (Kotlin: SharedPreferences / DataStore) |
| sensors_plus | 7.1.0 | Accelerometer | BSD-3-Clause (Kotlin: `SensorManager`) |
| Material Icons | — | UI icons (list in §10) | Apache 2.0 |

For the Kotlin rebuild, the Licenses screen should list at least **ARCore**, **JetBrains Mono (OFL-1.1)**, **Material Icons**, and every AndroidX/Kotlin library actually shipped. Using a license-collection plugin (e.g. AboutLibraries or `oss-licenses-plugin`) is recommended. Update the About subtitle and description sentence to drop "Flutter".

---

## 8. App icon & assets to copy

### 8.1 Icon spec

The icon is an Android **adaptive icon** built from vector drawables on a 108×108 canvas. The visible area is the inner 72 dp circle, radius 36, centred at (54, 54). It has no lettering.

- **Background layer:** solid black `#000000`.
- **Foreground layer:** a dimension line, well inside the 66 dp safe zone:
  - green (`#3DDC84`) strokes, width 3.5, round caps: a horizontal rule from (34, 54) to (74, 54) and an end tick at each end, x = 34 and x = 74, from y = 43 to y = 65.
  - Two pink (`#FF6E9C`) dots, radius 6, at (34, 54) and (74, 54).
- **Monochrome layer** (Android 13+ themed icons): the same shapes in one colour.
- **Pre-API-26 fallback:** a 72×72 flattened vector with a black disc (radius 36) under the same shapes.

### 8.2 Files to copy into the Kotlin project

| Source path (this repo) | Purpose |
|---|---|
| `assets/fonts/JetBrainsMono/JetBrainsMono-Regular.ttf` | Font 400 (rename to lowercase with underscores for `res/font/`) |
| `assets/fonts/JetBrainsMono/JetBrainsMono-Bold.ttf` | Font 700 |
| `assets/fonts/JetBrainsMono/JetBrainsMono-Italic.ttf` | Font 400 italic |
| `assets/fonts/JetBrainsMono/JetBrainsMono-BoldItalic.ttf` | Font 700 italic |
| `assets/fonts/JetBrainsMono/OFL.txt` | Font license text (ship it and show it in Licenses) |
| `android/app/src/main/res/drawable/ic_launcher_foreground.xml` | Icon foreground |
| `android/app/src/main/res/drawable/ic_launcher_monochrome.xml` | Themed-icon layer |
| `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon definition |
| `android/app/src/main/res/mipmap-anydpi-v21/ic_launcher.xml` | Pre-adaptive fallback (minSdk 24 still needs it for API 24–25) |
| `android/app/src/main/res/values/ic_launcher_background.xml` | Icon background colour |
| `android/app/src/main/res/values/colors.xml` | `launch_background` colour |
| `android/app/src/main/res/drawable/launch_background.xml`, `drawable-v21/launch_background.xml` | Pre-12 launch screen |
| `android/app/src/main/res/values/styles.xml`, `values-v31/styles.xml` | Launch/Normal themes and Android 12 splash colour (re-parent onto your AppCompat/Material theme as needed) |
| `LICENSE` | MIT license |

The repo has no raster images or other assets. The native AR layer already exists in Kotlin under `android/app/src/main/kotlin/com/showconfigs/showdist/` (`ArMeasureController.kt`, `ArMeasureView.kt`, `BackgroundRenderer.kt`). It implements §2.1, §2.4, §2.5 and §2.10 and can be reused if desired. Its Flutter method/event-channel plumbing would be replaced with direct calls.

---

## 9. Known problems

1. **AR performance degrades and the app runs out of memory after 2–3 measurements.** The developer reports this; it was not reproduced while writing this spec.
   - The existing mitigation is the memory watchdog in §2.10. It only *offers* a session restart, once per session, and only after the heap has grown by 256–768 MB or the system is low on memory. That is evidently too late or too weak.
   - Traits of the current implementation that likely contribute (unverified; investigate in the rebuild):
     - Saving or clearing only detaches anchors. The **same ARCore session keeps running** across measurements, so its planes, feature-point map and depth data keep growing. Only closing the screen or accepting the memory prompt releases them.
     - **Depth mode AUTOMATIC** is enabled on supporting devices, which adds significant per-frame native memory.
     - The camera view is hosted as a Flutter platform view using **hybrid composition** (an "expensive" Android view), rendered by a continuously redrawing GLSurfaceView at the display refresh rate. Each new camera frame is also copied into a fresh array and posted to the main thread about 30 times a second.
     - The camera texture and GL program are never explicitly deleted when the view is disposed. When the session changes, the anchor list is cleared without detaching anchors (the old session is closed, though).
   - Suggestions for the rebuild:
     - Render the camera and overlay natively in one view, e.g. a single GLSurfaceView or SurfaceView plus a Canvas/Compose overlay, without a composition bridge.
     - Consider disabling depth or making it optional.
     - Consider starting a fresh session automatically after each save, or after N measurements.
     - Release GL resources on dispose.
     - Profile with Android Studio's Memory Profiler across 5 or more consecutive measurements.
2. **Emulator:** ARCore session creation failed on an API 36 x86_64 emulator image ("no default camera 0"). A physical device is required.
3. **Accuracy:** roughly 1–2 cm in good conditions. It is worse on plain, featureless or shiny surfaces and in low light.
4. **Platform:** Android only. Portrait only, except the Leveler.
5. The release build is signed with the debug key. A real signing config is needed before distribution.

---

## 10. Icons used (Material Symbols, rounded style)

settings (outlined), history, chevron_right, arrow_forward, arrow_back, undo, close, ios_share, content_copy, select_all, delete_outline, check_circle, circle (outlined), radio_button_checked, radio_button_unchecked, center_focus_strong, restart_alt, picture_as_pdf.

## 11. Haptics summary

| Event | Haptic |
|---|---|
| Place point tap | selection click |
| Hold-to-save charge begins | selection click |
| Save completes | medium impact |
| Leveler Zero / Reset | selection click |

## 12. Timing constants summary

| Constant | Value |
|---|---|
| Reticle period | 11 s per clockwise turn |
| Save charge | 800 ms (after ~500 ms long-press recognition) |
| Charge cancel rewind | 200 ms |
| Hint cross-fade | 250 ms |
| Bar-button rotation | 250 ms, ease-out |
| Orientation sensor | ~5 Hz |
| Leveler sensor | ~50 Hz, low-pass α = 0.15 |
| Plane recount | every 10 new camera frames |
| Memory check | every 90 new camera frames |
| Max points per measurement | 24 |
| Text truncation | 20 segments (single share/copy) |

## 13. Discrepancies noted while writing this spec

- **Palette names:** the code's `Palette` tokens use the §5 names (`Palette.Surface`, `Palette.TextMuted`, …); `Palette.unit(units)` picks amber or cyan.
- **README's Leveler section is out of date:** it describes "the dot centres in the ring" and a seaGreen bubble when plumb. The code draws the constellation-style vial and bullseye of §4.2–4.3, with a violet bubble that turns green when level.
- **About text:** the description and the licenses subtitle mention Flutter, and the subtitle claims ARCore's license is listed even though it isn't registered (§7.2). Both should be revised for the Kotlin build.
