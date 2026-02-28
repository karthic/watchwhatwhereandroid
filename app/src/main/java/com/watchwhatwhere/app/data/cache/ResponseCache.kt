package com.watchwhatwhere.app.data.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple file-based cache for API responses.
 * Since the API uses POST requests, HTTP caching doesn't work.
 * This cache stores JSON responses on disk with a 1-week expiration.
 */
@Singleton
class ResponseCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val cacheDir = File(context.cacheDir, "api_cache")
    private val cacheMaxAgeMs = 7 * 24 * 60 * 60 * 1000L // 1 week
    
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    /**
     * Get cached response if it exists.
     * @param key Cache key
     * @param ignoreExpiry If true, return cached data regardless of age (for offline mode).
     *                     If false, only return if cache is less than 7 days old.
     */
    fun get(key: String, ignoreExpiry: Boolean = false): String? {
        val file = File(cacheDir, key.hashCode().toString())
        if (!file.exists()) return null
        
        // Check if cache is expired (only if we're not ignoring expiry)
        if (!ignoreExpiry) {
            val age = System.currentTimeMillis() - file.lastModified()
            if (age > cacheMaxAgeMs) {
                return null  // Don't delete - might need it if network fails
            }
        }
        
        return try {
            file.readText()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Store response in cache
     */
    fun put(key: String, value: String) {
        try {
            val file = File(cacheDir, key.hashCode().toString())
            file.writeText(value)
        } catch (e: Exception) {
            // Ignore cache write failures
        }
    }
    
    /**
     * Check if a key exists in cache (regardless of expiry)
     */
    fun has(key: String): Boolean {
        return File(cacheDir, key.hashCode().toString()).exists()
    }
    
    /**
     * Clear all cached data
     */
    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}

