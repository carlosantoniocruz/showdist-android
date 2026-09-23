package com.showconfigs.showdist.settings

import android.content.Context
import androidx.annotation.RawRes
import com.showconfigs.showdist.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Third-party components actually shipped (SPEC.md §7.2), curated by hand. Keep this in
 * sync with the dependency tree (`./gradlew :app:dependencies`) when libraries change.
 * [intro] paragraphs come first; [texts] are full license files from res/raw.
 */
private class LicenseSource(val name: String, val intro: List<String>, @param:RawRes val texts: List<Int>)

private const val APACHE_NOTE = "Licensed under the Apache License, Version 2.0 (full text below)."

private val SOURCES = listOf(
    LicenseSource(
        "AndroidX and Jetpack Compose",
        listOf(
            "Copyright The Android Open Source Project.",
            "Activity, Annotation, AppCompat, Collection, Compose (Runtime, UI, Foundation, Animation, " +
                "Material 3), Core, Emoji2, Fragment, Graphics, Lifecycle, Navigation Event, " +
                "Profile Installer, SavedState, Startup, Tracing, Window and their supporting modules.",
            APACHE_NOTE,
        ),
        listOf(R.raw.license_apache_2_0),
    ),
    LicenseSource(
        "ARCore SDK",
        listOf(
            "Google ARCore SDK for Android (com.google.ar:core 1.54.0).",
            "Provided by Google under the ARCore Additional Terms of Service: " +
                "https://developers.google.com/ar/develop/terms",
        ),
        emptyList(),
    ),
    LicenseSource(
        "Filament",
        listOf("Filament rendering engine by Google (filament-android, gltfio, filament-utils 1.72.1).", APACHE_NOTE),
        listOf(R.raw.license_apache_2_0),
    ),
    LicenseSource(
        "Fuel",
        listOf("Fuel HTTP library for Kotlin (2.3.1), a dependency of SceneView.", "MIT License."),
        listOf(R.raw.license_mit_fuel),
    ),
    LicenseSource(
        "Guava ListenableFuture, JSpecify and JetBrains Annotations",
        listOf("Annotation and concurrency support libraries.", APACHE_NOTE),
        listOf(R.raw.license_apache_2_0),
    ),
    LicenseSource(
        "JetBrains Mono",
        listOf(
            "JetBrains Mono is licensed under the SIL Open Font License, Version 1.1.",
            "Copyright 2020 The JetBrains Mono Project Authors (https://github.com/JetBrains/JetBrainsMono)",
        ),
        listOf(R.raw.license_ofl_1_1),
    ),
    LicenseSource(
        "Kotlin and kotlinx",
        listOf("Kotlin standard library, kotlinx.coroutines and kotlinx.serialization by JetBrains.", APACHE_NOTE),
        listOf(R.raw.license_apache_2_0),
    ),
    LicenseSource(
        "kotlin-math",
        listOf("Kotlin math library by Romain Guy (1.8.0), a dependency of SceneView.", APACHE_NOTE),
        listOf(R.raw.license_apache_2_0),
    ),
    LicenseSource(
        "Material Symbols",
        listOf("Icons by Google, drawn from their path data.", APACHE_NOTE),
        listOf(R.raw.license_apache_2_0),
    ),
    LicenseSource(
        "Result",
        listOf("Result type for Kotlin (3.1.0), a dependency of Fuel.", "MIT License."),
        listOf(R.raw.license_mit_result),
    ),
    LicenseSource(
        "SceneView",
        listOf("3D and AR for Android (sceneview, arsceneview 4.39.0).", APACHE_NOTE),
        listOf(R.raw.license_apache_2_0),
    ),
    LicenseSource(
        "Showdist",
        listOf("This app's own license."),
        listOf(R.raw.license_mit_showdist),
    ),
)

/** One paragraph of license text; [centred] ones are centre-aligned (SPEC.md §1.7). */
class LicenseParagraph(val text: String, val centred: Boolean)

class License(val name: String, val paragraphs: List<LicenseParagraph>)

/** Builds the license list once per app run, off the main thread, and caches it. */
object Licenses {
    @Volatile
    private var cache: List<License>? = null

    /** Already-loaded list, or null if [load] hasn't finished yet. */
    val loaded: List<License>? get() = cache

    suspend fun load(context: Context): List<License> = cache ?: withContext(Dispatchers.IO) {
        val res = context.applicationContext.resources
        val textCache = HashMap<Int, List<LicenseParagraph>>()
        SOURCES
            .sortedBy { it.name.lowercase() }
            .map { source ->
                val paragraphs = source.intro.map { LicenseParagraph(it, centred = false) } +
                    source.texts.flatMap { id ->
                        textCache.getOrPut(id) {
                            paragraphsOf(res.openRawResource(id).bufferedReader().use { it.readText() })
                        }
                    }
                License(source.name, paragraphs)
            }
            .also { cache = it }
    }

    /**
     * Splits hard-wrapped plain text on blank lines and reflows each paragraph. A paragraph
     * whose every line is indented by 10+ spaces (e.g. the Apache title block) is centred.
     */
    private fun paragraphsOf(text: String): List<LicenseParagraph> =
        text.split(Regex("\\n\\s*\\n"))
            .map { block -> block.lines().filter { it.isNotBlank() } }
            .filter { it.isNotEmpty() }
            .map { lines ->
                LicenseParagraph(
                    text = lines.joinToString(" ") { it.trim() },
                    centred = lines.all { line -> line.takeWhile { it == ' ' }.length >= 10 },
                )
            }
}
