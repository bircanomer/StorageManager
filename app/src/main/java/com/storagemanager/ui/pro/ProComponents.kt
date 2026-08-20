package com.storagemanager.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storagemanager.R

private val DarkBackground = Color(0xFF0D0D1A)
private val DarkSurface = Color(0xFF1A1A2E)
private val PrimaryColor = Color(0xFF6C63FF)
private val SecondaryColor = Color(0xFF00BCD4)
private val OnSurfaceColor = Color(0xFFC8C8D8)
private val GoldColor = Color(0xFFFFC107)

/**
 * Kilitli bir ekranın tamamını kaplayan bilgi katmanı.
 * Metinler uzun dillerde de sığsın diye sabit yükseklik kullanılmaz.
 */
@Composable
fun ProLockedScreen(
    featureText: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(PrimaryColor, SecondaryColor)),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.pro_locked_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                featureText,
                color = OnSurfaceColor.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onUnlock,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.pro_unlock_button),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Ücretsiz sürümün toplu işlem sınırına takıldığında gösterilen diyalog.
 *
 * @param limit ücretsiz sürümde tek seferde işlenebilecek öge sayısı
 */
@Composable
fun ProLimitDialog(
    limit: Int,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        icon = {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = GoldColor
            )
        },
        title = {
            Text(
                stringResource(R.string.pro_limit_title),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                stringResource(R.string.pro_limit_message, limit),
                color = OnSurfaceColor
            )
        },
        confirmButton = {
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
            ) {
                Text(stringResource(R.string.pro_unlock_button), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = OnSurfaceColor)
            }
        }
    )
}

/** Üst çubukta gösterilen küçük Pro rozeti / satın alma girişi. */
@Composable
fun ProBadgeButton(
    isPro: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (isPro) GoldColor.copy(alpha = 0.18f) else PrimaryColor.copy(alpha = 0.18f)
    val tint = if (isPro) GoldColor else PrimaryColor

    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.WorkspacePremium,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(if (isPro) R.string.pro_badge else R.string.pro_upgrade_short),
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
