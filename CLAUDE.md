# Showdist (native Android rebuild)

`SPEC.md` is the source of truth for behaviour, layout, colours and text. `SHOWDIST_REBUILD_PLAN.md` holds the stage-by-stage prompts. Read `SPEC.md` before each stage; read only the files the stage needs.

## Standing rules

- Language: Kotlin. UI: Jetpack Compose with Material 3 (`androidx.compose.material3`, via the Compose BOM; approved in Stage 5). AR: ARCore via SceneView (`io.github.sceneview:arsceneview`, latest stable). Single-activity app.
- Android only. minSdk 24, target the latest stable SDK.
- Minimal dependencies. Ask before adding any library not listed here.
- Performance rules: no object allocation inside per-frame callbacks; reuse materials, meshes and nodes; detach and destroy anchors/nodes when removed; no blur effects anywhere (v1.5: marks over the camera get a crisp near-black backing instead, SPEC.md §3).
- Work in small steps. Explain what you're about to change in 3–5 lines, then do it. After each stage, give a short "how to test on the phone" checklist.
- Never rewrite files outside the current stage's scope.

## Design rules (v1.5; SPEC.md §0a has them in full)

After Dieter Rams: as little design as possible. Check every change against these.

- True black; white for text, numbers and the reticle dot; ONE accent, cyan, only ever as `Palette.Accent`; peach red only on the capture button. Never colour by unit system. No gradients, shadows, blur or glow.
- JetBrains Mono everywhere; one corner (`Corner`, 4 dp) on every box; hairline dividers; tight padding; capped number sizes.
- Every tappable control is at the bottom, in the thumb zone; the top of a screen is passive. Bottom rows mirror for the left hand (`HandSetting`).
- In Measure the bottom row never moves when the phone is turned; only its buttons and the text over the camera turn (`ui/DeviceOrientation.kt` is the one orientation handler; the activity stays portrait).
- Nothing animates unless it helps the task. The AR session and the keep-awake flag run only while Measure is actually in use.
- Before adding a control, ask what can be removed instead. Clear and the History count badge were removed for this reason.

## Repo notes

- Build: `./gradlew assembleDebug`. Versions live in `gradle/libs.versions.toml`.
- SceneView 4.39 requires compileSdk 37, so compileSdk and targetSdk are 37.
- JetBrains Mono's four faces are in `app/src/main/res/font/`; license texts (OFL, Apache 2.0, MIT) are in `app/src/main/res/raw/`.
- The Licenses list in `settings/Licenses.kt` is curated by hand. When a dependency changes, check `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` and update it.
- LeakCanary 2.14 is a `debugImplementation` only (approved in Stage 8); it adds a "Leaks" launcher icon to debug builds.
- Release builds use R8 and resource shrinking. After changing dependencies, install a release build and exercise Measure to catch missing keep rules (`app/proguard-rules.pro`).
- Release signing reads the gitignored `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword); the keystore lives outside the repo. `./gradlew bundleRelease` builds the AAB.
- Icons are Material Symbols drawn from path data in `ui/Icons.kt`; no icon library. The app mark is drawn by `ui/AppMark.kt` and must match `res/drawable/ic_launcher_foreground.xml` (dots on the line/tick intersections).
- Testing on the phone: check Showdist is the resumed activity before sending any `adb shell input`; the user often has the phone in hand.
