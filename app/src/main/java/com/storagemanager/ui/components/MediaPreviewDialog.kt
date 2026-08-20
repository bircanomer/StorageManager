package com.storagemanager.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.res.stringResource
import com.storagemanager.R

data class MediaItemPreview(
    val uriOrPath: String,
    val title: String,
    val subtitle: String,
    val isVideo: Boolean = false,
    val isSelected: Boolean = false,
    val onToggleSelect: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null
)

fun launchExternalVideoPlayer(context: android.content.Context, pathOrUri: String) {
    try {
        val file = java.io.File(pathOrUri)
        val uri: Uri = if (file.exists()) {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } else {
            Uri.parse(pathOrUri)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Oynatıcıyı bir kapsayıcı içine alır: AndroidView'in ölçüsü artık videonun kendi
 * boyutuna değil, Compose'un verdiği kısıtlara bağlıdır. Yerleşim ayrıca elle isteniyor;
 * Compose taşıyıcıyı yeniden kullandığında (aynı sınırlarla) alt görünüme hiç yerleşim
 * uygulanmıyor ve SurfaceView "has no frame" durumunda takılı kalıyordu.
 */
private fun createVideoContainer(ctx: android.content.Context): android.widget.FrameLayout {
    val container = android.widget.FrameLayout(ctx)
    container.setBackgroundColor(android.graphics.Color.BLACK)
    val videoView = android.widget.VideoView(ctx).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.Gravity.CENTER
        )
        // Oynatma yokken gizli: yüzey bırakıldığında önceki videonun donmuş son karesi
        // ekranda kalmaz.
        visibility = android.view.View.INVISIBLE
        setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.start()
        }
        setOnErrorListener { _, _, _ ->
            (container.tag as? String)?.let { launchExternalVideoPlayer(context, it) }
            true
        }
    }
    container.addView(videoView)

    val mediaController = android.widget.MediaController(ctx)
    mediaController.setAnchorView(container)
    videoView.setMediaController(mediaController)

    container.post { container.requestLayout() }
    return container
}

