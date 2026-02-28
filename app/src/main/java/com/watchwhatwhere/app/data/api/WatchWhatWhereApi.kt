package com.watchwhatwhere.app.data.api

import com.watchwhatwhere.app.data.model.*
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface WatchWhatWhereApi {
    
    companion object {
        const val BASE_URL = "https://watchwhatwhere.com/"
    }
    
    // ─────────────────────────────────────────────
    // Auth endpoints (still POST to getdata.php)
    // ─────────────────────────────────────────────
    
    /**
     * Check the current session. Returns myinfo if a valid login cookie exists.
     * Returns empty body if no session, so we use ResponseBody to handle it.
     */
    @FormUrlEncoded
    @POST("inputs/getdata.php")
    suspend fun checkSession(
        @Field("func") func: String = "login",
        @Field("do") doAction: String = "get"
    ): ResponseBody
    
    /**
     * Login with an OAuth provider. Sends provider-specific fields to the server.
     */
    @FormUrlEncoded
    @POST("inputs/getdata.php")
    suspend fun loginWithProvider(
        @Field("func") func: String = "login",
        @Field("do") doAction: String = "get",
        @FieldMap fields: Map<String, String>
    ): ResponseBody
    
    // ─────────────────────────────────────────────
    // Public data endpoints (GET /jsonc/...)
    // ─────────────────────────────────────────────
    
    /**
     * Get homepage content with categorized titles
     */
    @GET("jsonc/home")
    suspend fun getHome(): HomeResponse
    
    /**
     * Browse titles by type (e.g. movie, tvSeries, free, trend, etc.)
     */
    @GET("jsonc/type/{type}")
    suspend fun getTypes(
        @Path("type") type: String
    ): List<TitleItemWrapper>
    
    /**
     * Browse titles by type with genre filter
     */
    @GET("jsonc/type/{type}/{genre}")
    suspend fun getTypesWithGenre(
        @Path("type") type: String,
        @Path("genre") genre: String
    ): List<TitleItemWrapper>
    
    /**
     * Browse titles by type with pagination offset (no genre filter)
     */
    @GET("jsonc/type/{type}/{offset}")
    suspend fun getTypesWithOffset(
        @Path("type") type: String,
        @Path("offset") offset: Int
    ): List<TitleItemWrapper>
    
    /**
     * Browse titles by type with genre and pagination offset
     */
    @GET("jsonc/type/{type}/{genre}/{offset}")
    suspend fun getTypesWithGenreAndOffset(
        @Path("type") type: String,
        @Path("genre") genre: String,
        @Path("offset") offset: Int
    ): List<TitleItemWrapper>
    
    /**
     * Browse titles by production company
     */
    @GET("jsonc/prod/{companyId}")
    suspend fun getProdCompanyTitles(
        @Path("companyId") companyId: Long
    ): List<TitleItemWrapper>
    
    @GET("jsonc/prod/{companyId}/{offset}")
    suspend fun getProdCompanyTitlesWithOffset(
        @Path("companyId") companyId: Long,
        @Path("offset") offset: Int
    ): List<TitleItemWrapper>
    
    /**
     * Get full title details (movie or TV show)
     */
    @GET("jsonc/title/{titleId}")
    suspend fun getTitleDetail(
        @Path("titleId") titleId: String
    ): TitleDetail
    
    /**
     * Get artist/person details
     */
    @GET("jsonc/artist/{artistId}")
    suspend fun getArtist(
        @Path("artistId") artistId: String
    ): ArtistDetail
    
    // ─────────────────────────────────────────────
    // Search (still POST to getdata.php)
    // ─────────────────────────────────────────────
    
    /**
     * Search for titles
     */
    @FormUrlEncoded
    @POST("inputs/getdata.php")
    suspend fun search(
        @Field("func") func: String = "search",
        @Field("do") doAction: String = "get",
        @Field("q") query: String
    ): List<SearchResult>
    
    // ─────────────────────────────────────────────
    // Profile, Tags, Reviews, Lists (POST to getdata.php)
    // ─────────────────────────────────────────────
    
    /**
     * Generic POST action for profile, tag, review, userlist, viewlist, provpref endpoints.
     * All share the same getdata.php endpoint but with different func/do/dowhat params.
     */
    @FormUrlEncoded
    @POST("inputs/getdata.php")
    suspend fun postAction(
        @FieldMap fields: Map<String, String>
    ): ResponseBody
    
    /**
     * Get full list of streaming providers with source, viewcost, and logo.
     */
    @GET("dumpeddata/providers.json")
    suspend fun getProviders(): List<Provider>
    
    // ─────────────────────────────────────────────
    // Provider titles (GET /jsonc/prov/{source})
    // ─────────────────────────────────────────────
    
    /**
     * Browse titles available on a specific streaming provider.
     */
    @GET("jsonc/prov/{source}")
    suspend fun getProviderTitles(
        @Path("source") source: String
    ): List<TitleItemWrapper>
    
    @GET("jsonc/prov/{source}/{genre}")
    suspend fun getProviderTitlesWithGenre(
        @Path("source") source: String,
        @Path("genre") genre: String
    ): List<TitleItemWrapper>
    
    @GET("jsonc/prov/{source}/{offset}")
    suspend fun getProviderTitlesWithOffsetOnly(
        @Path("source") source: String,
        @Path("offset") offset: Int
    ): List<TitleItemWrapper>
    
    @GET("jsonc/prov/{source}/{genre}/{offset}")
    suspend fun getProviderTitlesWithOffset(
        @Path("source") source: String,
        @Path("genre") genre: String,
        @Path("offset") offset: Int
    ): List<TitleItemWrapper>
    
    // ─────────────────────────────────────────────
    // Contact form (POST to contact_submit.php)
    // ─────────────────────────────────────────────
    
    /**
     * Submit a contact form message.
     */
    @FormUrlEncoded
    @POST("inputs/contact_submit.php")
    suspend fun submitContact(
        @Field("name") name: String,
        @Field("email") email: String,
        @Field("message") message: String,
        @Field("company") company: String = "",  // honeypot field
        @Field("site") site: String = "WatchWhatWhere"
    ): ResponseBody
}
