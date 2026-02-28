package com.watchwhatwhere.app.data.repository

import android.util.Log
import android.webkit.CookieManager
import com.watchwhatwhere.app.data.api.WatchWhatWhereApi
import com.watchwhatwhere.app.data.model.AuthState
import com.watchwhatwhere.app.data.model.LoginCheckResponse
import com.watchwhatwhere.app.data.model.UserInfo
import com.watchwhatwhere.app.data.model.ProviderPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages authentication state by checking the session cookie with the server.
 * The loginname cookie is set by the server during OAuth and persists forever.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: WatchWhatWhereApi,
    private val okHttpClient: OkHttpClient,
    private val configRepository: com.watchwhatwhere.app.data.repository.MobileConfigRepository
) {
    companion object {
        private const val TAG = "AuthRepository"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * Check the current session by hitting the login endpoint.
     * If a valid session cookie exists, the server returns myinfo.
     * If no session, the server returns an empty body.
     */
    suspend fun checkSession() {
        try {
            // Debug: log cookies for the base URL
            val baseUrl = configRepository.config.value?.app?.baseUrl ?: "https://watchwhatwhere.com"
            val cookies = android.webkit.CookieManager.getInstance()
                .getCookie(baseUrl) ?: "none"
            Log.d(TAG, "checkSession — cookies: $cookies")
            
            val responseBody = api.checkSession()
            val bodyString = responseBody.string()
            
            if (bodyString.isBlank()) {
                _authState.value = AuthState.Unauthenticated
                Log.d(TAG, "No session (empty response)")
                return
            }
            
            val response = json.decodeFromString<LoginCheckResponse>(bodyString)
            val myInfo = response.myinfo
            val userName = myInfo?.name ?: myInfo?.nama ?: myInfo?.loginname
            if (myInfo != null && !userName.isNullOrBlank()) {
                _authState.value = AuthState.Authenticated(
                    UserInfo(
                        name = userName,
                        email = myInfo.email,
                        picture = myInfo.picture,
                        provider = myInfo.provider,
                        providerPrefs = parseProviderPrefs(myInfo.providerprefs)
                    )
                )
                Log.d(TAG, "Session valid: $userName")
            } else {
                _authState.value = AuthState.Unauthenticated
                Log.d(TAG, "No valid session")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Session check failed", e)
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Login with a provider by sending token/credentials to the server.
     * The server validates the token, creates a session, and returns user info.
     */
    suspend fun loginWithProvider(fields: Map<String, String>): Result<Unit> {
        return try {
            val responseBody = api.loginWithProvider(fields = fields)
            val bodyString = responseBody.string()
            Log.d(TAG, "loginWithProvider response: $bodyString")
            
            if (bodyString.isBlank()) {
                Log.e(TAG, "Login returned empty response")
                return Result.failure(Exception("Login failed - empty response"))
            }
            
            val response = json.decodeFromString<LoginCheckResponse>(bodyString)
            val myInfo = response.myinfo
            val userName = myInfo?.name ?: myInfo?.nama ?: myInfo?.loginname
            if (myInfo != null && !userName.isNullOrBlank()) {
                _authState.value = AuthState.Authenticated(
                    UserInfo(
                        name = userName,
                        email = myInfo.email,
                        picture = myInfo.picture,
                        provider = myInfo.provider,
                        providerPrefs = parseProviderPrefs(myInfo.providerprefs)
                    )
                )
                Log.d(TAG, "Login success: $userName")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Login response missing user info")
                Result.failure(Exception("Login failed - no user info returned"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "loginWithProvider failed", e)
            Result.failure(e)
        }
    }

    /**
     * PKCE: generates code_verifier and code_challenge.
     * Call this before building the auth URL and store the verifier 
     * for later use during token exchange.
     */
    fun generatePkce(): Pair<String, String> {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        val codeVerifier = android.util.Base64.encodeToString(
            bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray())
        val codeChallenge = android.util.Base64.encodeToString(
            digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
        Log.d(TAG, "PKCE generated: verifier=${codeVerifier.take(8)}..., challenge=${codeChallenge.take(8)}...")
        return Pair(codeVerifier, codeChallenge)
    }

    /**
     * Handle OAuth auth code from the WebView.
     * For Microsoft: exchanges the code with Microsoft's token endpoint using PKCE,
     * then sends the access token to our server via loginWithProvider.
     * For other providers: relays the full redirect URL to our server via OkHttp.
     */
    suspend fun handleOAuthCode(
        code: String,
        provider: String,
        codeVerifier: String?,
        clientId: String,
        redirectUri: String,
        tokenEndpoint: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Exchange the auth code for an access token with the provider
                Log.d(TAG, "Exchanging auth code with token endpoint: $tokenEndpoint")
                
                val formBody = okhttp3.FormBody.Builder()
                    .add("client_id", clientId)
                    .add("code", code)
                    .add("redirect_uri", redirectUri)
                    .add("grant_type", "authorization_code")
                
                if (codeVerifier != null) {
                    formBody.add("code_verifier", codeVerifier)
                }
                
                val request = Request.Builder()
                    .url(tokenEndpoint)
                    .header("Origin", redirectUri.trimEnd('/'))
                    .post(formBody.build())
                    .build()
                    
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Token exchange response (${response.code}): ${body.take(500)}")
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Token exchange failed: ${response.code} - $body"))
                }
                
                // Parse tokens from the response
                val tokenJson = json.parseToJsonElement(body)
                val accessToken = tokenJson.jsonObject["access_token"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("No access_token in response"))
                val idToken = tokenJson.jsonObject["id_token"]?.jsonPrimitive?.contentOrNull
                
                Log.d(TAG, "Got access token for provider=$provider, fetching user info")
                
                // Fetch user profile from the provider's API using the access token
                val userInfoFields = mutableMapOf(
                    "provider" to provider
                )
                
                if (provider == "microsoft") {
                    // Server expects ms_token, ms_id, ms_name, ms_email fields
                    userInfoFields["ms_token"] = accessToken
                    
                    // Get user info from Microsoft Graph API
                    val graphRequest = Request.Builder()
                        .url("https://graph.microsoft.com/v1.0/me")
                        .header("Authorization", "Bearer $accessToken")
                        .get()
                        .build()
                    val graphResponse = okHttpClient.newCall(graphRequest).execute()
                    val graphBody = graphResponse.body?.string() ?: ""
                    Log.d(TAG, "Graph API response (${graphResponse.code}): $graphBody")
                    
                    if (graphResponse.isSuccessful && graphBody.isNotBlank()) {
                        val graphJson = json.parseToJsonElement(graphBody)
                        val msId = graphJson.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: ""
                        val email = graphJson.jsonObject["mail"]?.jsonPrimitive?.contentOrNull
                            ?: graphJson.jsonObject["userPrincipalName"]?.jsonPrimitive?.contentOrNull ?: ""
                        val name = graphJson.jsonObject["displayName"]?.jsonPrimitive?.contentOrNull ?: ""
                        userInfoFields["ms_id"] = msId
                        userInfoFields["ms_email"] = email
                        userInfoFields["ms_name"] = name
                        Log.d(TAG, "Microsoft user: $name ($email) id=$msId")
                    }
                } else if (provider == "facebook") {
                    // Server expects fb_token, fb_id, fb_name, fb_email, fb_picture fields
                    userInfoFields["fb_token"] = accessToken
                    
                    // Get user info from Facebook Graph API
                    val fbGraphRequest = Request.Builder()
                        .url("https://graph.facebook.com/me?fields=id,name,email,picture.width(200)&access_token=$accessToken")
                        .get()
                        .build()
                    val fbGraphResponse = okHttpClient.newCall(fbGraphRequest).execute()
                    val fbGraphBody = fbGraphResponse.body?.string() ?: ""
                    Log.d(TAG, "Facebook Graph API response (${fbGraphResponse.code}): $fbGraphBody")
                    
                    if (fbGraphResponse.isSuccessful && fbGraphBody.isNotBlank()) {
                        val fbJson = json.parseToJsonElement(fbGraphBody)
                        val fbId = fbJson.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: ""
                        val email = fbJson.jsonObject["email"]?.jsonPrimitive?.contentOrNull ?: ""
                        val name = fbJson.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: ""
                        val picture = fbJson.jsonObject["picture"]
                            ?.jsonObject?.get("data")
                            ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""
                        userInfoFields["fb_id"] = fbId
                        userInfoFields["fb_email"] = email
                        userInfoFields["fb_name"] = name
                        userInfoFields["fb_picture"] = picture
                        Log.d(TAG, "Facebook user: $name ($email) id=$fbId")
                    }
                }
                
                Log.d(TAG, "Sending user info to server: provider=$provider, fields=${userInfoFields.keys}")
                loginWithProvider(userInfoFields)
            } catch (e: Exception) {
                Log.e(TAG, "OAuth code exchange failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Handle OAuth redirect for providers that don't need client-side token exchange.
     * Relays the full redirect URL to our server via OkHttp so the server
     * can process the auth code server-side.
     */
    suspend fun handleOAuthRedirect(redirectUrl: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Relaying OAuth redirect via OkHttp: $redirectUrl")
                val request = Request.Builder().url(redirectUrl).get().build()
                val response = okHttpClient.newCall(request).execute()
                Log.d(TAG, "OAuth redirect response: ${response.code}")
                response.close()
                
                // Now check if we got a session
                checkSession()
                
                if (_authState.value is AuthState.Authenticated) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("No session after OAuth redirect"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "OAuth redirect relay failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Handle Facebook access token from implicit flow.
     * Fetches user info from Graph API, then sends to our server.
     */
    suspend fun handleFacebookToken(accessToken: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Facebook implicit flow: fetching user info from Graph API")
                
                val fbGraphRequest = Request.Builder()
                    .url("https://graph.facebook.com/me?fields=id,name,email,picture.width(200)&access_token=$accessToken")
                    .get()
                    .build()
                val fbGraphResponse = okHttpClient.newCall(fbGraphRequest).execute()
                val fbGraphBody = fbGraphResponse.body?.string() ?: ""
                Log.d(TAG, "Facebook Graph API response (${fbGraphResponse.code}): $fbGraphBody")
                
                if (!fbGraphResponse.isSuccessful || fbGraphBody.isBlank()) {
                    return@withContext Result.failure(Exception("Facebook Graph API failed: ${fbGraphResponse.code}"))
                }
                
                val fbJson = json.parseToJsonElement(fbGraphBody)
                val fbId = fbJson.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: ""
                val email = fbJson.jsonObject["email"]?.jsonPrimitive?.contentOrNull ?: ""
                val name = fbJson.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val picture = fbJson.jsonObject["picture"]
                    ?.jsonObject?.get("data")
                    ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""
                Log.d(TAG, "Facebook user: $name ($email) id=$fbId")
                
                val fields = mutableMapOf(
                    "provider" to "facebook",
                    "fb_token" to accessToken,
                    "fb_id" to fbId,
                    "fb_name" to name,
                    "fb_email" to email,
                    "fb_picture" to picture
                )
                
                loginWithProvider(fields)
            } catch (e: Exception) {
                Log.e(TAG, "Facebook token handling failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Logout: clear cookies and reset state.
     */
    fun logout() {
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cookies", e)
        }
        _authState.value = AuthState.Unauthenticated
        Log.d(TAG, "Logged out")
    }
    
    /**
     * Parse providerPrefs from raw JSON.
     * PHP returns [] (array) when empty, {} (object) when populated.
     */
    private fun parseProviderPrefs(element: JsonElement?): List<ProviderPref> {
        if (element == null || element !is JsonObject) return emptyList()
        return element.entries.map { (source, status) ->
            ProviderPref(source, status.jsonPrimitive.content)
        }
    }
}
