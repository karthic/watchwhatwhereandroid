package com.watchwhatwhere.app.data.repository

import com.watchwhatwhere.app.data.api.WatchWhatWhereApi
import com.watchwhatwhere.app.data.cache.ResponseCache
import com.watchwhatwhere.app.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TitleRepository @Inject constructor(
    private val api: WatchWhatWhereApi,
    private val cache: ResponseCache,
    private val json: Json
) {
    /**
     * Get homepage content
     */
    suspend fun getHome(): Result<HomeResponse> {
        val cacheKey = "home"
        
        return try {
            val response = api.getHome()
            // Cache successful response
            cache.put(cacheKey, json.encodeToString(response))
            Result.success(response)
        } catch (e: Exception) {
            android.util.Log.e("TitleRepository", "getHome failed", e)
            // Try to get from cache
            val cached = cache.get(cacheKey, ignoreExpiry = true)
            if (cached != null) {
                try {
                    Result.success(json.decodeFromString<HomeResponse>(cached))
                } catch (e2: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Browse titles by type with optional genre and offset.
     * Uses separate API methods for different URL patterns:
     *   /jsonc/type/{type}
     *   /jsonc/type/{type}/{genre}
     *   /jsonc/type/{type}/{genre}/{offset}
     */
    suspend fun getTypes(
        type: String,
        genre: String? = null,
        offset: Int? = null
    ): Result<List<TitleItem>> {
        val cacheKey = "types_${type}_${genre}_$offset"
        
        return try {
            val response = when {
                genre != null && offset != null -> api.getTypesWithGenreAndOffset(type, genre, offset)
                genre != null -> api.getTypesWithGenre(type, genre)
                offset != null -> api.getTypesWithOffset(type, offset)
                else -> api.getTypes(type)
            }
            val items = response.map { it.data }
            cache.put(cacheKey, json.encodeToString(items))
            Result.success(items)
        } catch (e: Exception) {
            android.util.Log.e("TitleRepository", "getTypes failed for $type", e)
            val cached = cache.get(cacheKey, ignoreExpiry = true)
            if (cached != null) {
                try {
                    Result.success(json.decodeFromString<List<TitleItem>>(cached))
                } catch (e2: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Browse titles by production company.
     * /jsonc/prod/{companyId}/{offset?}
     */
    suspend fun getProdCompanyTitles(
        companyId: Long,
        offset: Int? = null
    ): Result<List<TitleItem>> {
        val cacheKey = "prod_${companyId}_$offset"
        return try {
            val response = if (offset != null && offset > 0) {
                api.getProdCompanyTitlesWithOffset(companyId, offset)
            } else {
                api.getProdCompanyTitles(companyId)
            }
            val items = response.map { it.data }
            cache.put(cacheKey, json.encodeToString(items))
            Result.success(items)
        } catch (e: Exception) {
            android.util.Log.e("TitleRepository", "getProdCompanyTitles failed for $companyId", e)
            val cached = cache.get(cacheKey, ignoreExpiry = true)
            if (cached != null) {
                try {
                    Result.success(json.decodeFromString<List<TitleItem>>(cached))
                } catch (e2: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get available genres for a type — now served from the static list in mobileconfig.
     * Kept for backward compat; BrowseScreen may still call this but should migrate to config.
     */
    suspend fun getGenres(type: String): Result<List<String>> {
        // Genres are now in mobileconfig.json → content.genres
        // Return empty to signal callers to use the config
        return Result.success(emptyList())
    }
    
    /**
     * Search titles - not cached as searches are dynamic
     */
    suspend fun search(query: String): Result<List<TitleItem>> = runCatching {
        api.search(query = query).map { it.toTitleItem() }
    }
    
    /**
     * Get title details
     */
    suspend fun getTitleDetail(titleId: Long): Result<TitleDetail> {
        val cacheKey = "title_$titleId"
        
        return try {
            val response = api.getTitleDetail(titleId = titleId.toString())
            cache.put(cacheKey, json.encodeToString(response))
            Result.success(response)
        } catch (e: Exception) {
            android.util.Log.e("TitleRepository", "getTitleDetail failed for $titleId", e)
            val cached = cache.get(cacheKey, ignoreExpiry = true)
            if (cached != null) {
                try {
                    Result.success(json.decodeFromString<TitleDetail>(cached))
                } catch (e2: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get artist details
     */
    suspend fun getArtist(artistId: Long): Result<ArtistDetail> {
        val cacheKey = "artist_$artistId"
        
        return try {
            val response = api.getArtist(artistId = artistId.toString())
            cache.put(cacheKey, json.encodeToString(response))
            Result.success(response)
        } catch (e: Exception) {
            android.util.Log.e("TitleRepository", "getArtist failed for $artistId", e)
            val cached = cache.get(cacheKey, ignoreExpiry = true)
            if (cached != null) {
                try {
                    Result.success(json.decodeFromString<ArtistDetail>(cached))
                } catch (e2: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get full list of streaming providers
     */
    suspend fun getProviders(): Result<List<Provider>> {
        val cacheKey = "providers_list"
        return try {
            val providers = api.getProviders()
            cache.put(cacheKey, json.encodeToString(providers))
            Result.success(providers)
        } catch (e: Exception) {
            android.util.Log.e("TitleRepository", "getProviders failed", e)
            val cached = cache.get(cacheKey, ignoreExpiry = true)
            if (cached != null) {
                try {
                    Result.success(json.decodeFromString<List<Provider>>(cached))
                } catch (e2: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Set provider preference (prio or hide)
     */
    suspend fun setProviderPref(source: String, status: String): Result<Unit> = runCatching {
        api.postAction(mapOf(
            "func" to "provpref",
            "do" to "put",
            "source" to source,
            "status" to status
        ))
    }
    
    /**
     * Delete provider preference (reset to default)
     */
    suspend fun deleteProviderPref(source: String): Result<Unit> = runCatching {
        api.postAction(mapOf(
            "func" to "provpref",
            "do" to "del",
            "source" to source
        ))
    }
    
    /**
     * Get current user provider preferences from server.
     * Returns map of source -> status ("prio" or "hide").
     */
    suspend fun getProviderPrefs(): Result<Map<String, String>> {
        return try {
            val response = api.postAction(mapOf(
                "func" to "provpref",
                "do" to "get"
            ))
            val bodyString = response.string()
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val parsed = json.decodeFromString<ProviderPrefsResponse>(bodyString)
            Result.success(parsed.prefs ?: emptyMap())
        } catch (e: Exception) {
            android.util.Log.e("TitleRepository", "getProviderPrefs failed", e)
            Result.failure(e)
        }
    }
}
