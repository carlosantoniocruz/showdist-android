# Showdist (native Android rebuild)

`SPEC.md` is the source of truth for behaviour, layout, colours and text. `SHOWDIST_REBUILD_PLAN.md` holds the stage-by-stage prompts. Read `SPEC.md` before each stage; read only the files the stage needs.

## Standing rules

- Language: Kotlin. UI: Jetpack Compose. AR: ARCore via SceneView (`io.github.sceneview:arsceneview`, latest stable). Single-activity app.
- Android only. minSdk 24, target the latest stable SDK.
- Minimal dependencies. Ask before adding any library not listed here.
- Performance rules: no object allocation inside per-frame callbacks; reuse materials, meshes and nodes; detach and destroy anchors/nodes when removed; no blur effects in the AR view, except the small shadow blurs SPEC.md §3 specifies for the reticle, points and lines (create each blurred paint once and reuse it, never per frame).
- Work in small steps. Explain what you're about to change in 3–5 lines, then do it. After each stage, give a short "how to test on the phone" checklist.
- Never rewrite files outside the current stage's scope.

## Repo notes

- Build: `./gradlew assembleDebug`. Versions live in `gradle/libs.versions.toml`.
- SceneView 4.39 requires compileSdk 37, so compileSdk and targetSdk are 37.
- `assets/fonts/` and `android/app/src/main/res/` are reference copies from the old Flutter app (SPEC.md §8.2), not part of the build. Move them into `app/src/main/res/` in the stage that needs them (fonts: Stage 5, icon: Stage 8).
