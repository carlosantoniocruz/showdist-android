package com.showconfigs.showdist.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.BackBar
import com.showconfigs.showdist.ui.Corner
import com.showconfigs.showdist.ui.Haptics
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.Type
import com.showconfigs.showdist.ui.drawAppMark
import com.showconfigs.showdist.ui.readingColumn
import com.showconfigs.showdist.ui.rememberHaptics
import com.showconfigs.showdist.ui.rememberReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val DEVELOPER_NAME = "Carlos Cruz"
private const val X_HANDLE = "@shcarlosconfigs"
private const val X_URL = "https://x.com/shcarlosconfigs"
private const val PRIVACY_POLICY_URL = "https://carlosantoniocruz.github.io/showdist-privacy/"

/** The length of the mark's line on the page; its dots and ticks reach a little past each end. */
private val MARK_WIDTH = 88.dp

/** The hold's touch target: the mark with a wide margin, most of it below, where a thumb lands. */
private val TARGET_WIDTH = 280.dp
private val TARGET_HEIGHT = 168.dp

/** The mark sits this far below the target's top, so it stays above a thumb pressing the middle. */
private val MARK_FROM_TOP = 52.dp

/** How long the dots take to meet, and to ease back if the finger lifts first. */
private const val HOLD_MS = 2200
private const val EASE_BACK_MS = 350
private const val BURST_MS = 380

/**
 * About (SPEC.md §1.6): the app's mark, large, above its name; then the version, the
 * developer, the link to their X account, and "Created with AI assistance". Pressing and
 * holding the mark slides its two dots together; when they meet, a burst, and the facts
 * roll ([FactsScene]). Nothing here animates until the mark is held, and it all stops when
 * the facts are closed (Back) or the page is left.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // Read from the installed package, so it can't drift from the build's versionName.
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    var facts by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = facts) { facts = false }

    Scaffold(
        topBar = { ShowdistAppBar("ABOUT") },
        bottomBar = { BackBar(onBack = { if (facts) facts = false else onBack() }) },
        containerColor = Palette.Black,
    ) { padding ->
        if (facts) {
            FactsScene(rememberFactDeck(), rememberReduceMotion(), Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.readingColumn().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HoldMark(onMeet = { facts = true })
                Text("SHOWDIST", style = Type.Title.copy(letterSpacing = 4.sp))
                Spacer(Modifier.height(4.dp))
                Text("Version $version", style = Type.Secondary)
                Spacer(Modifier.height(14.dp))
                Text(DEVELOPER_NAME, style = Type.Body)
                XContactRow(onClick = { runCatching { uriHandler.openUri(X_URL) } })
                Text("Created with AI assistance", style = Type.Secondary)

                SettingsDivider(Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
                // Required by the ARCore terms of service.
                Text(
                    "This application runs on Google Play Services for AR (ARCore), which is provided " +
                        "by Google LLC and governed by the Google Privacy Policy.",
                    Modifier.padding(horizontal = 16.dp),
                    style = Type.Secondary.copy(fontSize = 13.sp, textAlign = TextAlign.Center),
                )
                SettingsRow(
                    "Privacy policy",
                    subtitle = "Opens in your browser",
                    // No browser to handle it: nothing to do.
                    onClick = { runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) } },
                ) { Chevron() }
            }
        }
    }
}

/**
 * The mark and its hold (SPEC.md §1.6). The target is much larger than the mark, edges and
 * margin included, and the mark sits in its upper part, so a thumb anywhere near starts the
 * hold without covering the dots. A tick on touch; the dots slide together over [HOLD_MS]
 * and swell as the gap closes; sliding the thumb changes nothing, only lifting does, and
 * lifting early eases them back. When they meet: a confirming tap, one expanding ring, then
 * [onMeet].
 */
@Composable
private fun HoldMark(onMeet: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics: Haptics = rememberHaptics()
    val close = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    Canvas(
        Modifier
            .size(TARGET_WIDTH, TARGET_HEIGHT)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    haptics.tick()
                    var met = false
                    val hold: Job = scope.launch {
                        close.animateTo(1f, tween(((1f - close.value) * HOLD_MS).toInt(), easing = LinearEasing))
                        met = true
                        haptics.confirm()
                        burst.snapTo(0f)
                        burst.animateTo(1f, tween(BURST_MS, easing = FastOutSlowInEasing))
                        onMeet()
                        close.snapTo(0f)
                        burst.snapTo(0f)
                    }
                    // Only lifting ends the hold: every move is taken here, so neither a
                    // sliding thumb nor the page's scrolling can cancel it.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (!met) {
                        hold.cancel()
                        scope.launch { close.animateTo(0f, tween(EASE_BACK_MS, easing = FastOutSlowInEasing)) }
                    }
                }
            }
            .semantics { contentDescription = "Showdist. Press and hold for facts about measuring." },
    ) {
        val centre = Offset(size.width / 2f, MARK_FROM_TOP.toPx())
        val length = MARK_WIDTH.toPx()
        drawAppMark(centre, length, close.value)
        val b = burst.value
        if (b > 0f) {
            drawCircle(Palette.Accent, radius = length * 0.9f * b, center = centre, alpha = 1f - b, style = Stroke(2.dp.toPx()))
        }
    }
}

/**
 * The developer's only contact (SPEC.md §1.6): the X logo and the handle beside it in the
 * accent, one tappable row (at least 48 dp tall) that opens the profile in the X app or the
 * browser.
 */
@Composable
private fun XContactRow(onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(Corner)
            .clickable(role = Role.Button, onClickLabel = "Open on X", onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "Developer on X, $X_HANDLE"
                role = Role.Button
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ShowdistIcons.XLogo, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(X_HANDLE, style = Type.Label.copy(color = Palette.Accent))
    }
}
