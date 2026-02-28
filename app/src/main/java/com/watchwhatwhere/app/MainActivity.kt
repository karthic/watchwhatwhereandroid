package com.watchwhatwhere.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.watchwhatwhere.app.ui.navigation.AppNavigation
import com.watchwhatwhere.app.ui.theme.WatchWhatWhereTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // Callback for auth deep link — set by AppNavigation
    var onAuthCallback: (() -> Unit)? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install native splash screen before super.onCreate
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            WatchWhatWhereTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
        
        // Handle deep link if app was launched from it
        handleAuthDeepLink(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthDeepLink(intent)
    }
    
    private fun handleAuthDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "myapp" && uri.host == "auth") {
            Log.d(TAG, "Auth deep link received: $uri")
            onAuthCallback?.invoke()
        }
    }
}
