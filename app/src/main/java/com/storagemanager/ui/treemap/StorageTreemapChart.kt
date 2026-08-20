package com.storagemanager.ui.treemap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storagemanager.domain.model.StorageTreeNode
import com.storagemanager.ui.components.formatFileSize
import com.storagemanager.ui.theme.*
import androidx.compose.ui.res.stringResource

private val TreemapColors = listOf(
    Primary,
    Secondary,
    PhotoColor,
    AppColor,
    CacheColor,
    VideoColor,
    Success,
    PrimaryVariant
)

@Composable
fun StorageTreemapChart(
    node: StorageTreeNode,
    onNodeClick: (StorageTreeNode) -> Unit,
    modifier: Modifier = Modifier,
    maxChildren: Int = 10
) {
    val children = node.children.take(maxChildren)
    if (children.isEmpty()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceVariant)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${node.name}\n${formatFileSize(node.size)}",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val topHalf = children.take(3)
        val bottomHalf = children.drop(3).take(3)

        if (topHalf.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topHalf.forEachIndexed { index, child ->
                    val weight = child.percentageOfParent.coerceAtLeast(0.15f)
                    TreemapBox(
                        childNode = child,
                        color = TreemapColors[index % TreemapColors.size],
                        onNodeClick = onNodeClick,
                        modifier = Modifier.weight(weight)
                    )
                }
            }
        }

        if (bottomHalf.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bottomHalf.forEachIndexed { index, child ->
                    val weight = child.percentageOfParent.coerceAtLeast(0.15f)
                    TreemapBox(
                        childNode = child,
                        color = TreemapColors[(index + 3) % TreemapColors.size],
                        onNodeClick = onNodeClick,
                        modifier = Modifier.weight(weight)
                    )
                }
            }
        }
    }
}

@Composable
private fun TreemapBox(
    childNode: StorageTreeNode,
    color: Color,
    onNodeClick: (StorageTreeNode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.85f))
            .clickable { onNodeClick(childNode) }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = childNode.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Column {
                Text(
                    text = formatFileSize(childNode.size),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    text = stringResource(com.storagemanager.R.string.percent_format, (childNode.percentageOfParent * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
