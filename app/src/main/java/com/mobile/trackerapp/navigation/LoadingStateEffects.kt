package com.deep.lumoraai.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Navigation loader safety net.
 *
 * Full-screen loaders can get "stuck" when the user navigates away while a
 * ViewModel is still in a loading state: because the ViewModel survives the
 * navigation, re-entering the destination (or returning to it) shows the stale
 * spinner. These effects explicitly reset the loading flag on the relevant
 * lifecycle/navigation transitions so a spinner never outlives the operation
 * that started it.
 */

/**
 * Resets a loading state when this destination leaves the composition
 * (i.e. the user navigates away). [reset] is invoked exactly once in
 * onDispose.
 *
 * Usage inside a Route composable:
 * ```
 * ResetLoadingOnLeave { viewModel.resetLoading() }
 * ```
 */
@Composable
fun ResetLoadingOnLeave(reset: () -> Unit) {
    DisposableEffect(Unit) {
        onDispose { reset() }
    }
}

/**
 * Resets a loading state whenever the destination's lifecycle drops below
 * STARTED (backgrounded, or covered by a navigation transition) and again when
 * the composable is disposed. This guards both the "navigated away" and
 * "returned while a stale spinner is showing" cases.
 */
@Composable
fun ResetLoadingOnStop(reset: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                reset()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            reset()
        }
    }
}

/**
 * Resets a loading state once, when the destination is first entered. Useful for
 * clearing any loading flag left over from a previous visit before the screen
 * kicks off its own work.
 */
@Composable
fun ResetLoadingOnEnter(reset: () -> Unit) {
    LaunchedEffect(Unit) { reset() }
}
