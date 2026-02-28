package com.watchwhatwhere.app.ui.components

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex

/**
 * Full-screen video player overlay (not a Dialog) that stays within the same window.
 * This allows the activity's immersive mode to remain active.
 */
@Composable
fun YouTubePlayerOverlay(
    videoKey: String,
    videoTitle: String?,
    baseUrl: String = "https://watchwhatwhere.com",
    onDismiss: () -> Unit
) {
    var isFullscreen by remember { mutableStateOf(false) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenContainer by remember { mutableStateOf<FrameLayout?>(null) }
    
    // Handle back press
    BackHandler {
        if (isFullscreen) {
            fullscreenContainer?.let { container ->
                fullscreenView?.let { view ->
                    container.removeView(view)
                    fullscreenView = null
                    isFullscreen = false
                }
            }
        } else {
            onDismiss()
        }
    }
    
    // Full screen overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .zIndex(100f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isFullscreen) {
                    onDismiss()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Video container - stop click propagation
        Box(
            modifier = Modifier
                .then(
                    if (isFullscreen) Modifier.fillMaxSize()
                    else Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(16f / 9f)
                )
                .clip(if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp))
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Stop propagation */ }
        ) {
            // WatchWhatWhere video player WebView
            AndroidView(
                factory = { context ->
                    // Container for fullscreen view
                    val container = FrameLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    fullscreenContainer = container
                    
                    val webView = WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        settings.apply {
                            javaScriptEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            domStorageEnabled = true
                        }
                        
                        webViewClient = WebViewClient()
                        
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                fullscreenView = view
                                isFullscreen = true
                                view?.let { container.addView(it) }
                            }
                            
                            override fun onHideCustomView() {
                                fullscreenView?.let { container.removeView(it) }
                                fullscreenView = null
                                isFullscreen = false
                            }
                        }
                        
                        // Load WatchWhatWhere video player
                        loadUrl("${baseUrl.trimEnd('/')}/watch.php?id=$videoKey")
                    }
                    
                    container.addView(webView)
                    container
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Close button (hide when fullscreen)
            if (!isFullscreen) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

// Keep the old name as an alias for compatibility
@Composable
fun YouTubePlayerDialog(
    videoKey: String,
    videoTitle: String?,
    baseUrl: String = "https://watchwhatwhere.com",
    onDismiss: () -> Unit
) {
    YouTubePlayerOverlay(videoKey, videoTitle, baseUrl, onDismiss)
}
