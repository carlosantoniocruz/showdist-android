# Showdist (native Android rebuild)

`SPEC.md` is the source of truth for behaviour, layout, colours and text. `SHOWDIST_REBUILD_PLAN.md` holds the stage-by-stage prompts. Read `SPEC.md` before each stage; read only the files the stage needs.

## Standing rules

- Language: Kotlin. UI: Jetpack Compose with Material 3 (`androidx.compose.material3`, via the Compose BOM; approved in Stage 5). AR: ARCore via SceneView (`io.github.sceneview:arsceneview`, latest stable). Single-activity app.
- Android only. minSdk 24, target the latest stable SDK.
- Minimal dependencies. Ask before adding any library not listed here.
- Performance rules: no object allocation inside per-frame callbacks; reuse materials, meshes and nodes; detach and destroy anchors/nodes when removed; no blur effects in the AR view, except the small shadow blurs SPEC.md §3 specifies for the reticle, points and lines (create each blurred paint once and reuse it, never per frame).
- Work in small steps. Explain what you're about to change in 3–5 lines, then do it. After each stage, give a short "how to test on the phone" checklist.
- Never rewrite files outside the current stage's scope.

## Repo notes

- Build: `./gradlew assembleDebug`. Versions live in `gradle/libs.versions.toml`.
- SceneView 4.39 requires compileSdk 37, so compileSdk and targetSdk are 37.
- JetBrains Mono's four faces are in `app/src/main/res/font/`; license texts (OFL, Apache 2.0, MIT) are in `app/src/main/res/raw/`.
- The Licenses list in `settings/Licenses.kt` is curated by hand. When a dependency changes, check `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` and update it.
- LeakCanary 2.14 is a `debugImplementation` only (approved in Stage 8); it adds a "Leaks" launcher icon to debug builds.
- Release builds use R8 and resource shrinking. After changing dependencies, install a release build and exercise Measure to catch missing keep rules (`app/proguard-rules.pro`).
- Release signing reads the gitignored `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword); the keystore lives outside the repo. `./gradlew bundleRelease` builds the AAB.
- Icons are Material Symbols drawn from path data in `ui/Icons.kt`; no icon library.
