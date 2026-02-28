package com.watchwhatwhere.app.di

import android.util.Log
import com.watchwhatwhere.app.data.repository.MobileConfigRepository
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that dynamically rewrites request URLs based on the live
 * MobileConfig. This allows the server to change endpoint paths (e.g.
 * /jsonc/home → /jsonc/home2) via mobileconfig.json without an app update.
 *
 * Rewrites:
 *  - POST endpoint path (inputs/getdata.php → config.api.postEndpoint)
 *  - GET /jsonc/home → config.api.endpoints["home"].url
 *  - GET /jsonc/type/{...} → config.api.endpoints["browse"].urlPattern
 *  - GET /jsonc/title/{...} → config.api.endpoints["title"].urlPattern
 *  - GET /jsonc/artist/{...} → config.api.endpoints["artist"].urlPattern
 */
class ConfigUrlInterceptor(
    private val configProvider: () -> MobileConfigRepository?
) : Interceptor {

    companion object {
        private const val TAG = "ConfigUrlInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val config = configProvider()?.config?.value ?: return chain.proceed(original)
        
        val originalPath = original.url.encodedPath
        val newPath = rewritePath(originalPath, config.api.postEndpoint, config.api.endpoints)
        
        if (newPath != null && newPath != originalPath) {
            Log.d(TAG, "Rewriting: $originalPath → $newPath")
            val newUrl = original.url.newBuilder()
                .encodedPath(newPath)
                .build()
            val newRequest = original.newBuilder().url(newUrl).build()
            return chain.proceed(newRequest)
        }
        
        return chain.proceed(original)
    }
    
    private fun rewritePath(
        path: String,
        postEndpoint: String,
        endpoints: Map<String, com.watchwhatwhere.app.data.model.EndpointConfig>
    ): String? {
        // Rewrite POST endpoint (e.g. /inputs/getdata.php)
        if (path.contains("getdata.php") && postEndpoint.isNotBlank()) {
            return postEndpoint
        }
        
        // Rewrite GET /jsonc/home
        if (path == "/jsonc/home") {
            return endpoints["home"]?.url
        }
        
        // Rewrite GET /jsonc/type/{type}/{genre?}/{offset?}
        if (path.startsWith("/jsonc/type/")) {
            val pattern = endpoints["browse"]?.urlPattern ?: return null
            // Extract path segments after /jsonc/type/
            val segments = path.removePrefix("/jsonc/type/").split("/")
            val type = segments.getOrNull(0) ?: return null
            val genre = segments.getOrNull(1)
            val offset = segments.getOrNull(2)
            
            // Build the new path from the pattern
            var newPath = pattern
                .replace("{type}", type)
                .replace("/{genre?}", if (genre != null) "/$genre" else "")
                .replace("/{offset?}", if (offset != null) "/$offset" else "")
            return newPath
        }
        
        // Rewrite GET /jsonc/title/{titleId}
        if (path.startsWith("/jsonc/title/")) {
            val pattern = endpoints["title"]?.urlPattern ?: return null
            val titleId = path.removePrefix("/jsonc/title/")
            return pattern.replace("{titleId}", titleId)
        }
        
        // Rewrite GET /jsonc/artist/{artistId}
        if (path.startsWith("/jsonc/artist/")) {
            val pattern = endpoints["artist"]?.urlPattern ?: return null
            val artistId = path.removePrefix("/jsonc/artist/")
            return pattern.replace("{artistId}", artistId)
        }
        
        return null
    }
}