@Composable
fun MediaPreviewDialog(
    item: MediaItemPreview,
    onDismiss: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isPlayingVideo by remember(item.uriOrPath) { mutableStateOf(false) }

    var scale by remember(item.uriOrPath) { mutableStateOf(1f) }
    var offset by remember(item.uriOrPath) { mutableStateOf(Offset.Zero) }
    var totalDragX by remember(item.uriOrPath) { mutableStateOf(0f) }
    var swipeTriggered by remember(item.uriOrPath) { mutableStateOf(false) }

    // Diyalog penceresine sistem çubuğu inset'leri güvenilir şekilde ulaşmıyor
    // (Android 15+ kenardan kenara pencerelerde içeride safeDrawing 0 dönüyor).
    // Bu yüzden değerleri Activity'nin kök penceresinden okuyup elle uyguluyoruz.
    val rootView = LocalView.current
    val density = LocalDensity.current
    val systemBarInsets = remember(rootView) {
        val insets = ViewCompat.getRootWindowInsets(rootView)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        with(density) {
            (insets?.top ?: 0).toDp() to (insets?.bottom ?: 0).toDp()
        }
    }
    val (topInset, bottomInset) = systemBarInsets

    Dialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows = false olmadan diyalog penceresi inset'leri kendi tüketiyor;
        // bu durumda içerideki statusBarsPadding/navigationBarsPadding sıfır dönüyor ve
        // alttaki "Sil" butonu sistem gezinme çubuğunun altında kalıyordu.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Üst bar / medya / alt bar üst üste bindirilmiyor, dikey olarak diziliyor.
        // Bindirmeli düzende fotoğraf tüm pencereye göre ortalanıyordu; alt bar üst bardan
        // çok daha yüksek olduğu için fotoğraf görsel olarak alta yapışık duruyordu.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // ── Üst Bar (Kapat ve İlerleme) ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.15f), shape = CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isVideo) {
                        IconButton(
                            onClick = { launchExternalVideoPlayer(context, item.uriOrPath) },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), shape = CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.preview_external_player), tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    // Seçim Butonu (varsa)
                    if (item.onToggleSelect != null) {
                        Button(
                            onClick = item.onToggleSelect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.isSelected) Color(0xFF6C63FF) else Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (item.isSelected) stringResource(R.string.selected) else stringResource(R.string.preview_select),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── Medya İçeriği (Görsel veya Video Önizleme) ───────────────────
            // weight(1f): barlardan artan alanın tamamını kaplar, fotoğraf bu alana ortalanır
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Video oynatıcı, video önizlemesi açık olduğu sürece ağaçta kalır; yalnızca
                // kaynağı değişir. Her videoda yeni bir VideoView oluşturulursa Compose,
                // AndroidView taşıyıcısını yeniden kullandığı için yeni görünüme hiç yerleşim
                // (layout) uygulamıyor; yüzey oluşmadığından oynatma sessizce hiç başlamıyordu.
                if (item.isVideo) {
                    AndroidView(
                        factory = { ctx -> createVideoContainer(ctx) },
                        update = { container ->
                            val videoView = container.getChildAt(0) as android.widget.VideoView
                            val path = item.uriOrPath
                            val shouldPlay = isPlayingVideo

                            // Ölçüm/yerleşim geçişi sırasında VideoView'a dokunmak, onun
                            // requestLayout() çağrısının yutulmasına yol açıyor. Bu yüzden
                            // kaynak değişimi geçiş bittikten sonra uygulanır.
                            container.post {
                                // Diyalog bu arada kapanmış olabilir
                                if (!container.isAttachedToWindow) return@post

                                if (shouldPlay) {
                                    videoView.visibility = android.view.View.VISIBLE
                                    if (container.tag != path) {
                                        container.tag = path
                                        val file = java.io.File(path)
                                        if (file.exists()) {
                                            videoView.setVideoPath(file.absolutePath)
                                        } else {
                                            videoView.setVideoURI(Uri.parse(path))
                                        }
                                    }
                                } else if (container.tag != null ||
                                    videoView.visibility == android.view.View.VISIBLE
                                ) {
                                    // Videodan çıkıldı: oynatıcıyı bırak ve yüzeyi gizle ki
                                    // önceki videonun son karesi bir sonrakinde görünmesin.
                                    container.tag = null
                                    videoView.stopPlayback()
                                    videoView.visibility = android.view.View.INVISIBLE
                                }
                            }
                        },
                        onRelease = { container ->
                            container.tag = null
                            // Oynatıcı kaynaklarını serbest bırak
                            (container.getChildAt(0) as android.widget.VideoView).stopPlayback()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (!(item.isVideo && isPlayingVideo)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(item.uriOrPath) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        offset = offset + pan
                                    } else {
                                        offset = Offset.Zero
                                        if (!swipeTriggered) {
                                            totalDragX += pan.x
                                            if (totalDragX < -150f) {
                                                swipeTriggered = true
                                                onNext?.invoke()
                                            } else if (totalDragX > 150f) {
                                                swipeTriggered = true
                                                onPrevious?.invoke()
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = item.uriOrPath,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    }

                    // Eğer video ise ortada Oynat butonu göster
                    if (item.isVideo) {
                        Surface(
                            onClick = { isPlayingVideo = true },
                            shape = CircleShape,
                            color = Color(0xFF6C63FF).copy(alpha = 0.9f),
                            shadowElevation = 8.dp,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.preview_play),
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                    }
                }

                // Sol Ok (Önceki)
                if (onPrevious != null) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.preview_previous), tint = Color.White)
                    }
                }

                // Sağ Ok (Sonraki)
                if (onNext != null) {
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.preview_next), tint = Color.White)
                    }
                }
            }

            // ── Alt Bar (Dosya Detayları & Sil Aksiyonu) ────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    // Alt inset gezinme çubuğunu ve ekran çentiğini kapsar
                    // (3 tuşlu gezinme ve jest çubuğu için ayrı ayrı doğru çalışır)
                    .padding(bottom = bottomInset)
                    .padding(16.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.subtitle,
                    color = Color(0xFFC8C8D8).copy(alpha = 0.8f),
                    fontSize = 13.sp
                )

                if (item.onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            item.onDelete.invoke()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B6B),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.preview_delete_this), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
