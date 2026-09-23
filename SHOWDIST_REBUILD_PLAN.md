# Showdist — Native Kotlin Rebuild Plan (for Claude Code)

## How to use this

1. Run **Step 0** inside your *existing Flutter repo*. It produces `SPEC.md`, which captures everything you designed.
2. Create a **new, empty repo** (e.g. `showdist-android`). Copy in `SPEC.md` and this file.
3. Paste **one stage prompt per Claude Code session**. Test on your phone. Commit. Type `/clear`. Next stage.
4. Never skip ahead. Each stage must build and run on-device before the next one starts.

**Why this saves usage:** every stage starts with a clean context, Claude Code reads only `SPEC.md` + `CLAUDE.md` + the files it needs, and it never holds the whole app in its head at once.

**One-time setup:** install Android Studio (you need its Android SDK, the device logcat, and the Memory Profiler). Enable USB debugging on your phone.

---

## Step 0 — Extract the spec (run in the OLD Flutter repo)

```
Read this Flutter project and write a single file, SPEC.md, that fully describes the app so it can be rebuilt natively in Kotlin by someone who never sees this code. Do NOT change any code.

Include:
1. Every screen and navigation path (landing, AR measure, leveler, settings/about, licences).
2. All measurement logic: how points are captured, how distance is calculated, units, rounding, what happens on undo/clear/reset.
3. The AR reticle behaviour and appearance (three dots in a triangle slowly rotating around a white centre dot, seafoam green with a shadow).
4. Leveler logic: sensor used, single-axis roll, zero/tare, portrait and landscape handling, bubble vial visuals.
5. The full colour palette with exact hex values as used in code (dark citrine, peach red, Olympic blue, light mauve, seafoam, warm grey, white, scrim), and where each colour is used.
6. Typography (JetBrains Mono), the SHOWDIST wordmark, the landing screen layout with the static upward tick marks.
7. About section text including "Created with AI assistance", and the list of third-party licences/assets.
8. App icon spec and any asset files that should be copied over (list their paths).
9. Known problems: note that AR performance degrades and runs out of memory after 2–3 measurements.

Keep it factual and structured. No code in the spec except short formulas.
```

Then copy `SPEC.md` and any font/icon/image assets it lists into the new repo.

---

## Stage 1 — Scaffold + ground rules

```
Read SPEC.md. We are rebuilding Showdist as a native Android app. Before writing code, create CLAUDE.md with these standing rules for every future session:

- Language: Kotlin. UI: Jetpack Compose. AR: ARCore via SceneView (io.github.sceneview:arsceneview, latest stable). Single-activity app.
- Android only. minSdk 24, target the latest stable SDK.
- Minimal dependencies. Ask before adding any library not listed here.
- Performance rules: no object allocation inside per-frame callbacks; reuse materials, meshes and nodes; detach and destroy anchors/nodes when removed; no blur effects in the AR view, except the small shadow blurs SPEC.md §3 specifies for the reticle, points and lines (create each blurred paint once and reuse it, never per frame).
- Work in small steps. Explain what you're about to change in 3–5 lines, then do it. After each stage, give me a short "how to test on the phone" checklist.
- Never rewrite files outside the current stage's scope.

Then scaffold the project: Gradle Kotlin DSL, version catalog, Compose, SceneView, camera permission, ARCore "required" in the manifest, and a placeholder Compose screen that says SHOWDIST. Make sure ./gradlew assembleDebug succeeds. Stop there.
```

**Test:** installs and opens on your phone.

---

## Stage 2 — AR session + reticle

```
Read CLAUDE.md and SPEC.md. Stage 2 only: the AR camera screen.

- Full-screen ARSceneView with plane detection (horizontal and vertical).
- Camera permission request flow and a friendly message if ARCore is unavailable.
- Each frame, hit-test from the screen centre (plane inside its polygon, depth point, or oriented feature point — SPEC.md §2.4). Draw the reticle fixed at the centre of the camera area (per SPEC.md §3.1: three seaGreen dots in a triangle turning clockwise once every 11 s around a white centre dot, with a soft black shadow). Solid when there's a hit; hollow and faded when there isn't.
- Draw the reticle as a Compose overlay at screen centre, not as a 3D node, unless you have a strong reason otherwise — tell me which you chose and why.
- No measurement yet.
```

**Test:** point at a floor and a wall; reticle appears/disappears smoothly, no stutter.

---

