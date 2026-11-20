package com.cinestream.galleryplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cinestream.galleryplayer.ui.theme.CyanAccent
import com.cinestream.galleryplayer.ui.theme.NavyBorder

/**
 * Modifier for Android TV D-pad navigation and touch focus.
 * Adds cyan border highlight, subtle scaling, and handles D-pad Center / Enter keys.
 */
fun Modifier.tvFocusable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    focusedBorderColor: Color = CyanAccent,
    unfocusedBorderColor: Color = NavyBorder,
    borderWidth: Dp = 2.dp,
    scaleOnFocus: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused && scaleOnFocus) 1.03f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tv_focus_scale"
    )

    this
        .scale(scale)
        .border(
            BorderStroke(
                width = if (isFocused) borderWidth else 1.dp,
                color = if (isFocused) focusedBorderColor else unfocusedBorderColor
            ),
            shape = shape
        )
        .focusable(enabled = enabled, interactionSource = interactionSource)
        .onKeyEvent { keyEvent ->
            if (onClick != null && keyEvent.type == KeyEventType.KeyUp) {
                when (keyEvent.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
}
