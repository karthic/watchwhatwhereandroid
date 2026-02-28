package com.watchwhatwhere.app.ui.components

import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex

private const val TAG = "OAuthWebView"

/**
 * Full-screen WebView overlay for OAuth login.
 * 
 * The WebView shows the OAuth provider's login page. When the 
 * provider redirects back to our baseUrl with an auth code,
 * we intercept the URL and call [onAuthCodeCaptured] with the 
 * auth code and state. The WebView does NOT load the redirect URL.
 */
@Composable
fun OAuthWebViewOverlay(
    authUrl: String,
    baseUrl: String = "https://watchwhatwhere.com",
    onAuthCodeCaptured: (code: String, state: String?) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .zIndex(100f)
    ) {
        // Close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .zIndex(101f)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }
        
        var isLoading by remember { mutableStateOf(true) }
        val baseHost = remember(baseUrl) {
            android.net.Uri.parse(baseUrl).host ?: "watchwhatwhere.com"
        }
        
        AndroidView(
            factory = { context ->
                CookieManager.getInstance().setAcceptCookie(true)
                
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }
                    
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            val uri = request.url
                            Log.d(TAG, "Navigating: $url")
                            
                            // Intercept msauth:// redirect (Microsoft Android app)
                            if (uri.scheme == "msauth" && uri.getQueryParameter("code") != null) {
                                val code = uri.getQueryParameter("code")!!
                                val state = uri.getQueryParameter("state")
                                Log.d(TAG, "Auth code captured from msauth redirect! state=$state")
                                onAuthCodeCaptured(code, state)
                                return true
                            }
                            
                            // Intercept implicit flow token from fragment (#access_token=...)
                            val fragment = uri.fragment
                            if (uri.host == baseHost && fragment != null && fragment.contains("access_token=")) {
                                val params = fragment.split("&").associate {
                                    val parts = it.split("=", limit = 2)
                                    parts[0] to (parts.getOrNull(1) ?: "")
                                }
                                val accessToken = params["access_token"]
                                if (accessToken != null) {
                                    Log.d(TAG, "Access token captured from fragment!")
                                    // Pass token with prefix so handler knows it's a token, not a code
                                    onAuthCodeCaptured("access_token:$accessToken", null)
                                    return true
                                }
                            }
                            
                            // Intercept the redirect back to our site with auth code
                            if (uri.host == baseHost && uri.getQueryParameter("code") != null) {
                                val code = uri.getQueryParameter("code")!!
                                val state = uri.getQueryParameter("state")
                                Log.d(TAG, "Auth code captured! state=$state")
                                onAuthCodeCaptured(code, state)
                                return true  // Block WebView from loading this URL
                            }
                            
                            // Also intercept error redirects so we can close gracefully
                            if (uri.host == baseHost && uri.getQueryParameter("error") != null) {
                                val error = uri.getQueryParameter("error_description") ?: "Unknown error"
                                Log.e(TAG, "OAuth error: $error")
                                onDismiss()
                                return true
                            }
                            
                            return false  // Let WebView handle normally
                        }
                        
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            Log.d(TAG, "Page finished: $url")
                            // Also check for access_token in fragment on page finish
                            // (some browsers don't pass fragments to shouldOverrideUrlLoading)
                            if (url != null && url.contains("#access_token=")) {
                                val fragment = url.substringAfter("#")
                                val params = fragment.split("&").associate {
                                    val parts = it.split("=", limit = 2)
                                    parts[0] to (parts.getOrNull(1) ?: "")
                                }
                                val accessToken = params["access_token"]
                                if (accessToken != null) {
                                    Log.d(TAG, "Access token captured from page finish fragment!")
                                    onAuthCodeCaptured("access_token:$accessToken", null)
                                }
                            }
                        }
                    }
                    
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    loadUrl(authUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp)
        )
        
        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}
