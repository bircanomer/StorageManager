package com.storagemanager.ui.components

import android.graphics.Color as AndroidColor
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.storagemanager.R
import com.storagemanager.ads.AdsManager

/**
 * Liste içine yerleştirilen native reklam kartı.
 *
 * Reklam yüklenmezse (Pro kullanıcı, ağ yok, envanter yok) hiçbir şey çizilmez —
 * boş bir yer tutucu bırakılmaz.
 */
@Composable
fun NativeAdCard(
    adsManager: AdsManager,
    modifier: Modifier = Modifier
) {
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    LaunchedEffect(Unit) {
        nativeAd = adsManager.loadNativeAd()
    }

    DisposableEffect(nativeAd) {
        onDispose { nativeAd?.destroy() }
    }

    val ad = nativeAd ?: return
    val adLabel = stringResource(R.string.ad_label)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                val density = context.resources.displayMetrics.density
                fun dp(value: Int) = (value * density).toInt()

                val headline = TextView(context).apply {
                    setTextColor(AndroidColor.WHITE)
                    textSize = 15f
                    maxLines = 2
                }
                val body = TextView(context).apply {
                    setTextColor(AndroidColor.parseColor("#C8C8D8"))
                    textSize = 13f
                    maxLines = 3
                }
                val cta = Button(context).apply {
                    isAllCaps = false
                    setTextColor(AndroidColor.WHITE)
                    setBackgroundColor(AndroidColor.parseColor("#6C63FF"))
                }
                val icon = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                }
                val badge = TextView(context).apply {
                    setTextColor(AndroidColor.parseColor("#0D0D1A"))
                    setBackgroundColor(AndroidColor.parseColor("#FFC107"))
                    textSize = 10f
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    text = adLabel
                }

                val textColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(dp(12), 0, 0, 0)
                    addView(badge)
                    addView(headline)
                    addView(body)
                }

                val topRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(icon)
                    addView(textColumn)
                }

                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    addView(topRow)
                    addView(
                        cta,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(10) }
                    )
                }

                NativeAdView(context).apply {
                    addView(container)
                    headlineView = headline
                    bodyView = body
                    callToActionView = cta
                    iconView = icon
                }
            },
            update = { adView ->
                (adView.headlineView as? TextView)?.text = ad.headline
                (adView.bodyView as? TextView)?.apply {
                    text = ad.body
                    visibility = if (ad.body.isNullOrBlank()) android.view.View.GONE
                    else android.view.View.VISIBLE
                }
                (adView.callToActionView as? Button)?.apply {
                    text = ad.callToAction
                    visibility = if (ad.callToAction.isNullOrBlank()) android.view.View.GONE
                    else android.view.View.VISIBLE
                }
                (adView.iconView as? ImageView)?.apply {
                    val drawable = ad.icon?.drawable
                    setImageDrawable(drawable)
                    visibility = if (drawable == null) android.view.View.GONE
                    else android.view.View.VISIBLE
                }
                adView.setNativeAd(ad)
            }
        )
    }
}
