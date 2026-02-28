package com.watchwhatwhere.app.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.watchwhatwhere.app.data.api.MobileConfigApi
import com.watchwhatwhere.app.data.api.WatchWhatWhereApi
import com.watchwhatwhere.app.data.repository.MobileConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Persistent cookie jar that bridges OkHttp to Android's WebKit CookieManager.
 * Cookies persist forever across app restarts (no expiry).
 */
class PersistentCookieJar : CookieJar {
    companion object {
        /** Expiry time of the loginname cookie in epoch millis, or null if unknown */
        var loginCookieExpiresAt: Long? = null
            private set
    }
    
    private val cookieManager = android.webkit.CookieManager.getInstance().apply {
        setAcceptCookie(true)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        for (cookie in cookies) {
            cookieManager.setCookie(urlString, cookie.toString())
            // Track the loginname cookie expiry
            if (cookie.name == "loginname") {
                loginCookieExpiresAt = if (cookie.expiresAt != Long.MAX_VALUE) cookie.expiresAt else null
            }
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val urlString = url.toString()
        val cookieString = cookieManager.getCookie(urlString) ?: return emptyList()
        return cookieString.split(";").mapNotNull { header ->
            Cookie.parse(url, header.trim())
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    
    @Provides
    @Singleton
    fun provideCookieJar(): CookieJar = PersistentCookieJar()
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: CookieJar,
        configRepositoryProvider: javax.inject.Provider<MobileConfigRepository>
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        val configInterceptor = ConfigUrlInterceptor {
            try { configRepositoryProvider.get() } catch (_: Exception) { null }
        }
        
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(configInterceptor)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(WatchWhatWhereApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideWatchWhatWhereApi(retrofit: Retrofit): WatchWhatWhereApi {
        return retrofit.create(WatchWhatWhereApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideMobileConfigApi(retrofit: Retrofit): MobileConfigApi {
        return retrofit.create(MobileConfigApi::class.java)
    }
}





