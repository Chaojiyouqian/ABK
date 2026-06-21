package com.abk.kernel.miuix.util

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported

/**
 * Creates a [LayerBackdrop] that captures content for blur effects.
 *
 * @param enableBlur Whether blur is enabled. Returns `null` when disabled.
 * @param surfaceColor The surface color to draw as an opaque base to prevent bleed-through.
 * Should match the current theme's surface color (MD3 or MIUIX).
 * @return A [LayerBackdrop] for use with [BlurredBar] and [Modifier.layerBackdrop], or `null`.
 */
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean, surfaceColor: Color): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    return rememberLayerBackdrop {
        drawRect(surfaceColor) // Opaque base prevents bleed-through
        drawContent()
    }
}

/**
 * Wraps content with a blur effect when [backdrop] is non-null.
 *
 * Use this to wrap a [TopAppBar] in the Scaffold's `topBar` slot. Set the TopAppBar's
 * `color` to [Color.Transparent] when the backdrop is active so the blur shows through.
 *
 * @param backdrop The [LayerBackdrop] providing captured content. Pass `null` to skip blur.
 * @param surfaceColor The surface color for the blur blend. Should match the current theme's
 * surface color (MD3 or MIUIX).
 * @param blurActive Whether blur is currently active. Defaults to `true`. Set to `false` to
 * temporarily disable blur without removing the backdrop.
 * @param content The composable to wrap (typically a [TopAppBar]).
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    surfaceColor: Color,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (blurActive && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = surfaceColor.copy(0.87f)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}
