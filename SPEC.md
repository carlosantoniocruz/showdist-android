# Showdist — Functional & Visual Specification

A complete description of the Showdist Android app (currently Flutter + a native ARCore layer), written so it can be rebuilt natively in Kotlin without reference to the existing source.

All sizes are in density-independent pixels (dp; for text, sp). "Alpha" is opacity from 0 to 1. Angles are in degrees unless noted. Where this spec disagrees with the README, the spec follows the actual code (see §13).

---

## 0. App identity & platform

| Item | Value |
|---|---|
| App name (launcher label) | Showdist |
| Application ID / namespace | `com.showconfigs.showdist` |
| Version | 1.2 (version code 3) |
| Min SDK | 24 (ARCore requires 24+) |
| Target / compile SDK | Latest stable (was Flutter's default, platform 36) |
| JVM target | 17 |
| AR library | Google ARCore `com.google.ar:core:1.56.0`, **required** (manifest meta-data `com.google.ar.core = required`) |
| Permissions | `CAMERA` |
| Required features | `android.hardware.camera.ar`, OpenGL ES 2.0 |
| Orientation | Activity locked to **portrait**, except the tube Leveler, which allows portrait, landscape-left and landscape-right while it is open, then restores portrait-only on exit |
| System UI | Immersive sticky (status and navigation bars hidden; they reappear briefly on swipe) |
| Screen timeout | Screen kept on (`FLAG_KEEP_SCREEN_ON`) while the app is in the foreground |
| Theme | One fixed theme (black, white and light-grey text, a single sandstone accent; §5). It ignores the system light/dark setting |
| License | MIT, © 2026 carlosantoniocruz |

---

## 1. Screens and navigation

### 1.1 Screen map

```
Launch screen (black field)
  └─ Main Menu (landing; the orbital selector)
       ├─ AR Measure (moon)  ──► History ──► Measurement detail sheet
       │        └─► "Saved" sheet (after hold-to-save)
       ├─ Tube Level (moon)
       ├─ Bullseye Level (moon)
       ├─ History (top row; only shown once ≥1 measurement is saved) ──► Measurement detail sheet
       │        └─► "View in AR" ──► Measure, projecting it (§2.11)
       └─ Settings (top row, right; About is part of it, §1.6)
             └─ Licenses
                  └─ License detail (one package)
```

Every screen is a full-screen page pushed on a back stack. The system Back button/gesture and each app bar's back arrow (`arrow_back_rounded`, tooltip "Back") pop one level. The main menu is the root; Back there exits the app.

### 1.2 Shared app-bar style (Settings, About, Licenses, License detail, History, Leveler, calibration screens)

- Background black, no elevation, no surface tint. Foreground (icons, title) white (text).
- Title centred and set in the **Caption** style (§6.3) in white: "SETTINGS", "ABOUT", "LICENSES", "LICENSE", "HISTORY", "TUBE LEVELER", "BULLSEYE LEVELER".
- No back arrow at the top (v1.4). **Back lives at the bottom** (`BackBar`, a Scaffold bottom bar): a bare sandstone `arrow_back_rounded` (30 dp) centred in a 56 dp round touch target with a ripple, content description "Back", on black, above the bottom safe-drawing and mandatory-gesture insets with 4 dp above and below, so it sits where the menu's History and Settings icons do. In History's selection mode it leaves selection first. On the Tube level in landscape (the only screen that rotates, §4.1) a bottom bar would take the height the vial and readout need, so the same back button floats in the bottom-start corner instead (8 dp in from the safe-drawing insets). The system back gesture works everywhere as before. Actions (History's menu, the Leveler's Zero and Reset, selection actions) stay in the top bar.

### 1.2a Dialogs (v1.4, `ui/ShowdistDialog.kt`)

Every pop-up uses one compact style, with no wasted space: a card on Surface with 20 dp corners, at most 360 dp wide (24 dp kept clear at the screen's sides), padding 18 dp at the sides and 16 dp on top, an optional title with 10 dp under it, then the content, then the buttons right-aligned close beneath (text buttons: "Cancel"-style in grey, the confirming one in bold sandstone; 4 dp bottom padding). **Text follows the main menu's sizes** (the guide's 0.86 scale, §1.4): titles like a tool's name (Title × 0.86, about 18 sp, regular), everything else like its paragraph (Body × 0.86, about 14 sp): messages in grey, text fields, button labels, and the prompt's descriptions. The prompt's option names are bold sandstone at the title size, in buttons ≥56 dp tall. No icon slot. Used by the Chained/Group prompt ("How will you measure?"; its two buttons ≥64 dp, 8 dp apart), the group-name and line-label dialogs, and the delete confirmations in History and on a group's screen.

### 1.3 Launch screen

- Pre-Android 12: the window background is a plain **black (#000000)** field. No logo.
- Android 12+: the system splash screen shows the app icon on `windowSplashScreenBackground` = black.
- The window background behind the app while it starts is also black, so nothing flashes white.

### 1.4 Main Menu (landing screen)

Plain black, no background graphics. Content sits inside the system safe area in a column: a slim top row, the orbital selector filling the space, and the quick guide beneath it. The main menu never touches the camera or the AR session.

**Bottom buttons** (v1.4, one-handed; `menu/MenuButtons.kt`): no top row; 16 dp of space at the top. Under the quick guide (whose bottom padding is 12 dp, so it sits just above them), a horizontally centred row, 40 dp apart, of two **bare icons** with no container: **History** (`history_rounded`, only once ≥1 recording is saved; content description "History, N saved") and **Settings** (`settings_outlined`). With History hidden, Settings centres alone. Each is a 30 dp sandstone icon in a 56 dp round touch target (a ripple shows when pressed). Below the row: 4 dp (lowered at the user's request from the brief's 16–24 dp), on top of the bottom mandatory-gesture inset (after the safe-drawing padding), so the icons sit low but stay clear of the gesture area; with the 56 dp touch target, the icon's centre is about 65 dp from the bottom edge on a Pixel 10a. There's no count badge. On wide screens the row is centred like the guide (§6.5). History is not a moon: it's saved data, not a tool. Export PDF is not on the menu; History exports any selection.
- **History pulse:** while recordings exist, History glows on a loop: one animated value goes 0 → 1 → 0 over **3.6 s** (ease in-out, reversing). It drives two layers' alpha only: a soft-white (#FFF8EE) copy of the icon over the sandstone one, and a pre-rendered halo of the icon's own shape (the icon drawn white and blurred 6 dp, once per size, into a bitmap, at 70% × value) behind it. Both layers modulate alpha (no offscreen buffer), so nothing redraws. The pulse pauses while the fun facts cover the menu and stops whenever the menu isn't on screen; with reduced motion it holds a steady soft-white tint (value 0.6).

**Orbital selector** (`menu/Orbit.kt`), sized from the space it's given (w × h):
- **Sun**, a dying star, flat and minimal (no glow, gradient or particles): a sandstone disc of radius `sunR = min(0.27w, 0.30h, 124 dp)`, with one shed-shell ring at 1.22 × sunR (1.5 dp, sandstone at alpha 0.45). "SHOWDIST" sits across it in black, weight 700, letter-spacing 0.12 em, sized to span 1.5 × sunR and raised 0.12 × sunR above centre. It ignores the system font scale (it's the app's mark). TalkBack reads it as a heading, "Showdist".
- **Orbit:** an ellipse centred on the sun, rx = min(0.40w, w/2 − 0.75 × moonR), ry = 0.8 × sunR, drawn as a 1 dp line in the line colour: its far half behind the sun, its near half over it. The shell top and front moon bottom are centred together vertically.
- **Moons**, one per tool, in order AR Measure, Tube Level, Bullseye Level, spaced 120° apart. Radius moonR = 0.4 × sunR at the front. A moon at angle θ from the front sits at (cx + rx·sin θ, cy + ry·cos θ); with depth d = cos θ and n = (d + 1)/2, it's scaled 0.6 + 0.4n and always drawn **fully opaque** (v1.4) and **darkened by depth**: an opaque black disc over its body, ring and lines at alpha 0.75 × (1 − n) on its own layer, lifted by the collapse's glow (× (1 − glow)); the white dots are drawn above the shading and stay bright, in front of the sun when d ≥ 0 and behind it otherwise. Each is a black disc (3 dp gap ring) over a surface-coloured disc, with a ring (2.5 dp sandstone when focused, 1.5 dp textMuted otherwise) and the tool's picture at half the radius, its lines in sandstone and its **dots pure white** as on the app icon (v1.4): a dimension line with end ticks (±0.55 of the half-length) and a white dot on each end (radius 0.3 of the half-length, the icon's 6 of 20) (Measure); a stadium vial with a white bubble (Tube); two rings and a white centre dot (Bullseye). The dots are drawn as vector circles, white in every state (focus only changes the ring; the press ripple and the egg's light don't tint them), each with a faint dark edge (black 35%, 0.75 dp) so they still read when the collapse lights the moon toward white.
- **Swipe:** a horizontal drag turns the orbit directly, one moon per **1.6 rx** of travel (v1.4, less sensitive than before); dragging left brings the right-hand moon to the front.
- **Snap (v1.4, more resistant):** on release, the drag plus a 0.12 s look-ahead of its velocity must have turned the orbit at least **0.35 of a moon** from where the drag started; then it moves on exactly one moon in that direction, otherwise it springs back. A spring (damping 0.85, stiffness 300) carries it there from the release velocity. Each time the orbit settles on a different moon, by swipe or tap, a light **selection tick** plays (§11).
- **Taps:** tapping a moon that isn't focused turns the orbit the shortest way until it's at the front. Tapping the focused moon once the orbit has settled opens its tool. First tap focuses, second tap launches.
- **Reduced motion:** with the system's "Remove animations" on (animator duration scale 0), snaps and focus changes jump to the end, and the guide appears without sliding.
- **Accessibility:** each moon is a button with content description "AR Measure" / "Tube Level" / "Bullseye Level", selected when focused, and click label "Show guide" (unfocused) or "Open" (focused). The selector also offers custom actions "Next tool" and "Previous tool".
- **The collapse** (Easter egg, `menu/CollapseEgg.kt`): pressing and holding the sun is timed like Android 16's own Easter egg (`PlatLogoActivity`: a 5 s warp ramp, launching 1 s later).
  - **One progress value** p, 0 at rest to 1 at collision, rises linearly over **6 s** of holding (v1.4). Everything follows it: the spin speed (up to four turns a second, ∝ p²); the **pull** towards the sun, a smoothstep of p, shrinking the moons to 40% at the centre; and the **glow** (= pull). As the glow rises, a white disc fades over the sun on its own layer (sandstone → white), a pre-rendered radial glow behind the sun (2 sun radii, scaled 0.8–1.2) crossfades from sandstone to white, the moons are tinted white (a white disc over each body at 85% × glow). The SHOWDIST wordmark washes out with the light: a white copy fades in over it at 85% × glow, leaving a faint grey on the white sun at collision (hidden from TalkBack). The glows are two bitmaps drawn once per size; nothing is blurred live and only layer alphas and scales change per frame.
  - **Haptics:** a pulse that quickens from every 250 ms to every 40 ms and strengthens with p (§11), then a crisp confirming tap at collision.
  - At **collision** (p = 1) the fun facts scene opens with a **flash**: white over the whole screen in 120 ms, then fading into black over 320 ms, so it never strobes. The orbit resets unseen behind it 300 ms later.
  - Each collision **plays the fun facts** (v1.4, replacing the ticker): after the flash (or a 600 ms fade to black with reduced motion) the facts roll up it, credits style, at **40 dp/s**, centred in a column at most 560 dp wide with 32 dp side padding. Each fact is in the Secondary style in sandstone, centre-aligned; its source, if any, sits 6 dp beneath in 12 sp TextMuted. Facts are separated by a dim "✦" (Divider colour) with 22 dp either side. Fixed 112 dp black-to-clear gradients at the top and bottom edges fade the text to black there (the screen is portrait-only, so there are no side fades). Only the column's layer translation changes per frame.
  - **Shuffle:** each collision deals the whole pool in a new random order; when that round has scrolled off the top, the next shuffled round starts from the bottom, never beginning with the fact that just ended. No fact repeats until every one has shown.
  - Tapping anywhere or going back fades the scene out over 350 ms, back to the menu. Content description "Fun facts. Tap to close."
  - Movement is the finger's net distance from where it went down (never the summed reports: Samsung screens report a resting finger as constant jitter). Until the long-press timeout (~500 ms) a move beyond touch slop hands the gesture to the orbit's swipe; after it, the hold consumes every event so the orbit can't take it, and allows 48 dp of wander. Lifting, or sliding further than that, before 6 s lets go: over 700 ms (ease-out) p eases back to 0, so the moons drift back out, the sun's colour and glow return to sandstone, and the spin coasts on to the next whole turn; nothing is ever left mid-animation. While any of this runs, taps on moons are ignored. A swipe that starts on the sun still turns the orbit.
  - With reduced motion, holding 6 s gives the haptics and shows the facts without the spin, pull, glow or flash: one fact at a time, still, centred; each tap shows the next (a new shuffled round after the last), and back closes it.
  - **The facts** live in `res/raw/fun_facts.txt`: one block per fact, blank-line separated, the fact's text then an optional `source: NAME` line; `#` lines are comments. Surprising or hard-to-believe facts carry a short source (BIPM, NIST, NASA, Britannica, IAU, CIA World Factbook, …); common knowledge needs none; legends are phrased "According to legend". Every fact was checked against a reliable source before being added (v1.4 review: the Great Pyramid "level to 2 cm" claim was dropped, as sources disagree; the granite cubit-rod claim was reduced to the cubit's definition and surviving rods' ~52 cm; the three non-metric countries are attributed to the CIA World Factbook; the Giza water-trench levelling is attributed to Lehner and Edwards as a suggestion).
- The focused moon is held by the app, not the menu, so the tool last used is still in front on return; it also survives rotation and process death.

**Orbit and guide placed for the thumb** (v1.4): the orbit gets only the height its span needs (2.42 sun radii + 32 dp, sun radius min(27% of width, 124 dp), shrinking on short screens, at least 160 dp), and the guide exactly the height of the tallest tool's guide (all three are laid out invisibly to size it, so nothing is reserved beyond the text and nothing moves as tools change). The height left over is shared by weight: **70% above the orbit** (so the whole group sits low, within thumb reach), then 10% each between the orbit and the guide, the guide and the closing line, and the closing line and the bottom buttons. Each tool's title sits at the same height. **Swiping anywhere on the guide** turns the orbit exactly as swiping on the orbit does (same step and fling); taps and holds there do nothing, and only holding the sun starts the collapse.

**Quick guide**, beneath the orbit, in a slot as tall as the tallest guide so the orbit never jumps. Once the orbit settles it slides up a third of its height with a spring (damping 0.9, stiffness 300) and fades in over 220 ms; while the orbit turns it fades out (120 ms). It's a polite live region, so TalkBack reads it out. One block of even width (at most 388 dp including 24 dp padding each side), centred on the sun's axis so its margins match on both sides, everything centred (v1.4):
- The tool's name: Title style scaled by 0.86 (18 sp), sandstone, letter-spacing 2.
- 8 dp gap, one concise paragraph: Body scaled by 0.86 (about 14 sp), white, line height 1.45. Each is worded to wrap to full lines with no word left alone on the last.

| Tool | Paragraph |
|---|---|
| AR MEASURE | Measure distances with the camera. Move slowly until the reticle turns solid, tap to drop points, hold to save. |
| TUBE LEVEL | Level or plumb walls and beams. Hold an edge of the phone to the surface until the bubble turns green. |
| BULLSEYE LEVEL | Level a flat surface both ways. Lay the phone on it and centre the bubble; X is side to side, Y front to back. |

**"TAP THE MOON AGAIN TO OPEN"** stands on its own between the guide and the bottom buttons: Caption at 12 sp, centred, in grey (`Palette.Hint` #8A8A8A, 5.8:1 on black). Its gap above (10% of the spare height + 12 dp) and below (10%) match the gap between the orbit and the guide (10%), so the three gaps look equal (measured on a Pixel 10a: 142, 139 and 153 px, the last including the icons' clear touch padding). Swiping on it turns the orbit too.

### 1.4a Menu backdrop (v1.4, `menu/Sky.kt`)

Decorative only (no semantics), and only in the empty band **above the sun** (the space the 70% top share leaves, §1.4), clipped to it, so nothing ever crosses the sun, the guide or the buttons. Shapes fade out within 40 dp of the band's top and bottom edges rather than being cut off.
- **Modern measuring tools**, as large, barely visible line art (#141414, 2 dp strokes, 110 dp boxes): a tape measure, a laser distance meter, a box level, a digital caliper, a speed square, a cross-line laser level on a tripod, a measuring wheel, a total station, a steel rule and a digital angle finder. Three float at a time, spread across the width to start, drifting mostly sideways at about 2–5 dp/s (vertical speed 35% of that) and turning at 0.6–1.8°/s, wrapping from side to side (seeded, so the arrangement is the same each launch).
- **Meteors**, very dark brown (#2E2016 at the head, the tail fading to nothing over 80 dp, 1.5 dp wide), each starting somewhere in the band on a **random heading** and gliding 45% of the width over 8–12 s, fading in over the first 15% and out over the last 30%. One starts every 4–9 s, at most two at once.
- Every shape is drawn once into a bitmap per density; motion only moves, turns and fades layers, about 30 times a second, so nothing redraws.
- Still (no drift, no meteors) with reduced motion and while the fun facts cover the menu; stopped whenever the menu isn't on screen.

### 1.5 Settings

A list with 8 dp vertical padding:

- Section header "UNITS" in the Caption style, textMuted, left-aligned, padding L16 T12 R16 B4, marked as a heading.
- Option rows "Imperial (ft, in)" and "Metric (m, cm)" (Label style, min height 64 dp). The trailing radio icon is `radio_button_checked_rounded` in **sandstone** when selected, or `radio_button_unchecked_rounded` in textMuted otherwise.

Tapping an option sets the unit system immediately and persists it (§2.7).

**FEEDBACK** (v1.4), after a divider: a "Vibration" row (subtitle "Taps and pulses as you measure and level") with a Material switch (sandstone track when on). On by default, persisted as `showdist.haptics`. Off silences every haptic in the app (§11).

Settings stays short and compact (v1.4; it doesn't stretch to fill the screen): UNITS, CALIBRATE (§1.5a), FEEDBACK, then **ADDITIONAL INFO**, each pair of sections separated by a divider with the same 8 dp above and below it, with an "About" row (subtitle "Showdist, the developer and licenses", chevron) that opens About (§1.6), then 24 dp of space.


**CALIBRATE** (v1.4), after a divider, before FEEDBACK: three rows with chevrons, each opening a screen with one instruction and one button.
- "Measurement scale", subtitle "Measure something you know" (or "+2.0% applied" once set).
- "Tracking warm-up", subtitle "Move your phone slowly".
- "Level zero", subtitle "Set zero on a surface you trust" (or "Zero set").

### 1.5a Calibration (v1.4, `settings/Calibration.kt`, `settings/CalibrateScreens.kt`)

All values are persisted (`showdist.calibration.scale`, `.level_x`, `.level_y`) and loaded at start.

- **Measurement scale** (app bar "MEASUREMENT SCALE"): "Measure something you know." Choose Letter paper, long edge (11 in = 279.4 mm); A4 paper, long edge (297 mm); US dollar bill, long edge (156 mm); credit card, long edge (85.6 mm, ISO/IEC 7810 ID-1); or "Something else I've measured" with a length field in cm (metric) or inches (imperial), 5 cm to 10 m. **Measure** opens AR Measure in calibration mode: no correction applied, hold-to-save off, and the hint reads "Place a point at each end of the sheet's long edge" (or the bill's, the card's, the object). 400 ms after the second point, the raw distance between the two anchors is compared with the true length and Measure closes, back to this screen, which shows the outcome:
  - factor = true ÷ measured. Accepted if within **0.85–1.15**: stored, and the screen says "Your measurements were 2% short. Fixed." (or "long"; under 0.5% off: "Your measurements were already spot on.").
  - Otherwise nothing changes: "That reading was 35% off, too far to be right. Check the two ends and try again." (coral).
  - **Reset** (shown while a correction is set, with "(+2.0% now applied)") returns the factor to 1.
  - The factor multiplies every **new** length: live labels, the live line and running total, and each saved Recording, which stores it (`scale` in the JSON, absent = 1). A recording's segments and total include its own factor, so History, sharing and PDF exports agree with what was shown when it was measured; its points stay raw, so "View in AR" lines up with the scene, and its projected labels use its factor. Older recordings are unchanged.
- **Tracking warm-up**: an overlay in AR Measure, centred on a scrim card (24 dp corners, max 320 dp): "Move your phone slowly side to side over a textured surface", an 88 dp ring (6 dp, white 20% track, sandstone fill, level green when full) and **Done** (enabled once full; back also closes). The fill is the tracker's warm-up quality, from each new camera image without allocation: 45% camera travel while tracking (full at 60 cm, each image's step capped at 5 cm), 35% planes found (full at 2), 20% seconds of steady tracking (full at 4 s); losing tracking halves the travel and restarts the steady time. It counts as full from 97%.
  - Opened from Settings, it's Measure in warm-up mode, and Done goes back to Settings.
  - **Automatic prompt:** in Measure, when the status pill (§2.3a) has shown Tracking lost, Aim at more detail, or Move phone slowly with points placed, for **5 s**, a "Warm up tracking" button (sandstone outline, ≥48 dp) appears 64 dp above the bottom of the camera area. It opens the same overlay over the running session, so points are kept; Done closes it. Hold-to-save is off while it's open.
- **Level zero** (app bar "LEVEL ZERO"): "Place your phone flat on a surface you trust, then tap Set zero." **Set zero** averages one second of the accelerometer (x/g, y/g, z/g); if the phone is face up and tilted under about 5° (√(x²+y²) < 0.09) it stores x and y as the zero ("Zero set. Both levels now read 0 here."), otherwise "Lay the phone flat, screen up, then try again." **Reset** clears it. Both levels subtract the stored x and y from every accelerometer reading before filtering (§4.1), so the offset applies to the bubble, readouts and the tube level alike. The levelers' own Zero (§4) still works on top, as a temporary relative zero.

### 1.6 About

Its own screen (v1.4, `settings/AboutScreen.kt`), reached **only from Settings** (Additional Info → About); nothing about the app, the developer or licences is on the main menu. App bar "ABOUT" with the standard back arrow; back (arrow or system gesture) returns to Settings. Content scrolls, in the centred reading column on wide screens (§6.5):
- "SHOWDIST" (Title, sandstone, letter-spacing 3), then "An AR tape measure and two levels: measure distances with the camera, and check walls, beams and flat surfaces for level." (Body), then "Created with AI assistance" (Secondary, italic); padding 20 × 12 dp.
- A divider, then "DEVELOPER": "Carlos Cruz" (Body), "Version {versionName}" (Secondary, read from the installed package), then the **only contact**: the official **X logo** (its published path data, as a vector, 18 dp, sandstone) with the handle **@shcarlosconfigs** beside it (Label style, JetBrains Mono, sandstone). Icon and handle are one tappable row, at least 48 dp tall, with a ripple when pressed and the accessibility label "Developer on X, @shcarlosconfigs" (click label "Open on X"). It opens https://x.com/shcarlosconfigs in the X app or the browser. **No email address appears anywhere in the app.**
- For each tool guide, a sandstone divider (inset 20 dp), a section header (Caption, heading) and rows of a 104 dp bold white label and its text (Secondary). The guides are AR MEASURE (Start, Points, Edit, Save), HISTORY (Open, Share, View in AR), TUBE LEVEL (Use, Level, Zero) and BULLSEYE LEVEL (Use, Read, Zero).
- A divider, then the ARCore notice (Secondary), required by the ARCore terms: "This application runs on Google Play Services for AR (ARCore), which is provided by Google LLC and governed by the Google Privacy Policy."
- A "Privacy policy" row (subtitle "Opens in your browser", chevron) that opens https://carlosantoniocruz.github.io/showdist-privacy/; Google Play requires the policy to be linked inside the app.
- A "Licenses" row with subtitle "ARCore, SceneView, AndroidX and JetBrains Mono" and a chevron. It opens Licenses.

### 1.7 Licenses

- While loading, a centred circular progress indicator is shown in sandstone.
- The screen shows every shipped component, sorted alphabetically. Each row shows the name in text with a textMuted chevron. Rows are separated by dividers (1 dp, divider colour, indented 20 dp).
- The license list is built once per app run and cached.
- Tapping a row opens **License detail**. Its padding is 24 dp on all sides. The component name is shown in **sandstone** in the Title style at 26 sp, followed by a 20 dp gap. Then comes each license paragraph in the Body style and a 16 dp gap after each. Paragraphs marked "centred" are centre-aligned, and all others are start-aligned.
- The list is curated by hand in `settings/Licenses.kt` (§7.2).

### 1.9 History

- The list shows every saved chain **and group** (§2.13) together, newest first, separated by dividers (1 dp, divider colour, indented 72 dp). The list has 24 dp bottom padding.
- **Group rows** (v1.4): a 44 dp thumbnail of the group's separate lines inside a thin rounded frame (so groups look different from chains), the group's name (Value at 0.85×), and "N lines · Mar 3, 2026" (Secondary). Tapping opens the group's screen (§2.13); long-press selects; the share menu shares or copies it. Selection, delete, Delete all, share, copy and Export PDF all work across chains and groups.
- **Empty state:** centred text with 40 dp side padding, 14 sp, textMuted, line height 1.5:
  "No measurements yet.\nPlace two or more dots, then hold anywhere on screen to save."
- **Row** (left padding 16, right padding 4):
  - Leading: a 44×44 **shape thumbnail** (§2.8), drawn in text, or sandstone when the row is selected.
  - Title: the total length (§2.5), 20 sp, weight 300, text.
  - Subtitle: "N points", 12 sp, textMuted. No date is shown anywhere.
  - Trailing (normal mode): a share-menu button (§2.7).
  - Trailing (selection mode): `check_circle_rounded` in sandstone if selected, `circle_outlined` in textMuted if not, with 12 dp right padding.
  - A selected row is filled surfaceHigh.
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
    - "Delete selected" (`delete_outline_rounded`, **sandstone**), disabled when nothing is selected.
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
   - Depth mode: **disabled** (v1.4). On phones without a depth sensor ARCore would otherwise run costly software depth.
   - Instant Placement, cloud anchors, geospatial, Streetscape, augmented faces, semantics: disabled. Augmented images: none (empty database).
   - Camera config (v1.4): back camera, **30 fps**, depth sensor not used; the smallest GPU texture whose short side is at least 720 px (else the largest), then the smallest CPU image. The choice is logged.
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
- An `error_outline` icon, 48 dp, in sandstone.
- 24 dp gap, then the title (22 sp, weight 300, text, centred).
- 12 dp gap, then the body (14 sp, textMuted, line height 1.4, centred).
- If there is an action: a 28 dp gap, then a pill-shaped outlined button with sandstone text (Label, weight 700) and a 1 dp sandstone border.

**Lifecycle rules:**
- When the app goes to the background (activity paused), pause the GL view and the AR session. Resume both when the app returns, but only if a session was started.
- When another screen (History) is pushed over Measure, pause the session and the orientation sensor **in place**. Anchors survive. When the user returns, resume both.
- When the Measure screen is closed, stop and **close** the session. This discards all anchors and ARCore's map.
- When the app returns to the foreground, restart the orientation listener. Retry the start routine only if the current problem is flagged to retry on resume.

### 2.2 AR layout

The screen is a vertical column:

1. **Camera area** fills all the height above the control bar, so nothing measured is ever hidden behind the bar. It is a stack of layers, bottom to top:
   1. The camera feed: a full-screen textured quad from ARCore's external OES texture, transformed with ARCore's display-geometry mapping. Until ARCore has delivered two camera images, the feed is covered in black: before then the texture holds uninitialised memory, which slower phones (e.g. Galaxy A17) show as coloured static.
   2. The **overlay canvas** (§2.4, §3). Touches pass through it. There is no top scrim; the hint and total each sit on their own scrim pill.
   3. The **top info column**, top-centre, inside the safe area (excluding bottom), padded L24 T16 R24 (v1.4: the menu button is no longer at the top):
      - The **hint** line (§2.3).
      - When there are ≥2 points: a 6 dp gap, then the **running total** as a pill. The pill is scrim (§5), fully rounded, with 16 h × 4 v padding. The total text is 34 sp, weight 200, pure white, with tabular figures, formatted per §2.5.
   5. The **menu button**, **bottom-left of the camera area** (v1.4, within thumb reach, just above the control bar), inside the horizontal safe area, 8 dp from the bottom and left: a 48 dp tap target around a 40 dp scrim disc holding a white `arrow_back_rounded` (22 dp, alpha 0.9). It returns to the main menu, closing the session like Back. Its content description is "Back to menu". It sits as far as possible from the control bar so it isn't hit by accident, and it turns with the bar icons (§2.9). A quick tap is needed; a long press still charges hold-to-save.
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

**v1.4:** the hint line shows only the last five kinds of hint (what to do next). For every row about tracking, scanning, warm-up or aiming, the line stays empty but keeps its height, so the running total doesn't jump, and the status pill (§2.3a) says what's wrong instead.

### 2.3a Tracking status pill (v1.4)

A small pill centred at the bottom of the camera area, 16 dp above the control bar, turning with the bar controls (§2.9). Palette.Scrim, fully rounded, 12 dp start / 14 dp end × 6 dp v padding; an 8 dp dot, 8 dp gap, then 14 sp white JetBrains Mono, letter-spacing 0.3. It's a polite live region for TalkBack.

| State (first match) | Text | Dot |
|---|---|---|
| not tracking, too dark | Too dark | Attention (#FF8A65) |
| not tracking, excessive motion | Move phone slowly | sandstone |
| not tracking, insufficient features | Aim at more detail | sandstone |
| not tracking, camera unavailable | Camera unavailable | Attention |
| not tracking, other reason, after tracking once | Tracking lost | Attention |
| not tracking, other reason, never tracked yet | Move phone slowly | sandstone |
| a tap would place (the main button is enabled) | Ready | Level green |
| 0 planes and no reticle hit | Scanning surface | sandstone |
| not warmed up (§2.4) for a first point | Scanning surface (0 planes) / Move phone slowly | sandstone |
| otherwise (no reticle hit) | Aim at a surface | sandstone |

Motion: the text cross-fades over 250 ms and the dot's colour over 300 ms. "Ready" stays for 1.2 s, then the pill fades out over 600 ms. Any other state brings it back over 250 ms, but only after lasting 400 ms, so a flickering hit can't blink it. Only the pill's own layer alpha animates.

### 2.4 Per-frame data & hit testing

Work runs only when ARCore delivers a **new camera image**, about 30 per second. The GL view itself redraws continuously at the display rate, drawing only the camera background on frames with no new image. For each new image:

- **Tracking status:** TRACKING, PAUSED (treated as "lost"), or STOPPED. The failure reason is none, bad state, insufficient light, excessive motion, insufficient features, or camera unavailable.
- **Plane count:** the number of planes that are TRACKING and not subsumed by another plane. It is recounted every 10th new image, about 3 times a second, and is used for the hint and the warm-up.
- **Reticle hit:** hit-test the **exact centre of the camera area**, then take, by priority (v1.1):
  1. the nearest Plane hit whose pose is inside the plane's polygon **and** whose plane faces the camera (the camera is on the side of the hit pose's +y normal), else
  2. the nearest feature Point hit whose orientation mode is ESTIMATED_SURFACE_NORMAL, as a last resort.
  If none qualifies, there is no reticle hit. Hit testing only runs while the camera is TRACKING (so never with a tracking failure reason).
- **Smoothing (v1.4):** the drawn hit (live line end, live label, a projection's aim and turn) is an exponential moving average of the raw hits, moving **50%** of the way on each new image. A jump of more than **25 cm**, or a hit after no hit, is taken at once. Placement samples use the raw hits.
- **Warm-up (v1.1):** before the first point of a measurement (or a projection's first tap), the session must have found at least one plane and the camera must have moved at least **8 cm** from where tracking began, for parallax. The camera position comes from the view matrix (−Rᵀt). Once warmed up, it stays so for the session. Until then the main button is disabled and the hint says why (§2.3).
- **Placing a point (v1.1):** a tap on the main button (enabled only with a reticle hit and, for a first point, after warm-up):
  - **Snap:** if a visible point's screen position is within **24 dp** of the reticle, the new point is anchored at that point's current pose (the nearest such point). Projections don't snap.
  - **Otherwise sample:** the reticle hit's position is recorded on each of the next new camera images until **6** samples are taken (about 200 ms), within at most 12 images. Tracking must hold throughout, or the placement is dropped. With at least 3 samples, the point is anchored at their **component-wise median**; with fewer, nothing is placed. Further taps are ignored while sampling.
  - Each point gets its own session anchor. A point is drawn only while its anchor is TRACKING; a paused anchor is kept, hidden, and shows again when it resumes. Distances are recomputed from the live anchor poses every new image, and the anchors are re-read when saving. Undo and Clear detach anchors; either one also cancels a placement that's still sampling.
  - **Logging (tag `ShowdistMeasure`, all builds, v1.4):** camera tracking-state changes (with the failure reason), the anchor count after each place/undo/clear, and the number of anchors tracking whenever it changes.
  - **Debug builds only:** each placement is also logged with the hit kind (Plane, Point or Snap), the tracking state, the sample count and the spread (largest distance of a sample from the median, in mm).
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
| 1 | M / FT unit toggle | Two tappable labels, "M" and "FT". The active one is weight 700 in its unit colour: **cyan** for M, **crimson** for FT. The inactive one is textMuted at alpha 0.6, weight 500. Both are 12 sp with letter-spacing 1.5 and padding 7 h × 16 v. Tapping sets and persists the unit system. |
| 2 | Undo | `undo_rounded`. Tooltip "Undo last point". With no points left, steps a projection back a stage (§2.11). Disabled when there are no points and no projection. |
| 3 | Main button | 96×96. Place point on tap (§2.4). Enabled with a reticle hit and, before a first point, after warm-up. Shows the save charge. |
| 4 | Clear | `close_rounded`. Tooltip "Clear all points". Also removes a projection. Disabled when there are no points and no projection. |
| 5 | History | `history_rounded` icon. If ≥1 saved, a count badge appears (white, 11 sp, weight 700, top-right of the 52×52 hit area, at right 4 / top 6). Opens History (pausing the session). |

- The icon buttons are bare 24 dp icons centred in a 52×52 hit area. They are text at alpha 0.9 when enabled and 0.3 when disabled.
- Controls 1, 2, 4 and 5 each sit in a fixed 60×60 slot and **rotate** to stay upright (§2.9). The round main button doesn't rotate.
- The 96 dp main button may overhang its column evenly.

**Main button** (custom-drawn, 96×96). Its accent is sandstone at full strength when enabled (reticle on a surface) and sandstone at alpha 0.4 when disabled. It has:
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
- The charge starts only with ≥2 points **and the camera TRACKING** (v1.4). With ≥2 points but tracking lost, it doesn't charge and plays a soft double tick ("not ready"); with fewer points, nothing happens.
- **Haptics while charging (v1.4):** pulses that quicken from every 160 ms to every 60 ms and strengthen from 0.15 to 0.7 as the charge fills, run from their own coroutine (never the render loop).
- It animates linearly from 0 to 1 over **800 ms**, filling the main button's ring.
- While the charge is above 0:
  - An **edge band** traces the screen's border: a 6 dp stroke with round caps in **sandstone** (§5), over a dark halo 3 dp wider each side, running clockwise from top centre to `charge` of the way round, on a full-perimeter track of white at alpha 0.2. It follows the display's rounded corners (the top-left `RoundedCorner` radius on API 31+, square before).
  - A **SAVING pill** is centred horizontally, and vertically halfway between the bottom of the top info column (§2.2, so it moves down when the running total shows) and 28 dp above the camera area's centre (clear of the reticle): "SAVING" (18 sp, bold, letter-spacing 3, white) on a fully rounded scrim pill (§5), padding 20 × 8 dp, with a 1.5 dp white border. It turns with the bar controls (§2.9).
  - Both disappear when the charge returns to 0 or the save completes.
- If the finger lifts or the gesture is cancelled before completion, the pulses stop at once (anything playing is cancelled, then one very light tick), and the charge animates back to 0 over 200 ms. Nothing is saved.
- On completion:
  1. Play a crisp confirming tap, stronger than the build-up.
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
  5. 18 dp gap. From History only (not the "SAVED" sheet), a full-width filled **sandstone** pill button "View in AR" (black, Label style, weight 700) that projects the recording in Measure (§2.11), then an 8 dp gap.
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
- A dot (radius 2.6 dp) at each point, in the row colour.

### 2.9 Orientation handling in Measure (button rotation)

The screen stays portrait. The four small bar controls rotate, and so do the readings over the camera: each distance label (about its own centre, §3.3), the running total and the SAVING pill (§2.6). Turned sideways, the SAVING pill takes its rotated footprint in layout; a label's clamp to the canvas uses its rotated bounds. **Running total held sideways (v1.4):** once the turn passes 45°, the total leaves the top info column and is centred on the reader's top edge: the screen's right edge when turned clockwise, the left edge otherwise, vertically centred on the camera area, 12 dp inside the horizontal safe-drawing insets. It's compact there: 32 sp, 12 h × 2 v padding. It never overlaps the reticle (centre), the menu button (top-left corner) or the control bar.

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
  - Buttons: "Not now" (textMuted) and "Save & start new" / "Start new" (sandstone, the Material primary).
- **Confirm:** if ≥2 points exist, save the recording and show a "Saved to History" snackbar. Then close the session and restart it (the full start routine). This discards ARCore's map.
- **Not now:** keep measuring. The prompt won't reappear in this session.

### 2.11 Projecting a saved measurement

"View in AR" on a History measurement sheet opens Measure with that recording projected into the scene. If Measure is already open underneath History, it returns to it (keeping its points); otherwise Measure opens on top of History, and Back returns there. Picking again replaces the projection.

- **What's kept:** a recording's points are world positions from a session that has ended. A new session has its own origin and heading, so the shape can't reappear where it was measured. ARCore's world y axis is always up, so the shape's size, heights and slopes are kept exactly; only where it sits and which way it faces are set anew.
- **Placing it, two taps on the main button:**
  1. **Aiming:** the shape's first point follows the reticle hit, turned so that its reference line points away from the camera. A tap anchors the first point there (a session anchor).
  2. **Aligning:** the shape turns about its first point so that its reference line points at the reticle hit, once the hit is more than 5 cm away horizontally. A tap freezes that heading.
  - The **reference line** runs from the first point to the first later point that is more than 1 cm away horizontally. A recording with no such point (straight up and down) skips aligning.
- **Drawn** before the user's own points and labels (§3.2), in the scheme's **projection** colour (§5): lines 2.5 dp, dots radius 4.5 dp, with the usual overlay halos. While aiming or aligning the lines are dashed (dash 6 dp, gap 6 dp); once placed, solid. Each drawn segment gets a label with the recording's saved length and a border in the projection colour.
- **Measuring continues** as normal once placed: taps add the user's own AR-pink points, and hold-to-save saves only those.
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
- **Groups (v1.4, §2.13):** a group takes one cell like a chain. Caption: "#N · Name · N lines" (bold 10 pt) and its date. Its lines are drawn in one outline (every endpoint projected together onto their best-fit plane, as for a chain), each line on its own with its two dots and its number at its midpoint (pushed off the line, on white). Beside it, right-aligned in the cell (at least from 45% of its width), a list "n  Label  length" per line, in as many columns as fit. Monochrome as above.

### 2.13 Chained and Group measuring (v1.4)

**Choosing.** Opening AR Measure from the menu shows a dialog with two large outlined buttons (sandstone, 20 dp corners, ≥88 dp tall, title in the Title style and a Secondary line) — **Chained** "One continuous path, with a running total" and **Group** "Separate lines for one thing: a room, a table, a cabinet". In the compact dialog style (§1.2a): title "How will you measure?", buttons ≥64 dp tall with 16 × 12 dp padding, 8 dp apart. There's no "remember" option in it. Dismissing it opens nothing. Settings → Measuring → **Measure mode** (`showdist.measure_kind`) can make one the default instead ("Ask each time", "Always chained", "Always group"); then the dialog is skipped. "View in AR" from History always opens a Chained session; calibration and warm-up measure as before.

**Chained** is unchanged: points link into one path; the pill shows the total; hold to save.

**Group** (the tracker's group mode): every two points make one standalone line, each point with its own anchor (§2.4); no segment joins one line's end to the next one's start, and the dashed live line shows only while a line is half drawn. Up to 12 lines (24 points).
- Hints: "Tap to start a line"; "Tap to end the line"; then "Tap to start the next line · hold to finish". With no reticle hit, the status pill explains (§2.3a).
- The pill (§2.2's running-total pill, same style and sideways placement) shows the **current line**: the one being drawn while the reticle has a surface, else the last one finished. It's hidden while a line is half drawn and there's no surface.
- Under it, once a line is finished: a "N lines" scrim pill and a **Done** button (scrim, 1.5 dp sandstone border, ≥48 dp).
- After each line, until the next one starts, a card 64 dp above the bottom of the camera area shows "Line N · length" (or its label) and "Add label" / "Edit"; tapping it opens "Label line N" with a text field ("e.g. North wall", up to 32 characters; empty clears it). Undo and Clear drop the labels of lines no longer there.
- **Done**, or holding anywhere (§2.6, ready once a line is finished), asks "Name this group" (placeholder and default "Group N", N = saved groups + 1; "Save" / "Keep measuring"). Saving stores the group, clears the points and shows "Saved Kitchen · 3 lines" in a snackbar. A half-drawn line is left out.
- **Adding to a saved group** (from the group's screen): the same, but Done appends the new lines to that group and returns to it, with no name asked.

**Data** (`history/Group.kt`): a group has an id (creation time in µs, like a recording's), a name, a createdAt timestamp, the unit at save, and a list of lines; each line has an id, two world points (as placed; lengths × its calibration scale, §1.5a), an optional label and an optional tag (§2.14). Groups live in `groups.json`, kept exactly like `recordings.json` (§2.7). A chain is still a single Recording.

**Group screen** (from History): app bar "GROUP" with Export PDF and the share menu (Share / Copy, for the whole group); back at the bottom (§1.2). The group's name (Title, 24 sp, sandstone, with a pencil; tap to rename), "N lines · date", then a row per line: its number (sandstone), its label (white) or "Line N" (grey), its length (20 sp) and a delete button (confirm "Delete …? This can't be undone."). Tapping a row renames the line. **Add lines** (outlined sandstone pill with a plus) opens Measure in group mode adding to this group; it's hidden while a Measure is already open beneath History. Deleting every line keeps the (empty) group.

**Share text** for a group: "Name · YYYY-MM-DD HH:MM", "Group of N lines", then "n. Label: length" per line.


### 2.14 Area and volume for groups (v1.4, `history/GroupResults.kt`)

**Tags.** A group line can be tagged **Length**, **Width** or **Height** (each tag on one line at most; giving it to another line takes it off the first).
- While measuring: the line card (§2.13) has a row of chips, Length · Width · Height · None (13 sp, 48 dp targets; the selected one filled sandstone with black text, the others outlined). When each of the first three lines is finished it's given the first tag not yet used, in that order (so normally Length, Width, Height); the user can accept it, pick another, or choose None, and a choice is never overridden. Later lines start untagged. Undo and Clear drop the tags of lines no longer there.
- On the group's screen: each line shows its tag in sandstone capitals ("LENGTH"), or a grey "TAG"; tapping it opens Length / Width / Height / None.
- Stored per line as `tag` in `groups.json`.

**Results**, from the tagged lines' lengths (each already including its calibration):
- Length and Width tagged → **floor area** = length × width.
- Length, Width and Height tagged → floor area and **volume** = length × width × height.
- Anything less (no Width, or only one tagged line) → nothing is shown, never a wrong number.
- Full precision throughout; rounded only for display: two decimals below 100, one decimal from 100 (thousands separated). Imperial: square feet ("120.0 sq ft") and cubic feet ("960.0 cu ft"), using 1 ft = 0.3048 m exactly; metric: m² and m³.

**Where they show:**
- The group's screen: at the **bottom**, after the last line, indented like the lines: "Floor area: …" and "Volume: …" (Label at 15 sp, tabular; smaller than the 20 sp line lengths), then 4 dp and, in grey 13 sp, "Assumes a rectangular space." (rectangular rooms only for now; no L-shaped or multi-section rooms).
- The PDF (§2.12): after the group's line list (which shows each line's tag in brackets), a blank row, then the same lines at 85% of the list's size, monochrome.
- Share and Copy text: after the lines, with each line's tag in brackets.

## 3. AR reticle and overlay drawing

Everything is drawn on a transparent canvas over the camera feed, with the reticle (§3.1) as its own layer on top. **v1.4:** the canvas repaints only when a new AR frame arrives *and* there are marks to move (points, a projection, or marks just removed), so with nothing placed it doesn't repaint at all; never on display frames alone, and nothing repaints while the session is paused. Screen positions are the normalised coordinates × canvas size.

### 3.1 Reticle

On this screen, legibility outdoors and indoors overrides the theme: strokes are brighter and thicker than elsewhere, and every mark sits on a dark halo.

- **Position:** always the **fixed centre of the camera area**, not the projected hit point.
- **Motif:** three small dots orbiting a **white** centre dot. The orbiting dots are in the **unit colour**: **crimson** for imperial, **cyan** for metric (§5), following the current unit setting.
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
- **Rendering (v1.4):** each look (crimson or cyan × on a surface or searching) is drawn once, blurs included, into a bitmap the first time it's needed, then reused. The spin is a single rotation of the reticle's own layer, set from `t` above, so turning it re-records nothing and never repaints the rest of the overlay. It turns only as camera frames arrive, so it stops while paused or hidden. Changing units or the surface state swaps the bitmap. Measured on a Pixel 10a with nothing placed (gfxinfo framestats, 120 frames): the old live-drawn reticle cost about 0.67 ms on the UI thread, 2.3–2.9 ms on the RenderThread and 2.2–4.2 ms of GPU per frame; the cached layer costs about 0.01 ms, 0.8 ms and 1.3–1.8 ms.

### 3.2 Placed points, segments, live line

**Placed points and segment lines** (the icon's colours, §8.1):
- Placed points are filled circles in the scheme's **point** colour (§5; white), radius 6 dp. They are drawn only when visible.
- Segment lines are the scheme's **segment** colour (§5; red for imperial, cyan for metric), 2.5 dp wide with round caps. One is drawn between consecutive points only if both endpoints are visible.

**Live line:**
- A dashed line from the last point to the reticle hit's projected screen position.
- It is the scheme's **live** colour (§5; pale yellow or pale gold), 3 dp wide with round caps; its visible dashes and gaps are 6 dp each (the dash intervals are 3 dp on, 9 dp off, allowing for the caps).
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
- Each label has a 1.5 dp border: the scheme's segment colour on a placed segment's label, its live colour on the live label, its projection colour on a projected recording's label.

---

## 4. Leveler

### 4.1 Behaviour

- **Purpose:** two levels, each opened from its own tile on the main menu (§1.4). The **tube** is a single-axis bubble level for hanging shelves or frames, used by holding the phone flat against a wall. The **bullseye** is a two-axis level used by laying the phone face up on a surface.
- **Sensor:** the accelerometer at "game" rate, about 50 Hz (20 ms). If no accelerometer is present, errors are ignored and the bubble stays centred.
- **Smoothing:** a one-pole low-pass filter on `x/g`, `y/g` and `z/g`, with g = 9.80665 m/s² and α = 0.15.
  - `est = est + (sample − est) × 0.15`
  - The estimate starts at (0, 0, 1).
  - The **readouts** (and the readouts' level test, and Zero) use a second, slower filter of the same form with α = 0.05 (a time constant of about 0.4 s), so the numbers settle instead of flickering; the bubble keeps α = 0.15.
  - **Readout hysteresis:** a shown tenth changes only once |displayed| × 10 is at least 0.75 away from it, so a reading sitting on a rounding boundary holds still.
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

Flat on black: thin outlines, a ruled scale, and a white bubble that turns green when level.

- **Background:** black. The app bar is the standard bar titled "TUBE LEVELER", with the Zero and Reset actions on the right.
- **Body** (inside the safe area):
  1. Instruction "HOLD FLAT AGAINST THE SURFACE" in the Caption style with letter-spacing 2, padding L32 T16 R32.
  2. The **vial** fills the remaining space and is centred.
  3. The **readout**: `|displayed|` to one decimal place followed by "°", e.g. `0.4°`, in the Readout style: **96 sp, weight 700**, tabular figures, white, or **sandstone** when level; readable from about a foot away. 16 dp bottom padding. It shows no sign.
- **Level haptic:** one clock-tick haptic each time the reading becomes level (both levelers).
- **Vial**, a stadium-shaped rounded bar:
  - Thickness 84 dp.
  - Length = the long dimension of the available space minus 2 × 28 dp.
  - Corner radius 42 dp.
  - Filled white at alpha 0.04, with a 1.5 dp outline in line (§5).
- **Scale:** down each long side, 16 dp outside the outline, a 1 dp line in line colour with a tick across it per degree from −15° to +15°, each where the bubble's centre sits at that reading (`deg / 15 × travel` from centre). Every 5th is major (±8 dp, 2 dp, white at alpha 0.85); the others are minor (±4 dp, 1 dp, white at alpha 0.45); the 0° tick is white, or sandstone when level. The 5°, 10° and 15° ticks are labelled "5°", "10°", "15°" (Secondary at 14 sp, tabular), 14 dp further out: beside the tick in portrait, under it in landscape.
- **Centre marks:** two lines across the vial, one on each side of centre.
  - Each sits `56/2 + 5 = 33` dp from centre along the vial axis.
  - Each spans ±(84/2 − 10) = ±32 dp across the vial.
  - Normally 1.5 dp in text at alpha 0.7. When level, 3 dp in **sandstone**.
- **Bubble:**
  - A filled circle, diameter 56 dp: **white**, or **level green** when level. No halo.
  - Its position comes from the bubble motion (§4.4). `travel = length/2 − 2 − 28`: the bubble's centre runs from the middle to where its edge touches the inner wall.
  - A positive reading moves the bubble down in portrait and right in landscape. So a clockwise rotation moves it up in portrait and left in landscape.

### 4.3 Bullseye Leveler visuals

- The same background, app bar actions and style as the tube, titled "BULLSEYE LEVELER".
- **Body:**
  1. Instruction "LAY FLAT ON THE SURFACE", in the tube's instruction style.
  2. The **bullseye** fills the remaining space and is centred.
  3. The **readouts**, stacked and right-aligned, 16 dp bottom padding: "X" then |displayedX|, and below it "Y" then |displayedY|. Each value is the Readout style at **72 sp** (two side by side don't fit in portrait), both sandstone when level. Each letter is the Caption style at 22 sp in sandstone, letter-spacing 0, 12 dp before its value, near its baseline.
- **Bullseye:** a disc of radius = half the shorter dimension of the available space − 28 dp.
  - Filled white at alpha 0.04, with a 1.5 dp outline in line.
  - 1 dp rings (line colour) where the bubble's centre sits at **5°** and **10°** of tilt.
  - Crosshairs: horizontal and vertical 1 dp lines (line colour) from just outside the centre ring (8 dp gap) to the rim, each with a tick across it at the rim (±8 dp, 2 dp, white at alpha 0.85).
  - **Centre ring:** radius 56/2 + 5 = 33 dp. Normally 1.5 dp in text at alpha 0.7; when level, 3 dp **sandstone**.
- **Bubble:**
  - A filled circle, diameter 56 dp: **white**, or **level green** when level. No halo.
  - It moves toward the higher side: right for positive X, up the screen for positive Y.
  - Its position comes from the bubble motion (§4.4), in two dimensions. `travel = radius − 2 − 28`: the bubble's centre runs from the middle to where its edge touches the rim.

### 4.4 Bubble motion and shape

Both levelers share this.

- **Target:** `displayed / 15` along the vial (the tube), or `(displayedX, −displayedY) / 15` (the bullseye, screen y running down), where 1 means the bubble touches the wall. Past 1 the target is clamped back to the wall, except that the bubble may press on into the wall by up to 30% of its radius, growing with how far past full scale the reading is (×2, capped).
- **Motion:** a spring-damper toward the target, stiffness 90 s⁻² (about 1.5 Hz) and damping ratio 0.5, so the bubble eases in and settles with a slight wobble instead of snapping. It's stepped once per display frame (`withFrameNanos`) in substeps of at most 1/120 s, with a frame's time capped at 50 ms. The sensor input is already low-passed (§4.1). At the press limit the bubble stops and bounces back at 25% of its speed into the wall.
- **Shape:** drawn each frame as a 64-point polygon in one reused path, filled like the round bubble (white, or level green when level).
  - **Velocity stretch:** an ellipse with the round bubble's area, radius × s along the direction of travel and radius ÷ s across it, where `s = 1 + min(speed / (25 × radius), 0.4)` with speed in px/s. At rest it's round.
  - **Wall contact:** any outline point past the wall's inner edge (the outline's inner edge plus a hairline, 2 dp in) is moved back onto it, so a bubble pressed against the vial's round end or the bullseye's rim flattens to follow that curve instead of overlapping it.

---

## 5. Colour palette

The palette is fixed and app-wide: black, white and light-grey text, and a single warm accent, **sandstone**. Readability is the goal; every text/background pair meets WCAG AA (4.5:1 body, 3:1 large). There is one theme only; no light/dark modes. `ui/Theme.kt` sets every Material 3 colour role from this palette, so dialogs, menus, snackbars and tooltips inherit it and no default Material colour appears anywhere. The PDF report (§2.12) is monochrome and doesn't use it.

| Token | Hex | Used for |
|---|---|---|
| **black** | `#000000` | Background of every screen, app bars, the Measure control bar, launch/splash, app-icon tile, the wordmark on the sun, text on sandstone fills. |
| **surface** | `#121212` | Dialogs, bottom sheets, popup menus, the moons' discs. |
| **surfaceHigh** | `#1E1C19` | Raised fills: a selected History row, Material containers. |
| **text** | `#FFFFFF` | Primary text, titles, bar icons, the Leveler readout, centre marks (alpha 0.7) and bubble. |
| **textMuted** | `#B8B8B8` | Secondary text: descriptions, captions, subtitles, guide steps, empty states, dialog bodies, cancel buttons; chevrons, unselected radios and moon rings. |
| **white** | `#FFFFFF` | Over the camera: the reticle centre dot, live line, label text, hint, running total, charge arc. Leveler ticks (alpha variants). |
| **sandstone** | `#C8A97E` | The only accent (the Material primary, secondary, tertiary and error): the sun and its shell, the focused moon's ring and every moon picture, the menu's History and Settings buttons, the guide's tool name and bullets, the main button, "View in AR", selection in History, "Delete", radios, "level" on the Leveler (marks, ring, readout, axis letters; not the bubble), headings in About and License detail, the Licenses spinner, the Problem view, the app-icon rule. |
| **divider** | sandstone at 35% (`#59C8A97E`) | Dividers in Settings (including About), Licenses and History. |
| **line** | white at 30% (`#4DFFFFFF`) | Outlines that should read but stay quiet: the Leveler vial, dish, rings, crosshairs and scale lines, the orbit, the control bar's top rule, outlined-button borders. |
| **level green** | `#3DDC84` | The Leveler bubble when level, and nothing else (11.8:1 on black). |
| **crimson** | `#E71837` | **Reserved for unit state in Measure: imperial (ft, in).** Reticle dots, the FT label when active. Never a general accent. |
| **cyan** | `#00E5FF` | **Reserved for unit state in Measure: metric (m, cm).** Reticle dots, the M label when active. Never a general accent. |

**AR marks** (`ArScheme`): drawn into the camera view only, where outdoor readability comes first. The set follows the unit system, to go with the reticle's unit colour (§3.1); there is no setting. Colours are point (placed dots) / segment (solid lines and their label borders) / live (the dashed live line and its label border) / projection (a projected recording, §2.11):

| Scheme | Units | Point | Segment | Live | Projection |
|---|---|---|---|---|---|
| **Supernova** | imperial (FT) | `#FFFFFF` white | `#FF5252` flare red | `#FFF59D` pale yellow | `#EA80FC` orchid |
| **Comet** | metric (M) | `#FFFFFF` white | `#18FFFF` bright cyan | `#FFE57F` pale gold | `#82B1FF` periwinkle |

**Errors** use sandstone beside an icon (`error_outline`, `delete_outline`); there is no separate error colour.

**Scrims and halos** (derived colours):

| Name | Value | Used for |
|---|---|---|
| scrim | `#B8000000`, black 72% | Behind every piece of live text over the camera: distance labels, the running total, the hint, the menu button |
| halo | `#99000000`, black 60% | Under the reticle (blur 2) and every point and line (blur 1.2), §3 |

**Contrast** (on black): text 21:1, textMuted 10.6:1, sandstone 9.4:1 (black on sandstone the same), crimson 4.6:1, cyan 13.7:1. On surface: text 18.7:1, textMuted 9.4:1, sandstone 8.4:1; on surfaceHigh the lowest pair is sandstone at 7.6:1. White on the scrim over a pure-white scene is 9.3:1.

---

## 6. Typography and wordmark

### 6.1 Font

- **JetBrains Mono** is the only typeface, used everywhere, including text drawn on the AR canvas.
- **Bundled faces:** Regular (400), Bold (700), Italic, and Bold Italic. Other weights the code asks for (200, 300, 500, 600, 800) render using the nearest bundled face.
- The OpenType **`zero`** feature (slashed zero) is enabled on **all** text.
- **Tabular figures** (`tnum`) are also enabled on the running total, distance labels, sheet segment lengths, the Leveler readouts and their scale labels.
- The Material 3 type scale is set in JetBrains Mono at **1.2 ×** its default sizes, so Material components (dialogs, menus, snackbars, tooltips, badges) match the app's styles. Shapes: 8 / 12 / 20 / 28 / 36 dp corners for extra-small to extra-large, pill buttons.

### 6.2 SHOWDIST wordmark

- The text "SHOWDIST": one word, all caps, JetBrains Mono, weight 700, in black on the menu's sun (§1.4), sized to the sun (eight glyphs spanning 1.5 radii), centred horizontally and raised very slightly, 0.04 radii above the sun's centre (v1.4). On the sun it's drawn extra bold: the filled text with a same-coloured outline over it, 5.5% of the font size wide with round joins, since the heaviest bundled face is Bold. The light-wash copy (§1.4 collapse) is drawn the same way in white. In About it's the Title style in sandstone.
- The app icon has no lettering.

### 6.3 Text styles (`Type` in `ui/Theme.kt`)

About 20% larger than the earlier build. Screens use these, not raw sizes.

| Style | Size | Weight / other | Used for |
|---|---|---|---|
| Caption | 14 sp | 600, letter-spacing 3, centred, textMuted | App-bar titles (in text), section headers, sheet titles, "WAKING THE CAMERA", the Leveler instruction (letter-spacing 2) |
| Title | 21 sp | 700, letter-spacing 1, line height 1.3 | Tool names in the guide, dialog titles, About and License headings |
| Label | 17 sp | 400 | List-row titles, button and menu-item labels |
| Body | 16 sp | 400, line height 1.5 | Running text |
| Secondary | 15 sp | 400, line height 1.45, textMuted | Descriptions, subtitles, guide steps |
| Value | 24 sp | 300, tabular | A total in the History list |
| ValueLarge | 52 sp | 200, tabular | A total on its sheet |
| Readout | 96 sp | 700, tabular | Leveler readouts (72 sp for the bullseye's pair) |

The Measure screen keeps its own sizes for text over the camera: hint 15 sp, running total 40 sp, unit toggle 15 sp, History badge 13 sp; distance labels 14 sp (§3.3).

---


### 6.5 Wide screens (v1.4)

Text-heavy content sits in a **horizontally centred column at most 520 dp wide** (`ui/ReadingColumn.kt`), decided by the width available, never by device type, so phones (all narrower) are unchanged. Inside the column, alignment stays as on phones.
- **Main menu:** the quick guide (title, purpose, steps, "TAP THE MOON AGAIN TO OPEN"), centred under the sun.
- **Settings, History, Licenses, License detail:** their rows and text. Scrolling lists keep scrolling edge to edge: the column comes from side content padding, not a narrower list.
- The measurement sheet is a Material bottom sheet, already capped at 640 dp. The fun facts column is capped at 560 dp (§1.4).
- **Orientation:** the activity asks for portrait, but Android 16+ ignores that on large screens (smallest width ≥ 600 dp), so tablets rotate. The layouts above hold in landscape (checked at 720 dp wide, both orientations): the orbit sizes itself from the smaller dimension, and the guide stays clear of the sun and moons. AR Measure on a landscape tablet is not yet adapted: its sideways controls (§2.9) assume a portrait screen.
## 7. About text & third-party licences

### 7.1 About text (verbatim)

In About (§1.6):
- `Carlos Cruz`, `Version {versionName}` (from the installed package), and the X row `@shcarlosconfigs` (no email address)
- `Created with AI assistance` (italic)
- `Licenses`, with subtitle `ARCore, SceneView, AndroidX and JetBrains Mono`.

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
  - sandstone (`#C8A97E`) strokes, width 3.5, round caps: a horizontal rule from (34, 54) to (74, 54) and an end tick at each end, x = 34 and x = 74, from y = 43 to y = 65.
  - Two white (`#FFFFFF`) dots, radius 6, at (34, 54) and (74, 54).
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
     - **Depth mode AUTOMATIC** was enabled on supporting devices, which adds significant per-frame native memory. (Disabled in v1.4.)
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

settings (outlined), info (outlined), error_outline, history, chevron_right, arrow_back, undo, close, ios_share, content_copy, select_all, delete_outline, check_circle, circle (outlined), radio_button_checked, radio_button_unchecked, center_focus_strong, restart_alt, picture_as_pdf.

## 11. Haptics summary

| Event | Haptic |
|---|---|
| Place point tap | selection click |
| Hold-to-save charging | pulses every 160 → 60 ms, strength 0.15 → 0.7 |
| Hold-to-save released early | pulses stop; one very light tick |
| Hold-to-save refused (≥2 points, tracking lost) | soft double tick |
| Save completes | confirming click |
| Leveler Zero / Reset | selection click |
| Menu orbit settles on a different moon | selection click |
| Collapse hold (menu) | pulses every 250 → 40 ms, strength rising with progress |
| Collapse collision | confirming click |

**Strength-varying haptics** (`ui/Haptics.kt`, v1.4) pick the richest the motor supports: composition primitives (tick scaled 0.25–1, click 1) on API 30+ where supported; else 12 ms one-shots at amplitude 40–240 (confirm 30 ms at 255) where amplitude control exists (API 26+); else the view's haptic constants (clock tick below half strength, keyboard tap above; confirm, or long-press before API 30). Every haptic in the app goes through it (selection clicks use the view's clock tick). They use touch usage attributes, play nothing while the app's **Vibration** setting (§1.5) or the system's touch feedback is off, and are fired from gesture and animation coroutines, never from a render loop. Requires the VIBRATE permission (install-time).

## 12. Timing constants summary

| Constant | Value |
|---|---|
| Reticle period | 11 s per clockwise turn |
| Save charge | 800 ms (after ~500 ms long-press recognition) |
| Charge cancel rewind | 200 ms |
| Hint cross-fade | 250 ms |
| Bar-button rotation | 250 ms, ease-out |
| Orientation sensor | ~5 Hz |
| Leveler sensor | ~50 Hz, low-pass α = 0.15 (readouts α = 0.05, hysteresis 0.75 tenth) |
| Plane recount | every 10 new camera frames |
| Memory check | every 90 new camera frames |
| Max points per measurement | 24 |
| Text truncation | 20 segments (single share/copy) |

## 13. Discrepancies noted while writing this spec

- **Palette names:** the code's `Palette` tokens use the §5 names (`Palette.Surface`, `Palette.TextMuted`, …); `Palette.unit(units)` picks crimson or cyan.
- **README's Leveler section is out of date:** it describes "the dot centres in the ring" and a seaGreen bubble when plumb. The code draws the flat vial and bullseye of §4.2–4.3, with a white bubble that turns green when level.
- **About text:** the description and the licenses subtitle mention Flutter, and the subtitle claims ARCore's license is listed even though it isn't registered (§7.2). Both should be revised for the Kotlin build.
