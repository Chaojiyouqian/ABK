package com.abk.kernel.ui.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A screen scaffold that hosts a [LocalBlurState] backdrop for frosted-glass bars,
 * replicating the Miuix structure:
 *
 * - the **body is laid out full-screen from y=0** (not pushed below the top bar) and its
 *   scroll container fills the whole screen, so content scrolls *beneath* the top bar;
 * - the backdrop captures that full-screen body — meaning the top-bar region has content
 *   (the scrolling page) for the bar to blur;
 * - the [topBar] is drawn as a floating overlay **outside** the backdrop's capture
 *   region. This is essential: if the top bar were inside the captured content, its own
 *   blur would sample itself and crash the render thread (SIGSEGV).
 *
 * The top bar is measured with a [SubcomposeLayout] **before** the body, so the body
 * receives the real bar height on its very first layout pass — there is no one-frame
 * inset jump from a zero-seeded height.
 *
 * Screens call it with a body that scrolls full-screen and reserves the bar height via a
 * leading [androidx.compose.foundation.layout.Spacer]:
 * ```
 * BlurScreenScaffold(
 *   blurConfig = ...,
 *   containerColor = ...,
 *   topBar = { ExpressiveTopBar(..., enableBlur = ...) },
 * ) { topBarHeight ->
 *   Column(Modifier.fillMaxSize().verticalScroll(...)) {
 *     Spacer(Modifier.height(topBarHeight + 16.dp))   // visual inset, NOT layout inset
 *     ...content...
 *   }
 * }
 * ```
 *
 * @param blurConfig Blur preferences for this screen.
 * @param containerColor Background color of the scaffold.
 * @param topBar Floating top bar overlay (draws above the content, outside the backdrop).
 * @param content Full-screen body; receives the measured [topBar] height so it can insert
 * a leading [androidx.compose.foundation.layout.Spacer] instead of applying layout padding.
 */
@Composable
fun BlurScreenScaffold(
    blurConfig: BlurConfig,
    modifier: Modifier = Modifier,
    containerColor: Color,
    topBar: @Composable (() -> Unit)? = null,
    content: @Composable (topBarHeight: Dp) -> Unit,
) {
    val density = LocalDensity.current
    BlurHost(blurConfig = blurConfig) {
        SubcomposeLayout(
            modifier = modifier.fillMaxSize().background(containerColor)
        ) { constraints ->
            // Measure the floating top bar first so the body is laid out with the real
            // bar height on frame one.
            val barPlaceables = if (topBar != null) {
                subcompose("abk-top-bar", topBar)
                    .map { it.measure(constraints.copy(minHeight = 0)) }
            } else {
                emptyList()
            }
            val barHeightPx = barPlaceables.maxOfOrNull { it.height } ?: 0
            // The body is the backdrop's blur source; it must never include the top bar
            // (that would sample the bar into itself and crash the render thread).
            val contentPlaceable = subcompose("abk-body") {
                Box(Modifier.fillMaxSize().blurSourceBody()) {
                    content(with(density) { barHeightPx.toDp() })
                }
            }.first().measure(constraints)
            layout(constraints.maxWidth, constraints.maxHeight) {
                contentPlaceable.place(0, 0)
                barPlaceables.forEach { it.place(0, 0) }
            }
        }
    }
}
