package com.showconfigs.showdist.measure

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * A lifecycle that follows the activity's, but stays at most STARTED while [capped].
 * Handing it to ARSceneView pauses the AR session in place when another screen covers
 * Measure (ON_PAUSE), and resumes it when uncovered, without closing the session.
 */
@Composable
fun rememberCappedLifecycle(capped: Boolean): Lifecycle {
    val parent = LocalLifecycleOwner.current.lifecycle
    val owner = remember(parent) { CappedLifecycleOwner(parent) }
    SideEffect { owner.capped = capped }
    DisposableEffect(owner) { onDispose { owner.detach() } }
    return owner.lifecycle
}

private class CappedLifecycleOwner(private val parent: Lifecycle) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    var capped = false
        set(value) {
            field = value
            update()
        }

    private val observer = LifecycleEventObserver { _, _ -> update() }

    init {
        parent.addObserver(observer)
    }

    fun detach() = parent.removeObserver(observer)

    private fun update() {
        val state = parent.currentState
        // A lifecycle that never started can't be moved straight to DESTROYED.
        if (state == Lifecycle.State.DESTROYED && registry.currentState == Lifecycle.State.INITIALIZED) return
        registry.currentState =
            if (capped && state.isAtLeast(Lifecycle.State.STARTED)) Lifecycle.State.STARTED else state
    }
}
