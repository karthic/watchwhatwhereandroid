package com.watchwhatwhere.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.watchwhatwhere.app.R
import com.watchwhatwhere.app.data.model.UserInfo
import com.watchwhatwhere.app.di.PersistentCookieJar
import kotlinx.coroutines.delay

/**
 * Account screen showing user info and a logout button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    userInfo: UserInfo,
    onLogout: () -> Unit,
    onCheckForUpdates: (() -> Unit)? = null,
    prefetchStatusText: String? = null,
    onProviderPrefsClick: (() -> Unit)? = null,
    onContactClick: (() -> Unit)? = null
) {
    // Live countdown updated every second
    val expiryText by produceState(initialValue = "") {
        while (true) {
            val expiresAt = PersistentCookieJar.loginCookieExpiresAt
            value = if (expiresAt != null) {
                val remaining = expiresAt - System.currentTimeMillis()
                if (remaining > 0) "Session expires in ${formatDuration(remaining)}" else "Session expired"
            } else {
                "Session active (no expiry set)"
            }
            delay(1000L)
        }
    }
    
    // Get app version from BuildConfig
    val appVersion = "${com.watchwhatwhere.app.BuildConfig.VERSION_CODE} (${com.watchwhatwhere.app.BuildConfig.VERSION_NAME})"
    
    var updateCheckMessage by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(1.dp))
            
            // Center content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile picture
                if (!userInfo.picture.isNullOrBlank()) {
                    AsyncImage(
                        model = userInfo.picture,
                        contentDescription = "Profile picture",
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Profile",
                        modifier = Modifier.size(96.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // User name
                Text(
                    text = userInfo.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                // Email
                userInfo.email?.let { email ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                
                // Provider
                userInfo.provider?.let { provider ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "via ${provider.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
                
                // Session expiry
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = expiryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                
                // Prefetch status
                prefetchStatusText?.let { status ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Provider Preferences button
                if (onProviderPrefsClick != null) {
                    OutlinedButton(
                        onClick = onProviderPrefsClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = "⭐ Provider Preferences",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Logout button
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log Out",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Help / Contact button
                if (onContactClick != null) {
                    TextButton(
                        onClick = onContactClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Need Help? Contact Us",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                // Update check result message
                updateCheckMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Bottom section: Check for Updates + version
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (onCheckForUpdates != null) {
                    TextButton(
                        onClick = {
                            updateCheckMessage = "Checking..."
                            onCheckForUpdates()
                            updateCheckMessage = "✓ Config refreshed — you're up to date!"
                        }
                    ) {
                        Text(
                            text = "Check for Updates",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Version $appVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = (totalSeconds / 3600) % 24
    val totalDays = totalSeconds / 86400
    val years = totalDays / 365
    val days = totalDays % 365
    
    val parts = mutableListOf<String>()
    if (years > 0) parts.add("$years ${if (years == 1L) "year" else "years"}")
    if (days > 0) parts.add("$days ${if (days == 1L) "day" else "days"}")
    if (hours > 0) parts.add("${hours}h")
    if (minutes > 0) parts.add("${minutes}m")
    parts.add("${seconds}s")
    
    return parts.joinToString(" ")
}
