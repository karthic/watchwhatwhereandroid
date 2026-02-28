package com.watchwhatwhere.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Full title detail response
 */
@Serializable
data class TitleDetail(
    val id: Long,
    val nama: String,
    val typa: String,
    val genres: List<String>? = null,
    val data: TitleData? = null,
    val episodes: List<Episode>? = null,
    val videos: List<Video>? = null,
    val princ: List<CastMember>? = null,
    val prods: List<ProductionCompany>? = null,
    val colls: List<Collection>? = null,
    val rec: List<TitleItem>? = null,
    val sim: List<TitleItem>? = null,
    val reviews: List<Review>? = null,
    @SerialName("reviews_site")
    val reviewsSite: List<Review>? = null,
    val reviewcount: Int? = null,
    val watchurls: List<WatchUrl>? = null
)

@Serializable
data class TitleData(
    val overview: String? = null,
    val tagline: String? = null,
    val runtime: Int? = null,
    val runtimeMinutes: String? = null,
    val startYear: String? = null,
    val endYear: String? = null,
    @SerialName("backdrop_path")
    val backdropPath: String? = null,
    @SerialName("backdrop_path_small")
    val backdropPathSmall: String? = null,
    @SerialName("poster_path")
    val posterPath: String? = null,
    @SerialName("poster_path_small")
    val posterPathSmall: String? = null,
    val homepage: String? = null,
    @SerialName("origin_country")
    val originCountry: String? = null,
    @SerialName("vote_average")
    val voteAverage: Double? = null,
    val averageRating: String? = null,
    val numVotes: String? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val status: String? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("first_air_date")
    val firstAirDate: String? = null
) {
    // Get rating as a value out of 5 stars
    val starRating: Float?
        get() {
            val rating = averageRating?.toFloatOrNull() ?: voteAverage?.toFloat()
            return rating?.let { it / 2f }
        }
    
    // Format budget/revenue as currency
    fun formatMoney(amount: Long?): String? {
        if (amount == null || amount == 0L) return null
        return when {
            amount >= 1_000_000_000 -> "$${amount / 1_000_000_000.0}B"
            amount >= 1_000_000 -> "$${amount / 1_000_000.0}M"
            amount >= 1_000 -> "$${amount / 1_000.0}K"
            else -> "$$amount"
        }
    }
}

@Serializable
data class Episode(
    val id: Long? = null,
    val episodeid: Long? = null,
    val name: String? = null,
    val nama: String? = null,  // Some responses use nama
    val seasonnum: Int? = null,
    val episodenum: Int? = null,
    @SerialName("still_path_small")
    val stillPathSmall: String? = null,
    @SerialName("still_path")
    val stillPath: String? = null,
    val episodeinfo: EpisodeInfo? = null
) {
    val displayName: String
        get() = "S${seasonnum ?: 0}E${episodenum ?: 0} - ${name ?: nama ?: "Episode"}"
    
    // Get still image from episodeinfo if not directly available
    val resolvedStillPath: String?
        get() = stillPathSmall ?: stillPath ?: episodeinfo?.stillPathSmall
}

@Serializable
data class EpisodeInfo(
    val id: Long? = null,
    val nama: String? = null,
    @SerialName("still_path_small")
    val stillPathSmall: String? = null,
    @SerialName("poster_path_small")
    val posterPathSmall: String? = null
)

@Serializable
data class Video(
    val id: Long? = null,
    val nama: String? = null,
    val videokey: String? = null,
    val typa: String? = null
) {
    val youtubeThumbnail: String?
        get() = videokey?.let { "https://img.youtube.com/vi/$it/0.jpg" }
    
    val youtubeUrl: String?
        get() = videokey?.let { "https://www.youtube.com/watch?v=$it" }
}

@Serializable
data class CastMember(
    val artistid: Long? = null,
    val name: String? = null,
    val category: String? = null,
    val characters: List<String>? = null,
    @SerialName("profile_path_small")
    val profilePathSmall: String? = null,
    @SerialName("profile_path")
    val profilePath: String? = null
)

@Serializable
data class ProductionCompany(
    val id: Long,
    val nama: String,
    @SerialName("logo_path")
    val logoPath: String? = null
)

@Serializable
data class Collection(
    val id: Long? = null,
    val nama: String? = null,
    val others: List<TitleItem>? = null
)

@Serializable
data class Review(
    val author: String? = null,
    val content: String? = null,
    val data: String? = null, // Date string
    val source: String? = null // "user" or "tmdb"
)

@Serializable
data class WatchUrl(
    val id: Long,
    val titleid: Long? = null,
    val source: String? = null,
    val viewcost: String? = null, // free, sub, buy, rent
    val pageurl: String? = null,
    val logo: String? = null,
    val imageurl: String? = null
) {
    val displayName: String
        get() = source?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Watch"
    
    val isFree: Boolean
        get() = viewcost == "free"
}