## Stage 3 — Anchors + measurement (the part that broke before)

```
Read CLAUDE.md and SPEC.md. Stage 3: measurement.

- Peach red capture button. On tap, create an ARCore anchor at the current reticle hit and show a peach red dot there.
- Second tap creates the next anchor and draws a line between them. Distance = Euclidean distance between anchor pose translations, formatted per SPEC.md units/rounding.
- Support undo last point and clear all, with anchors detached and nodes destroyed properly.
- Create the dot material and line material ONCE and reuse them. Lines should be updated, not recreated, if an anchor pose refines.
- Anchors must stay locked in place as I walk around.

Add a temporary debug overlay showing: number of anchors, and app memory (Runtime totalMemory - freeMemory in MB), updating once per second.
```

**Test (this is the pass/fail):** place **20+ points** while walking around. Lines stay put, memory in the debug overlay stays roughly flat, no "start another session" crash. If it fails, tell Claude Code exactly what you saw before moving on.

---

## Stage 4 — Measurement labels

```
Read CLAUDE.md and SPEC.md. Stage 4: live measurement labels.

- Show each segment's length as white JetBrains Mono text on a dark scrim, positioned at the segment midpoint.
- Project the world midpoint to screen coordinates each frame and draw with Compose; hide labels behind the camera or off-screen.
- Show the live distance from the last point to the reticle while aiming.
- Keep per-frame work allocation-free. Remove the Stage 3 debug overlay only when I say so.
```

**Test:** labels track the lines smoothly while you move.

---

## Stage 5 — Theme + landing screen

```
Read CLAUDE.md and SPEC.md. Stage 5: theme and landing.

- Create a single Compose theme (no separate light/dark) from the SPEC.md palette, using JetBrains Mono app-wide.
- Landing screen per SPEC.md §1.4 and §6: darkTyrianBlue background, SHOWDIST wordmark in light mauve (one word, all caps, single colour), Measure and Leveler tool cards, the History link under Measure (only once something is saved), a Settings gear top-right, and upward ruler tick marks along the bottom that drift slowly right (static when reduce-motion is on).
- Wire navigation to the AR screen. Leveler, History and Settings can be placeholders.
```

---

## Stage 6 — Leveler

```
Read CLAUDE.md and SPEC.md. Stage 6: the leveler.

- Use the accelerometer (~50 Hz), low-pass smoothed per SPEC.md §4.1. Single axis only (side-to-side roll), like a physical bubble level for hanging things on a wall.
- Work in portrait and landscape, using whichever long edge is against the surface.
- Zero/tare button to null against the wall, plus Reset to true level. Don't persist the offset: it resets each time the screen opens.
- Visual per SPEC.md §4.2: hardware-store bubble vial on a warm grey background, seaGreen liquid, white bubble with a darkTyrianBlue outline, darkTyrianBlue centre marks that turn white when level (within 0.3°), and a darkTyrianBlue degree readout.
- Unregister sensor listeners when the screen isn't visible.
```

---

## Stage 7 — About, settings, licences

```
Read CLAUDE.md and SPEC.md. Stage 7: About/settings.

- Settings (from the landing gear) per SPEC.md §1.5: Units (Imperial/Metric, persisted), and a row that opens About.
- About per SPEC.md §1.6 and §7.1: version, description, developer email, "View licenses", and "Created with AI assistance". Update the Flutter wording for the native app.
- Licenses per SPEC.md §1.7 and §7.2, listing the third-party resources actually shipped. All themed with the app palette (no default Material colours anywhere).
```

---

## Stage 8 — Icon, polish, performance check, release

```
Read CLAUDE.md and SPEC.md. Stage 8: finish.

1. Adaptive app icon per SPEC.md §8.1: light mauve tile, three seaGreen dots in a triangle around a white centre dot, darkTyrianBlue ring at the edge (the icon XMLs are already in the repo).
2. Remove the debug overlay and any unused dependencies/resources. Enable R8 minification and resource shrinking for release.
3. Add LeakCanary as a debug-only dependency, and tell me how to run a 5-minute measuring session to check for leaks.
4. Set up a signed release build (AAB) for Google Play and walk me through creating the keystore. Do not commit the keystore or passwords.
```

---

## If something goes wrong mid-stage

```
Stop. Don't change more code yet. Here's what I saw on the phone: [describe]. Tell me the most likely cause in a few lines, then propose the smallest fix.
```
