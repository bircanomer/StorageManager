package com.storagemanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.storagemanager.ui.theme.*
import com.storagemanager.R

/**
 * A selectable file/photo list item with optional thumbnail,
 * checkbox, and delete action.
 */
@Composable
fun SelectableFileItem(
    name: String,
    size: Long,
    dateModified: Long,
    thumbnailUri: String?,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Primary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(250),
        label = "select_bg"
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val selectedLabel = androidx.compose.ui.res.stringResource(com.storagemanager.R.string.selected)
    val unselectedLabel = androidx.compose.ui.res.stringResource(com.storagemanager.R.string.not_selected)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription = if (isSelected) selectedLabel else unselectedLabel
            }
            .background(bgColor, shape = RoundedCornerShape(12.dp))
            .clickable(role = Role.Checkbox) { onToggleSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelect() },
            colors = CheckboxDefaults.colors(
                checkedColor = Primary,
                uncheckedColor = OnSurface.copy(alpha = 0.6f),
                checkmarkColor = OnPrimary
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Thumbnail or icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!thumbnailUri.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                    contentDescription = null,
                    tint = OnSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // File info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row {
                Text(
                    text = formatFileSize(size),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatRelativeTime(context, dateModified),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Delete action
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = androidx.compose.ui.res.stringResource(com.storagemanager.R.string.delete),
                tint = Error.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
