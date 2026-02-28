package com.watchwhatwhere.app.data.api

import com.watchwhatwhere.app.data.model.MobileConfig
import retrofit2.http.GET

/**
 * Retrofit interface for fetching the remote mobile config.
 * This is a simple GET of a static JSON file, separate from the main POST-based API.
 */
interface MobileConfigApi {
    @GET("mobileconfig.json")
    suspend fun getConfig(): MobileConfig
}
