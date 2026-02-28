package com.watchwhatwhere.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.watchwhatwhere.app.R
import com.watchwhatwhere.app.data.model.AuthConfig
import com.watchwhatwhere.app.ui.components.OAuthWebViewOverlay

private const val TAG = "LoginScreen"

// Brand colors for OAuth buttons
private val GoogleRed = Color(0xFFDB4437)
private val FacebookBlue = Color(0xFF1877F2)
private val MicrosoftBlue = Color(0xFF00A4EF)
private val DiscordPurple = Color(0xFF5865F2)

// Fallback constants — only used if config hasn't loaded yet
private const val FALLBACK_GOOGLE_WEB_CLIENT_ID = "1062416956606-bqnic83qs3knun983kahmkea8m82u8n4.apps.googleusercontent.com"

/**
 * Login screen showing OAuth provider buttons.
 * All auth parameters (client IDs, URLs, scopes) are read from MobileConfig.
 * Google uses native Google Sign-In SDK (no browser).
 * Other providers use an in-app WebView that shares cookies with OkHttp.
 */
@Composable
fun LoginScreen(
    authConfig: AuthConfig = AuthConfig(),
    baseUrl: String = "https://watchwhatwhere.com",
    appVersion: String = "",
    onLoginComplete: () -> Unit,
    onCheckSession: () -> Unit = {},
    onGoogleToken: (String) -> Unit = {},
    onOAuthCode: (code: String, provider: String, codeVerifier: String?, clientId: String, redirectUri: String, tokenEndpoint: String) -> Unit = { _, _, _, _, _, _ -> },
    onOAuthRedirect: (String) -> Unit = {},
    onFacebookToken: (String) -> Unit = {},
    generatePkce: () -> Pair<String, String> = { Pair("", "") },
    onCheckForUpdates: (() -> Unit)? = null,
    prefetchStatusText: String? = null,
    onContactClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Resolve auth values from config
    val googleProvider = authConfig.providers.firstOrNull { it.id == "google" }
    val facebookProvider = authConfig.providers.firstOrNull { it.id == "facebook" }
    val microsoftProvider = authConfig.providers.firstOrNull { it.id == "microsoft" }
    val discordProvider = authConfig.providers.firstOrNull { it.id == "discord" }
    
    val googleWebClientId = googleProvider?.clientIdWeb?.ifBlank { null }
        ?: FALLBACK_GOOGLE_WEB_CLIENT_ID
    // OAuth redirect goes to web server (to exchange code + set cookie)
    val redirectUri = "${baseUrl.trimEnd('/')}${authConfig.redirectUri}"
    
    // State for WebView OAuth overlay
    var oauthUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    // PKCE state — stored when opening Microsoft WebView, used on code capture
    var currentCodeVerifier by remember { mutableStateOf<String?>(null) }
    var currentClientId by remember { mutableStateOf("") }
    var currentTokenEndpoint by remember { mutableStateOf("") }
    var currentProvider by remember { mutableStateOf("") }
    
    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    Log.d(TAG, "Google Sign-In success, got ID token")
                    isLoading = true
                    onGoogleToken(idToken)
                } else {
                    Log.e(TAG, "Google Sign-In: no ID token")
                    Toast.makeText(context, "Sign-in failed: no token received", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google Sign-In failed: ${e.statusCode}", e)
                Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "Google Sign-In cancelled: resultCode=${result.resultCode}")
        }
    }
    
    // Trigger native Google Sign-In
    fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(googleWebClientId)
            .requestEmail()
            .requestProfile()
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        // Sign out first to ensure account picker always shows
        client.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(client.signInIntent)
        }
    }
    
    // Build OAuth URLs from config
    fun buildFacebookUrl(): String {
        val appId = facebookProvider?.appId ?: "1255053839334994"
        val sdkVersion = facebookProvider?.sdkVersion ?: "v25.0"
        val scopes = facebookProvider?.scopes?.joinToString(",") ?: "email,public_profile"
        currentProvider = "facebook"
        currentClientId = appId
        currentCodeVerifier = null
        currentTokenEndpoint = ""  // Not needed for implicit flow
        return "https://www.facebook.com/$sdkVersion/dialog/oauth" +
            "?client_id=$appId" +
            "&redirect_uri=$redirectUri" +
            "&scope=$scopes" +
            "&response_type=token"
    }
    
    
    fun buildMicrosoftUrl(): String {
        val clientId = microsoftProvider?.clientIdWeb ?: "5ecbbfce-36d7-4fb4-91fd-457b6ba44fc3"
        val authority = microsoftProvider?.authority ?: "https://login.microsoftonline.com/common"
        val scopes = microsoftProvider?.scopes?.joinToString("%20") ?: "openid%20profile%20email%20User.Read"
        val statePrefix = microsoftProvider?.statePrefix ?: "microsoft:"
        // Generate PKCE for Microsoft (required for public client)
        val (verifier, challenge) = generatePkce()
        currentCodeVerifier = verifier
        currentClientId = clientId
        currentProvider = "microsoft"
        currentTokenEndpoint = "$authority/oauth2/v2.0/token"
        return "$authority/oauth2/v2.0/authorize" +
            "?client_id=$clientId" +
            "&redirect_uri=$redirectUri" +
            "&response_type=code" +
            "&scope=$scopes" +
            "&state=$statePrefix" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"
    }
    
    // Also generate PKCE for Discord (in case it needs it)
    fun buildDiscordUrl(): String {
        val clientId = discordProvider?.clientIdWeb ?: "1472675384414834850"
        val authUrl = discordProvider?.authUrl ?: "https://discord.com/oauth2/authorize"
        val scopes = discordProvider?.scopes?.joinToString("%20") ?: "identify%20email"
        val statePrefix = discordProvider?.statePrefix ?: "discord:"
        currentCodeVerifier = null  // Discord doesn't need PKCE
        currentClientId = clientId
        currentProvider = "discord"
        currentTokenEndpoint = "https://discord.com/api/oauth2/token"
        return "$authUrl" +
            "?client_id=$clientId" +
            "&redirect_uri=$redirectUri" +
            "&response_type=code" +
            "&scope=$scopes" +
            "&state=$statePrefix"
    }
    
    // OAuth WebView overlay — shown when a non-Google provider is selected
    oauthUrl?.let { url ->
        OAuthWebViewOverlay(
            authUrl = url,
            baseUrl = baseUrl,
            onAuthCodeCaptured = { code, state ->
                Log.d(TAG, "Auth code captured for provider=$currentProvider")
                oauthUrl = null
                if (code.startsWith("access_token:")) {
                    // Facebook implicit flow: token received directly, skip code exchange
                    val accessToken = code.removePrefix("access_token:")
                    Log.d(TAG, "Facebook access token received directly")
                    onFacebookToken(accessToken)
                } else if (currentProvider == "microsoft") {
                    // Microsoft: client-side token exchange with PKCE
                    onOAuthCode(code, currentProvider, currentCodeVerifier, currentClientId, redirectUri, currentTokenEndpoint)
                } else {
                    // Discord: relay redirect URL to server for server-side code exchange
                    val fullRedirectUrl = "$redirectUri?code=${java.net.URLEncoder.encode(code, "UTF-8")}&state=${java.net.URLEncoder.encode(state ?: "", "UTF-8")}"
                    Log.d(TAG, "Relaying redirect URL to server: $fullRedirectUrl")
                    onOAuthRedirect(fullRedirectUrl)
                }
            },
            onDismiss = {
                Log.d(TAG, "WebView dismissed")
                oauthUrl = null
            }
        )
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
              // Main content centered
              Spacer(modifier = Modifier.weight(1f))
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // App branding
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "WatchWhatWhere",
                    modifier = Modifier.size(96.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Google — native sign-in
                if (googleProvider?.enabled != false) {
                    OAuthButton(
                        text = "Continue with Google",
                        backgroundColor = GoogleRed,
                        enabled = !isLoading,
                        onClick = { startGoogleSignIn() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Facebook — WebView
                if (facebookProvider?.enabled != false) {
                    OAuthButton(
                        text = "Continue with Facebook",
                        backgroundColor = FacebookBlue,
                        enabled = !isLoading,
                        onClick = { oauthUrl = buildFacebookUrl() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Microsoft — WebView
                if (microsoftProvider?.enabled != false) {
                    OAuthButton(
                        text = "Continue with Microsoft",
                        backgroundColor = MicrosoftBlue,
                        enabled = !isLoading,
                        onClick = { oauthUrl = buildMicrosoftUrl() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Discord — WebView
                if (discordProvider?.enabled != false) {
                    OAuthButton(
                        text = "Continue with Discord",
                        backgroundColor = DiscordPurple,
                        enabled = !isLoading,
                        onClick = { oauthUrl = buildDiscordUrl() }
                    )
                }
                

              }
                
              Spacer(modifier = Modifier.weight(1f))
                
              // Bottom section: Check for Updates + version
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  if (onCheckForUpdates != null) {
                      var updateCheckMessage by remember { mutableStateOf("") }
                      if (updateCheckMessage.isNotEmpty()) {
                          Text(
                              text = updateCheckMessage,
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                          )
                          Spacer(modifier = Modifier.height(4.dp))
                      }
                      TextButton(
                          onClick = {
                              updateCheckMessage = "Checking..."
                              onCheckForUpdates()
                              updateCheckMessage = "✓ Config refreshed"
                          }
                      ) {
                          Text(
                              text = "Check for Updates",
                              style = MaterialTheme.typography.bodyMedium
                          )
                      }
                  }
                  if (appVersion.isNotEmpty()) {
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                          text = "Version $appVersion",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                      )
                  }
                  prefetchStatusText?.let { status ->
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                          text = status,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                      )
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(
                      horizontalArrangement = Arrangement.Center,
                      modifier = Modifier.fillMaxWidth()
                  ) {
                      TextButton(onClick = {
                          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${baseUrl}/tos")))
                      }) {
                          Text(
                              text = "Terms of Service",
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                          )
                      }
                      Text(
                          text = "·",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                          modifier = Modifier.align(Alignment.CenterVertically)
                      )
                      TextButton(onClick = {
                          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${baseUrl}/privacy")))
                      }) {
                          Text(
                              text = "Privacy Policy",
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                          )
                      }
                      
                      // Help / Contact button
                      if (onContactClick != null) {
                          Text(
                              text = "·",
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                          )
                          TextButton(onClick = onContactClick) {
                              Text(
                                  text = "Help",
                                  style = MaterialTheme.typography.bodySmall,
                                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                              )
                          }
                      }
                  }
              }
            }
            
            // Loading overlay
            if (isLoading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Signing in...")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OAuthButton(
    text: String,
    backgroundColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
