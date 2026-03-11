package com.watchwhatwhere.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient

@HiltAndroidApp
class WatchWhatWhereApplication : Application(), ImageLoaderFactory {
    
    override fun onCreate() {
        super.onCreate()
        // Clear stale image cache on each launch to avoid serving old formats
        val cacheDir = cacheDir.resolve("image_cache")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }
    
    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "image/png, image/jpeg, image/webp, */*")
                    .build()
                chain.proceed(request)
            }
            .build()
        
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(500L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
