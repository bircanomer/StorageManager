package com.storagemanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.storagemanager.ui.theme.OnBackground

/**
 * Smoothly animates from 0 to [targetValue].
 * When [formatAsSize] is true the displayed text is a human-readable file size.
 */
@Composable
fun AnimatedCounter(
    targetValue: Long,
    modifier: Modifier = Modifier,
    formatAsSize: Boolean = true,
    style: TextStyle = MaterialTheme.typography.headlineMedium
) {
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(targetValue) {
        animatable.snapTo(0f)
        animatable.animateTo(
            targetValue = targetValue.toFloat(),
            animationSpec = tween(
                durationMillis = 1500,
                easing = FastOutSlowInEasing
            )
        )
    }

    val displayValue = animatable.value.toLong()

    val text = if (formatAsSize) {
        formatFileSize(displayValue)
    } else {
        displayValue.toString()
    }

    Text(
        text = text,
        style = style,
        color = OnBackground,
        modifier = modifier
    )
}
