package com.storagemanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storagemanager.domain.model.StorageInfo
import com.storagemanager.ui.theme.*

/**
 * Animated donut chart showing storage distribution.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageDonutChart(
    storageInfo: StorageInfo,
    modifier: Modifier = Modifier
) {
    // Build category data list
    val photosLabel = androidx.compose.ui.res.stringResource(com.storagemanager.R.string.category_image)
    val videosLabel = androidx.compose.ui.res.stringResource(com.storagemanager.R.string.category_video)
    val appsLabel = androidx.compose.ui.res.stringResource(com.storagemanager.R.string.category_apps)
    val cacheLabel = androidx.compose.ui.res.stringResource(com.storagemanager.R.string.category_cache)
    val audioLabel = androidx.compose.ui.res.stringResource(com.storagemanager.R.string.category_audio)
    val docsLabel = androidx.compose.ui.res.stringResource(com.storagemanager.R.string.category_document)
    val otherLabel = androidx.compose.ui.res.stringResource(com.storagemanager.R.string.category_other)

    val categories = remember(storageInfo, photosLabel) {
        listOf(
            CategoryData(photosLabel, storageInfo.photosSize, PhotoColor),
            CategoryData(videosLabel, storageInfo.videosSize, VideoColor),
            CategoryData(appsLabel, storageInfo.appsSize, AppColor),
            CategoryData(cacheLabel, storageInfo.cacheSize, CacheColor),
            CategoryData(audioLabel, storageInfo.audioSize, AudioColor),
            CategoryData(docsLabel, storageInfo.documentsSize, DocColor),
            CategoryData(otherLabel, storageInfo.otherSize, OtherColor)
        ).filter { it.size > 0 }
    }

    val totalUsed = storageInfo.usedSpace.coerceAtLeast(1L)

    // Sweep angles for each segment (proportional to size)
    val sweepAngles = remember(categories, totalUsed) {
        categories.map { (it.size.toFloat() / totalUsed) * 360f }
    }

    // Single entrance animation
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(storageInfo) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
    }

    val freePercentage = remember(storageInfo) {
        if (storageInfo.totalSpace > 0) {
            (storageInfo.freeSpace.toFloat() / storageInfo.totalSpace.toFloat()) * 100f
        } else {
            0f
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Donut chart
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 28.dp.toPx()
                val padding = strokeWidth / 2 + 8.dp.toPx()
                val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
                val topLeft = Offset(padding, padding)

                // Background ring (subtle)
                drawArc(
                    color = SurfaceVariant.copy(alpha = 0.5f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                // Glow effect — draw a slightly larger, blurred ring behind
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = if (categories.isNotEmpty()) {
                            categories.map { it.color.copy(alpha = 0.15f) } +
                                    categories.first().color.copy(alpha = 0.15f)
                        } else listOf(Color.Transparent, Color.Transparent)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animationProgress.value,
                    useCenter = false,
                    topLeft = topLeft - Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = Size(arcSize.width + 8.dp.toPx(), arcSize.height + 8.dp.toPx()),
                    style = Stroke(width = strokeWidth + 8.dp.toPx(), cap = StrokeCap.Butt)
                )

                // Glow cover
                drawArc(
                    color = Background,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft - Offset(2.dp.toPx(), 2.dp.toPx()),
                    size = Size(arcSize.width + 4.dp.toPx(), arcSize.height + 4.dp.toPx()),
                    style = Stroke(width = strokeWidth + 4.dp.toPx(), cap = StrokeCap.Butt)
                )

                // Segments
                var startAngle = -90f
                categories.forEachIndexed { index, category ->
                    val sweep = sweepAngles[index] * animationProgress.value
                    drawArc(
                        color = category.color,
                        startAngle = startAngle,
                        sweepAngle = sweep.coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += sweepAngles[index] * animationProgress.value
                }
            }

            // Center text (Boş Yer Odaklı)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        com.storagemanager.R.string.free_space_label,
                        bidiIsolate(String.format(java.util.Locale.getDefault(), "%.1f", freePercentage))
                    ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Success
                )
                Text(
                    text = formatFileSize(storageInfo.freeSpace),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Success
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        com.storagemanager.R.string.used_of_total,
                        formatFileSize(storageInfo.usedSpace),
                        formatFileSize(storageInfo.totalSpace)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Legend
        FlowRow(
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            categories.forEach { category ->
                LegendItem(
                    color = category.color,
                    label = category.name,
                    size = formatFileSize(category.size),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Boş Alan Özet Rozeti
        Surface(
            shape = CircleShape,
            color = Success.copy(alpha = 0.15f),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Success,
                    modifier = Modifier.size(8.dp),
                    content = {}
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(com.storagemanager.R.string.available_space),
                    fontSize = 13.sp,
                    color = OnBackground
                )
                Text(
                    text = "${formatFileSize(storageInfo.freeSpace)} (${bidiIsolate(String.format(java.util.Locale.getDefault(), "%.1f", freePercentage))}%)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Success
                )
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    size: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(8.dp),
            content = {}
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurface.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = size,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = OnBackground
        )
    }
}

private data class CategoryData(
    val name: String,
    val size: Long,
    val color: Color
)
