package com.watchwhatwhere.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a title item in lists (home, browse, search results)
 */
@Serializable
data class TitleItem(
    val id: Long,
    val nama: String,
    val typa: String,
    val rating: Double? = null,
    @SerialName("poster_path_small")
    val posterPathSmall: String? = null,
    @SerialName("poster_path")
    val posterPath: String? = null,
    val startYear: String? = null,
    val wu: List<String>? = null // Watch options: free, sub, buy, rent
) {
    val isFree: Boolean
        get() = wu?.contains("free") == true
    
    val displayTitle: String
        get() = if (startYear != null) "$nama ($startYear)" else nama
}

/**
 * Wrapper for items in types/browse response
 */
@Serializable
data class TitleItemWrapper(
    val id: Long,
    val typa: String,
    val nama: String,
    val data: TitleItem
)

/**
 * Homepage response with categorized content
 */
@Serializable
data class HomeResponse(
    val trend: List<TitleItem>? = null,
    val free: List<TitleItem>? = null,
    @SerialName("free_movie")
    val freeMovie: List<TitleItem>? = null,
    @SerialName("free_tv")
    val freeTv: List<TitleItem>? = null,
    val isfree: List<TitleItem>? = null,
    val isnowplaying: List<TitleItem>? = null,
    val ispopular: List<TitleItem>? = null,
    val istoprated: List<TitleItem>? = null,
    val isupcoming: List<TitleItem>? = null,
    val movie: List<TitleItem>? = null,
    val tvMovie: List<TitleItem>? = null,
    val tvSeries: List<TitleItem>? = null,
    val tvMiniSeries: List<TitleItem>? = null,
    val tvSpecial: List<TitleItem>? = null,
    val tvShort: List<TitleItem>? = null,
    val short: List<TitleItem>? = null,
    val video: List<TitleItem>? = null,
    val videoGame: List<TitleItem>? = null
) {
    fun toCategories(): List<Pair<String, List<TitleItem>>> {
        return listOfNotNull(
            trend?.takeIf { it.isNotEmpty() }?.let { "Trending" to it },
            free?.takeIf { it.isNotEmpty() }?.let { "Free to Watch" to it },
            freeMovie?.takeIf { it.isNotEmpty() }?.let { "Free Movies" to it },
            freeTv?.takeIf { it.isNotEmpty() }?.let { "Free TV Shows" to it },
            isnowplaying?.takeIf { it.isNotEmpty() }?.let { "Now Playing" to it },
            ispopular?.takeIf { it.isNotEmpty() }?.let { "Popular" to it },
            istoprated?.takeIf { it.isNotEmpty() }?.let { "Top Rated" to it },
            isupcoming?.takeIf { it.isNotEmpty() }?.let { "Upcoming" to it },
            movie?.takeIf { it.isNotEmpty() }?.let { "Movies" to it },
            tvMovie?.takeIf { it.isNotEmpty() }?.let { "TV Movies" to it },
            tvSeries?.takeIf { it.isNotEmpty() }?.let { "TV Series" to it },
            tvMiniSeries?.takeIf { it.isNotEmpty() }?.let { "Mini Series" to it },
            tvSpecial?.takeIf { it.isNotEmpty() }?.let { "TV Specials" to it },
            tvShort?.takeIf { it.isNotEmpty() }?.let { "TV Shorts" to it },
            short?.takeIf { it.isNotEmpty() }?.let { "Shorts" to it },
            video?.takeIf { it.isNotEmpty() }?.let { "Videos" to it },
            videoGame?.takeIf { it.isNotEmpty() }?.let { "Video Games" to it }
        )
    }
}
