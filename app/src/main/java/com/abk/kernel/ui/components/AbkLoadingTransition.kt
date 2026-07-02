package com.abk.kernel.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.ui.theme.uiSurfaceColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val AbkLoadingAccent = Color(0xFFB6F0A2)

@Composable
fun AbkLoadingPill(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val fallbackContainer = if (colors.surfaceContainerHighest.luminance() > 0.45f) {
        colors.inverseSurface.copy(alpha = 0.9f)
    } else {
        colors.surfaceContainerHighest.copy(alpha = 0.94f)
    }
    val containerColor = uiSurfaceColor(fallbackContainer)
    val contentColor = if (containerColor.luminance() > 0.35f) {
        colors.inverseOnSurface
    } else {
        colors.onSurface
    }
    val horizontalPadding = if (compact) 16.dp else 20.dp
    val verticalPadding = if (compact) 12.dp else 16.dp
    val glyphSize = if (compact) 22.dp else 28.dp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AbkLoadingGlyph(
                modifier = Modifier.size(glyphSize),
                tint = AbkLoadingAccent
            )
            Text(
                text = text,
                color = contentColor,
                style = if (compact) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.titleLarge
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AbkCenteredLoadingTransition(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AbkLoadingPill(text = text)
    }
}

@Composable
private fun AbkLoadingGlyph(
    modifier: Modifier = Modifier,
    tint: Color = AbkLoadingAccent
) {
    val transition = rememberInfiniteTransition(label = "abk-loading-glyph")
    val rotationDegrees by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6400, easing = LinearEasing)
        ),
        label = "abk-loading-rotation"
    )
    val scale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "abk-loading-scale"
    )

    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = rotationDegrees
            scaleX = scale
            scaleY = scale
        }
    ) {
        val radius = size.minDimension
        val orbit = radius * 0.24f
        val petalRadius = radius * 0.14f
        val centerRadius = radius * 0.22f

        repeat(8) { index ->
            val angle = (index / 8f) * (2f * PI.toFloat())
            val petalCenter = Offset(
                x = center.x + cos(angle.toDouble()).toFloat() * orbit,
                y = center.y + sin(angle.toDouble()).toFloat() * orbit
            )
            drawCircle(
                color = tint,
                radius = if (index % 2 == 0) petalRadius else petalRadius * 0.9f,
                center = petalCenter
            )
        }

        drawCircle(
            color = tint,
            radius = centerRadius,
            center = center
        )
    }
}
