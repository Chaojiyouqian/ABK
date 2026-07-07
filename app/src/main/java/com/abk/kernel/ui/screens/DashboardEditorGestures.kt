package com.abk.kernel.ui.screens

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.hypot

@Composable
internal fun rememberEditorPinchObserver(
    onTrigger: () -> Unit
): (MotionEvent) -> Boolean = remember(onTrigger) {
    var baselineDistance = 0f
    var opened = false

    { event: MotionEvent ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                baselineDistance = 0f
                opened = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    baselineDistance = event.pointerDistance()
                    opened = false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    if (baselineDistance <= 0f) {
                        baselineDistance = event.pointerDistance()
                    } else if (!opened) {
                        val zoom = event.pointerDistance() / baselineDistance
                        if (zoom < 0.92f) {
                            opened = true
                            onTrigger()
                        }
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    baselineDistance = 0f
                    opened = false
                }
            }
        }
        false
    }
}

private fun MotionEvent.pointerDistance(): Float {
    if (pointerCount < 2) return 0f
    val dx = getX(1) - getX(0)
    val dy = getY(1) - getY(0)
    return hypot(dx.toDouble(), dy.toDouble()).toFloat()
}
