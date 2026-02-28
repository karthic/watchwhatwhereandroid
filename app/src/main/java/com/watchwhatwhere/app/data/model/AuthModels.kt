package com.watchwhatwhere.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Auth state exposed to the UI.
 */
sealed class AuthState {
    object Unknown : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: UserInfo) : AuthState()
}

/**
 * Minimal user info extracted from the login session check.
 */
data class UserInfo(
    val name: String,
    val email: String?,
    val picture: String?,
    val provider: String? = null,
    val providerPrefs: List<ProviderPref> = emptyList()
)

/**
 * Raw response from POST /inputs/getdata.php with func=login, do=get
 * The server returns { "myinfo": { ... } } when logged in,
 * or an empty/error response when not.
 */
@Serializable
data class LoginCheckResponse(
    val myinfo: MyInfo? = null
)

@Serializable
data class MyInfo(
    val id: String? = null,
    val loginname: String? = null,
    val name: String? = null,
    val nama: String? = null,
    val email: String? = null,
    val picture: String? = null,
    val provider: String? = null,
    val level: String? = null,
    val defaultshare: String? = null,
    @SerialName("providerPrefs")
    val providerprefs: JsonElement? = null
)

@Serializable
data class ProviderPref(
    val source: String,
    val status: String // "prio" or "hide"
)

@Serializable
data class Provider(
    val source: String,
    val viewcost: String,
    val logo: String
)

@Serializable
data class ProviderPrefsResponse(
    val success: Boolean? = null,
    val prefs: Map<String, String>? = null
)

/**
 * Response from provider login POST.
 */
@Serializable
data class ProviderLoginResponse(
    val myinfo: MyInfo? = null,
    val error: String? = null
)
