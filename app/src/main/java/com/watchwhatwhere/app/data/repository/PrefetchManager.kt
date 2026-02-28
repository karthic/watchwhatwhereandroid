package com.watchwhatwhere.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import coil.ImageLoader
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.watchwhatwhere.app.data.cache.ResponseCache
import com.watchwhatwhere.app.data.model.TitleDetail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prefetches title detail JSON and all images for home screen titles (WiFi only).
 * Monitors WiFi state: pauses when WiFi drops, resumes when WiFi reconnects.
 */
@Singleton
class PrefetchManager @Inject constructor(
    private val titleRepository: TitleRepository,
    private val cache: ResponseCache,
    private val json: Json,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PrefetchManager"
        private const val PREFS_NAME = "prefetch_prefs"
        private const val KEY_COMPLETED_AT = "prefetch_completed_at"
        private const val KEY_TITLE_COUNT = "prefetch_title_count"
        private const val CACHE_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L // 1 week
    }
    
    data class PrefetchStatus(
        val totalTitles: Int = 0,
        val cachedTitles: Int = 0,
        val totalImages: Int = 0,
        val cachedImages: Int = 0,
        val isRunning: Boolean = false,
        val waitingForWifi: Boolean = false
    ) {
        val progress: String
            get() = when {
                totalTitles == 0 -> ""
                waitingForWifi -> "Waiting for WiFi to cache $totalTitles titles…"
                isRunning && cachedTitles < totalTitles -> "Caching $cachedTitles/$totalTitles titles…"
                isRunning -> "Caching images ($cachedImages/$totalImages)…"
                cachedTitles >= totalTitles -> "All $totalTitles titles cached ✓"
                else -> "$cachedTitles/$totalTitles titles cached"
            }
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _status = MutableStateFlow(PrefetchStatus())
    val status: StateFlow<PrefetchStatus> = _status.asStateFlow()
    
    private val imageLoader: ImageLoader get() = context.imageLoader
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefetchJob: Job? = null
    private var pendingTitleIds: List<Long>? = null  // stored when waiting for WiFi
    
    init {
        if (isPrefetchValid()) {
            val count = prefs.getInt(KEY_TITLE_COUNT, 0)
            _status.value = PrefetchStatus(totalTitles = count, cachedTitles = count)
        }
        
        // Listen for WiFi connectivity changes
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "WiFi connected")
                pendingTitleIds?.let { ids ->
                    Log.d(TAG, "Resuming prefetch for ${ids.size} titles")
                    startPrefetch(ids)
                }
            }
            override fun onLost(network: Network) {
                Log.d(TAG, "WiFi lost")
                if (prefetchJob?.isActive == true) {
                    Log.d(TAG, "Pausing prefetch")
                    prefetchJob?.cancel()
                    _status.value = _status.value.copy(isRunning = false, waitingForWifi = true)
                }
            }
        })
    }
    
    fun registerTitleIds(ids: List<Long>) {
        if (isPrefetchValid()) return
        if (prefetchJob?.isActive == true) return
        
        val distinctIds = ids.distinct()
        pendingTitleIds = distinctIds
        
        if (!isOnWifi()) {
            Log.d(TAG, "Not on WiFi — will start when WiFi connects")
            _status.value = PrefetchStatus(totalTitles = distinctIds.size, waitingForWifi = true)
            return
        }
        startPrefetch(distinctIds)
    }
    
    /** Check if prefetch was completed recently AND cache data still exists */
    private fun isPrefetchValid(): Boolean {
        val completedAt = prefs.getLong(KEY_COMPLETED_AT, 0L)
        if (completedAt == 0L || System.currentTimeMillis() - completedAt >= CACHE_MAX_AGE_MS) return false
        // Verify cache directory still has files (user might have cleared cache)
        val cacheDir = java.io.File(context.cacheDir, "api_cache")
        return cacheDir.exists() && (cacheDir.listFiles()?.size ?: 0) > 0
    }
    
    private fun isOnWifi(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    private fun startPrefetch(titleIds: List<Long>) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            _status.value = PrefetchStatus(totalTitles = titleIds.size, isRunning = true)
            val jsonSemaphore = Semaphore(3)
            val imageUrls = mutableListOf<String>()
            var cached = 0
            
            try {
                // ── Phase 0: Prefetch providers.json ──
                try {
                    titleRepository.getProviders()
                    Log.d(TAG, "Providers.json prefetched")
                } catch (_: Exception) { }
                
                // ── Phase 1: Fetch title detail JSON (3 concurrent) ──
                titleIds.map { titleId ->
                    async {
                        jsonSemaphore.withPermit {
                            ensureActive()
                            try {
                                val detail: TitleDetail? = if (cache.has("title_$titleId")) {
                                    try {
                                        val raw = cache.get("title_$titleId", ignoreExpiry = true)
                                        raw?.let { json.decodeFromString<TitleDetail>(it) }
                                    } catch (_: Exception) { null }
                                } else {
                                    titleRepository.getTitleDetail(titleId).getOrNull()
                                }
                                synchronized(this@PrefetchManager) {
                                    cached++
                                    detail?.let { extractImageUrls(it, imageUrls) }
                                }
                                _status.value = _status.value.copy(cachedTitles = cached)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to prefetch title $titleId", e)
                                synchronized(this@PrefetchManager) { cached++ }
                                _status.value = _status.value.copy(cachedTitles = cached)
                            }
                        }
                    }
                }.awaitAll()
                
                Log.d(TAG, "Title JSON done: $cached/${titleIds.size}. Found ${imageUrls.size} images.")
                
                // ── Phase 2: Download images to disk (3 concurrent) ──
                val uniqueUrls = imageUrls.distinct()
                _status.value = _status.value.copy(totalImages = uniqueUrls.size)
                val imgSemaphore = Semaphore(3)
                var imgCached = 0
                
                uniqueUrls.map { url ->
                    async {
                        imgSemaphore.withPermit {
                            ensureActive()
                            try {
                                val request = ImageRequest.Builder(context)
                                    .data(url)
                                    .size(400, 600)
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .build()
                                imageLoader.execute(request)
                            } catch (_: Exception) { }
                            synchronized(this@PrefetchManager) { imgCached++ }
                            if (imgCached % 25 == 0 || imgCached == uniqueUrls.size) {
                                _status.value = _status.value.copy(cachedImages = imgCached)
                            }
                        }
                    }
                }.awaitAll()
                
                _status.value = _status.value.copy(cachedImages = imgCached, isRunning = false)
                pendingTitleIds = null
                Log.d(TAG, "Prefetch complete: ${titleIds.size} titles, $imgCached/${uniqueUrls.size} images")
                
                prefs.edit()
                    .putLong(KEY_COMPLETED_AT, System.currentTimeMillis())
                    .putInt(KEY_TITLE_COUNT, titleIds.size)
                    .apply()
                    
            } catch (e: CancellationException) {
                Log.d(TAG, "Prefetch cancelled (WiFi lost)")
                // pendingTitleIds stays set — will resume when WiFi returns
            } catch (e: Exception) {
                Log.e(TAG, "Prefetch failed", e)
                _status.value = _status.value.copy(isRunning = false)
            }
        }
    }
    
    private fun extractImageUrls(detail: TitleDetail, out: MutableList<String>) {
        // Only cache images for THIS title's detail page (poster, backdrop, cast)
        // Skip rec/sim/collection posters — those titles aren't cached so they're useless offline
        detail.data?.posterPath?.let { out.add(it) }
        detail.data?.posterPathSmall?.let { out.add(it) }
        detail.data?.backdropPath?.let { out.add(it) }
        detail.data?.backdropPathSmall?.let { out.add(it) }
        detail.princ?.forEach { cast ->
            cast.profilePathSmall?.let { out.add(it) }
            cast.profilePath?.let { out.add(it) }
        }
    }
}
