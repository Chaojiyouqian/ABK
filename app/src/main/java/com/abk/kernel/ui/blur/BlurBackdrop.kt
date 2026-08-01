package com.abk.kernel.ui.blur

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import com.abk.kernel.ui.theme.LocalUiSurfaceAlpha

internal const val AbkBlurRadius = 25f
internal const val AbkBlurCardAlpha = 0.87f
internal const val AbkBlurMinBlendAlpha = 0.4f
internal const val AbkBlurBackgroundDim = 0.35f

/**
 * Creates a [LayerBackdrop] capturing the content drawn beneath the blurred bars.
 *
 * The draw callback first paints an optional background [Painter] (the custom
 * background image, when enabled), then a dimmed surface base for contrast, then the
 * content that will be captured as the blur source. Returns `null` when blur is
 * disabled or the device cannot run the frosted-glass shader (API < 33), in which
 * case the app falls back to opaque surfaces.
 *
 * @param enableBlur Whether blur is enabled.
 * @param surfaceColor The theme surface color used for the dimmed base.
 * @param backgroundPainter Optional background image drawn into the backdrop so it
 * becomes part of the blur source. Pass `null` to blur page content only.
 * @param backgroundDim Alpha of the dimmed base drawn under [backgroundPainter].
 */
@Composable
fun rememberBlurBackdrop(
    enableBlur: Boolean,
    surfaceColor: Color,
    backgroundPainter: Painter? = null,
    backgroundDim: Float = AbkBlurBackgroundDim,
): LayerBackdrop? {
    if (!enableBlur || !isBlurCapableDevice()) return null
    return rememberLayerBackdrop {
        backgroundPainter?.let { painter ->
            with(painter) { draw(size = drawContext.size) }
        }
        drawRect(surfaceColor.copy(alpha = backgroundDim))
        drawContent()
    }
}

/**
 * Marks content as the blur source for the active backdrop.
 *
 * Attach to the page content box (inside the Scaffold body, not the bar itself) so
 * that content scrolling beneath the bars is captured and blurred.
 */
@Composable
fun Modifier.blurSource(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return LocalBlurState.current?.let { backdrop ->
        this.then(Modifier.layerBackdrop(backdrop))
    } ?: this
}

/**
 * Applies a frosted-glass effect to a bar using the active backdrop.
 *
 * Attach to a top/bottom bar whose container color is [Color.Transparent] when the
 * backdrop is active, so the blurred content shows through.
 *
 * @param blendColor Optional tint blended over the blurred content. Defaults to the
 * theme's surfaceContainer scaled by the live "界面不透明度" slider
 * ([LocalUiSurfaceAlpha], floored at [AbkBlurMinBlendAlpha] for legibility), so the
 * frosted bars stay consistent with cards and the blur-off fallback.
 */
@Composable
fun Modifier.blurEffect(blendColor: Color = Color.Unspecified): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return LocalBlurState.current?.let { backdrop ->
        val effective = if (blendColor == Color.Unspecified) {
            MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = (AbkBlurCardAlpha * LocalUiSurfaceAlpha.current).coerceAtLeast(AbkBlurMinBlendAlpha)
            )
        } else {
            blendColor
        }
        this.then(
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = AbkBlurRadius,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = effective),
                    ),
                ),
            )
        )
    } ?: this
}
