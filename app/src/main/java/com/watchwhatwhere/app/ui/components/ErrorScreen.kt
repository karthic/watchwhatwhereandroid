package com.watchwhatwhere.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.watchwhatwhere.app.R

@Composable
fun ErrorScreen(
    message: String = "Unable to load content",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "WatchWhatWhere",
            modifier = Modifier.size(96.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // App name
        Text(
            text = "WatchWhatWhere",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error message
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Retry button
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}

/**
 * Get a human-readable error message from an exception
 */
fun getErrorMessage(error: Throwable?): String {
    return when {
        error == null -> "Something went wrong"
        error.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
        error.message?.contains("UnknownHostException", ignoreCase = true) == true ||
        error.message?.contains("No address associated", ignoreCase = true) == true -> 
            "No internet connection.\nPlease check your network and try again."
        error.message?.contains("timeout", ignoreCase = true) == true ->
            "Connection timed out.\nPlease try again."
        error.message?.contains("SSL", ignoreCase = true) == true ||
        error.message?.contains("certificate", ignoreCase = true) == true ->
            "Secure connection failed.\nPlease try again later."
        else -> "Unable to load content.\nPlease try again."
    }
}
