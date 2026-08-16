package com.dnc1981.musickontrol.utils

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

enum class GestureDirection {
    RIGHT,
    LEFT,
    UP,
    DOWN,
    NONE
}

data class GestureEvent(
    val direction: GestureDirection,
    val velocity: Float,
    val distance: Float
)

object AAOSGestureConfig {
    const val MIN_SWIPE_DISTANCE_DP = 80f
    const val MIN_SWIPE_VELOCITY = 200f
    const val MAX_ANGLE_DEGREES = 25f
    const val GESTURE_TIMEOUT_MS = 500L
    const val EDGE_MARGIN_DP = 40f
}

fun Modifier.aaosGestureDetector(
    context: Context,
    enabled: Boolean = true,
    onGesture: (GestureEvent) -> Unit
): Modifier = this.pointerInput(enabled) {
    if (!enabled) return@pointerInput

    detectDragGestures(
        onDragStart = {},
        onDragEnd = {},
        onDragCancel = {},
        onDrag = { change, dragAmount ->
            // ✅ VALIDAR ZONA SEGURA (NO EN BORDES)
            val isInValidZone = change.position.x > AAOSGestureConfig.EDGE_MARGIN_DP &&
                    change.position.x < (size.width - AAOSGestureConfig.EDGE_MARGIN_DP) &&
                    change.position.y > AAOSGestureConfig.EDGE_MARGIN_DP &&
                    change.position.y < (size.height - AAOSGestureConfig.EDGE_MARGIN_DP)

            if (!isInValidZone) {
                return@detectDragGestures
            }

            val horizontalDistance = dragAmount.x
            val verticalDistance = dragAmount.y

            // ✅ CALCULAR DISTANCIA TOTAL
            val totalDistance = kotlin.math.sqrt(
                horizontalDistance * horizontalDistance +
                        verticalDistance * verticalDistance
            )

            val minSwipeDistancePx = AAOSGestureConfig.MIN_SWIPE_DISTANCE_DP * density

            // ✅ VALIDAR DISTANCIA MÍNIMA
            if (totalDistance < minSwipeDistancePx) {
                return@detectDragGestures
            }

            // ✅ DETERMINAR SI ES HORIZONTAL O VERTICAL
            val isHorizontal = abs(horizontalDistance) > abs(verticalDistance)
            val isVertical = abs(verticalDistance) > abs(horizontalDistance)

            // ✅ CALCULAR ÁNGULO PARA EVITAR DIAGONALES
            val angle = kotlin.math.atan2(
                abs(verticalDistance),
                abs(horizontalDistance)
            ) * 180f / kotlin.math.PI

            // ✅ RECHAZAR SI ES DEMASIADO DIAGONAL
            if (angle > AAOSGestureConfig.MAX_ANGLE_DEGREES &&
                angle < (90f - AAOSGestureConfig.MAX_ANGLE_DEGREES)
            ) {
                return@detectDragGestures
            }

            // ✅ CALCULAR VELOCIDAD
            val velocity = totalDistance / 16f

            // ✅ VALIDAR VELOCIDAD MÍNIMA
            if (velocity < AAOSGestureConfig.MIN_SWIPE_VELOCITY) {
                return@detectDragGestures
            }

            // ✅ DETERMINAR DIRECCIÓN
            val direction = when {
                isHorizontal && horizontalDistance > 0 -> GestureDirection.RIGHT
                isHorizontal && horizontalDistance < 0 -> GestureDirection.LEFT
                isVertical && verticalDistance < 0 -> GestureDirection.UP
                isVertical && verticalDistance > 0 -> GestureDirection.DOWN
                else -> GestureDirection.NONE
            }

            // ✅ EJECUTAR CALLBACK SI HAY DIRECCIÓN
            if (direction != GestureDirection.NONE) {
                android.util.Log.d(
                    "AAOSGesture",
                    "✅ SWIPE DETECTADO: $direction (Distancia: $totalDistance, Velocidad: $velocity)"
                )

                onGesture(
                    GestureEvent(
                        direction = direction,
                        velocity = velocity,
                        distance = totalDistance
                    )
                )

                change.consume()
            }
        }
    )
}

fun showGestureToast(context: Context, direction: GestureDirection) {
    val message = when (direction) {
        GestureDirection.RIGHT -> "⏭️ Siguiente canción"
        GestureDirection.LEFT -> "⏮️ Anterior canción"
        GestureDirection.UP -> "📁 Carpeta anterior"
        GestureDirection.DOWN -> "📁 Siguiente carpeta"
        GestureDirection.NONE -> return
    }

    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
fun rememberGestureAnimationState(): Pair<Float, (GestureDirection) -> Unit> {
    var gestureDirection by remember { mutableStateOf(GestureDirection.NONE) }
    var animationTrigger by remember { mutableStateOf(0) }

    val scaleAnimation by animateFloatAsState(
        targetValue = when (gestureDirection) {
            GestureDirection.NONE -> 1f
            else -> 0.95f
        }
    )

    val triggerAnimation: (GestureDirection) -> Unit = { direction: GestureDirection ->
        gestureDirection = direction
        animationTrigger++
    }

    return Pair(scaleAnimation, triggerAnimation)
}