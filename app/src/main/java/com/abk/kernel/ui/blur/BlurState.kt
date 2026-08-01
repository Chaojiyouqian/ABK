package com.abk.kernel.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

/**
 * Composition local that exposes the active [LayerBackdrop] to blur consumers.
 *
 * Content that should be captured as the blur source attaches [Modifier.blurSource]
 * (wraps [top.yukonga.miuix.kmp.blur.layerBackdrop]); bars that should show a
 * frosted-glass effect attach [Modifier.blurEffect] (wraps
 * [top.yukonga.miuix.kmp.blur.textureBlur]). Both no-op when no backdrop is provided
 * or the device does not support RenderEffect (API < 31).
 */
val LocalBlurState = compositionLocalOf<LayerBackdrop?> { null }

/**
 * Immutable snapshot of the blur feature's preferences for one screen.
 *
 * Collapses the four values that every [BlurScreenScaffold] call site used to spell
 * out into a single argument, so future toggles touch one place instead of ~21.
 */
data class BlurConfig(
    val blurEnabled: Boolean,
    val backgroundExpEnabled: Boolean,
    val backgroundUri: String?,
    val backgroundImageEnabled: Boolean,
) {
    /** True when a custom background should be drawn into the blur source. */
    val wantsBackgroundPainter: Boolean
        get() = blurEnabled && backgroundExpEnabled && backgroundImageEnabled && !backgroundUri.isNullOrBlank()
}

/**
 * Whether frosted-glass will actually render for [enableBlur] in the current composition.
 *
 * The miuix render path needs three things: the user toggle, an active backdrop
 * (present only when RenderEffect is supported, i.e. API >= 31), and the runtime
 * shader the blur effect runs on (API >= 33). Callers must use this to decide between
 * a transparent bar (blur shows through) and the opaque fallback surface — gating the
 * transparent color on [enableBlur] alone leaves bars fully transparent on API < 33.
 */
@Composable
fun isBlurActive(enableBlur: Boolean): Boolean =
    enableBlur && LocalBlurState.current != null && isRuntimeShaderSupported()
