package com.watchwhatwhere.app.data.model

import kotlinx.serialization.Serializable

/**
 * Root mobile config from watchwhatwhere.com/mobileconfig.json
 * Only models the sections the app currently uses.
 */
@Serializable
data class MobileConfig(
    val app: AppInfo = AppInfo(),
    val auth: AuthConfig = AuthConfig(),
    val api: ApiConfig = ApiConfig(),
    val content: ContentConfig = ContentConfig(),
    val navigation: NavigationConfig = NavigationConfig()
)

@Serializable
data class AuthConfig(
    val redirectUri: String = "/",
    val redirectUriAndroid: String = "myapp://auth",
    val sessionCookieName: String = "loginname",
    val loginEndpoint: AuthLoginEndpoint = AuthLoginEndpoint(),
    val providers: List<AuthProvider> = emptyList()
)

@Serializable
data class AuthLoginEndpoint(
    val url: String = "/inputs/getdata.php"
)

@Serializable
data class AuthProvider(
    val id: String,
    val enabled: Boolean = true,
    val clientIdWeb: String = "",
    val clientIdAndroid: String = "",
    val appId: String = "",
    val authUrl: String = "",
    val authority: String = "",
    val scopes: List<String> = emptyList(),
    val responseType: String = "code",
    val statePrefix: String = "",
    val sdkVersion: String = "",
    val flowType: String = "redirect"
)

@Serializable
data class AppInfo(
    val name: String = "WatchWhatWhere",
    val version: String = "1.0.0",
    val minVersion: String = "1.0.0",
    val baseUrl: String = "https://watchwhatwhere.com",
    val updateUrl: String = "https://watchwhatwhere.com/mobileconfig.json",
    val appStoreUrl: String = "",
    val playStoreUrl: String = "",
    val contactEmail: String = "",
    val privacyPolicyUrl: String = "https://watchwhatwhere.com/privacy",
    val termsOfServiceUrl: String = "https://watchwhatwhere.com/tos"
)

@Serializable
data class ApiConfig(
    val postEndpoint: String = "/inputs/getdata.php",
    val paginationSize: Int = 100,
    val endpoints: Map<String, EndpointConfig> = emptyMap()
)

@Serializable
data class EndpointConfig(
    val url: String? = null,
    val urlPattern: String? = null,
    val method: String = "GET"
)

@Serializable
data class ContentConfig(
    val types: List<ContentType> = emptyList(),
    val genres: List<String> = emptyList(),
    val imageBaseUrl: String = "https://image.tmdb.org/t/p/",
    val imageSizes: ImageSizes = ImageSizes(),
    val watchProviderTypes: List<String> = emptyList(),
    val videoTypeOrder: List<String> = listOf("Teaser", "Trailer", "Clip", "Featurette", "Recap", "Opening Credits", "Behind the Scenes", "Bloopers"),
    val providers: List<ProviderInfo> = emptyList()
)

@Serializable
data class ContentType(
    val key: String,
    val label: String,
    val group: String = ""
)

@Serializable
data class ImageSizes(
    val posterSmall: String = "w185",
    val posterMedium: String = "w342",
    val posterLarge: String = "w500",
    val posterOriginal: String = "original",
    val backdropSmall: String = "w300",
    val backdropMedium: String = "w780",
    val backdropLarge: String = "w1280",
    val backdropOriginal: String = "original",
    val profileSmall: String = "w185",
    val profileMedium: String = "w342",
    val profileLarge: String = "h632",
    val profileOriginal: String = "original"
)

@Serializable
data class ProviderInfo(
    val source: String,
    val label: String,
    val viewcost: String = "",
    val logo: String = ""
)

@Serializable
data class NavigationConfig(
    val mainNav: List<NavItem> = emptyList(),
    val profileSections: List<String> = emptyList()
)

@Serializable
data class NavItem(
    val label: String,
    val page: String,
    val type: String? = null
)
