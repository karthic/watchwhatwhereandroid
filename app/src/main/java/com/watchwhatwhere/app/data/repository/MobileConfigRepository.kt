package com.watchwhatwhere.app.data.repository

import android.content.Context
import android.util.Log
import com.watchwhatwhere.app.data.api.MobileConfigApi
import com.watchwhatwhere.app.data.model.MobileConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching and caching the remote mobile config.
 * - Fetches at most once per day (24 hours)
 * - Caches in SharedPreferences (small payload, survives cache clears)
 * - Falls back to cached config on network failure
 * - Exposes a StateFlow<MobileConfig> for reactive access
 */
@Singleton
class MobileConfigRepository @Inject constructor(
    private val api: MobileConfigApi,
    private val json: Json,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MobileConfigRepo"
        private const val PREFS_NAME = "mobile_config_prefs"
        private const val KEY_CONFIG_JSON = "config_json"
        private const val KEY_LAST_FETCH = "last_fetch_timestamp"
        private const val FETCH_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow<MobileConfig?>(null)
    val config: StateFlow<MobileConfig?> = _config.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Initialize the config: load from cache first for instant startup,
     * then always fetch fresh from the network to stay current.
     * Should be called once at app startup.
     */
    suspend fun initialize() {
        _isLoading.value = true
        // Load cached config immediately so the app can start
        loadFromCache()
        // If no cached config, load the bundled default from assets
        if (_config.value == null) {
            loadBundledDefault()
        }
        // Always fetch fresh config from network
        fetchFromNetwork()
        _isLoading.value = false
    }

    /**
     * Force a refresh from the network, regardless of cache age.
     */
    suspend fun forceRefresh(): Result<MobileConfig> {
        return fetchFromNetwork()
    }

    /**
     * Fetch config from the network and cache it.
     */
    private suspend fun fetchFromNetwork(): Result<MobileConfig> {
        return try {
            val config = api.getConfig()
            // Cache the raw JSON
            val configJson = json.encodeToString(config)
            prefs.edit()
                .putString(KEY_CONFIG_JSON, configJson)
                .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                .apply()
            _config.value = config
            Log.d(TAG, "Config fetched from network: v${config.app.version}, minVersion=${config.app.minVersion}")
            Result.success(config)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch config from network", e)
            // If we have no cached config at all, load from bundled asset
            if (_config.value == null) {
                loadBundledDefault()
            }
            Result.failure(e)
        }
    }

    /**
     * Load config from SharedPreferences cache.
     */
    private fun loadFromCache() {
        val cachedJson = prefs.getString(KEY_CONFIG_JSON, null) ?: return
        try {
            val config = json.decodeFromString<MobileConfig>(cachedJson)
            _config.value = config
            Log.d(TAG, "Config loaded from cache: v${config.app.version}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cached config", e)
        }
    }

    /**
     * Load the bundled mobileconfig.json from assets as the default config.
     * This ensures the app always has a complete config even on first launch
     * with no network and no cache.
     */
    private fun loadBundledDefault() {
        try {
            val assetJson = context.assets.open("mobileconfig.json").bufferedReader().use { it.readText() }
            val config = json.decodeFromString<MobileConfig>(assetJson)
            _config.value = config
            Log.d(TAG, "Config loaded from bundled asset: v${config.app.version}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bundled config, using Kotlin defaults", e)
            _config.value = MobileConfig()
        }
    }

    /**
     * Compare two semver-style version strings (e.g. "1.0.0" vs "1.2.0").
     * Returns true if [current] is less than [minimum].
     */
    fun isUpdateRequired(current: String, minimum: String): Boolean {
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val minimumParts = minimum.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(currentParts.size, minimumParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val m = minimumParts.getOrElse(i) { 0 }
            if (c < m) return true
            if (c > m) return false
        }
        return false // equal
    }

    /**
     * Get the store URL to open for updates.
     * Falls back to izonewe.com if no store URL is configured.
     */
    fun getUpdateUrl(): String {
        val config = _config.value ?: return "https://izonewe.com"
        return config.app.playStoreUrl.ifBlank {
            config.app.appStoreUrl.ifBlank {
                "https://izonewe.com"
            }
        }
    }
}
