package com.watchwhatwhere.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────
// Profile page responses (watchlist, seen, rated, reviews)
// ─────────────────────────────────────────────

/**
 * A title item as returned by the profile endpoints (watchlist, seenlist, rated).
 * Slightly different from the browse TitleItem — includes tag_data and star_rating.
 */
@Serializable
data class ProfileTitleItem(
    val id: Long,
    val nama: String? = null,
    val typa: String? = null,
    val startYear: String? = null,
    @SerialName("poster_path_small")
    val posterPathSmall: String? = null,
    @SerialName("poster_path")
    val posterPath: String? = null,
    @SerialName("tag_data")
    val tagData: String? = null,
    @SerialName("star_rating")
    val starRating: Int? = null
) {
    val displayTitle: String
        get() = if (startYear != null) "${nama ?: "Unknown"} ($startYear)" else (nama ?: "Unknown")
}

/**
 * Paginated response from profile endpoints (watchlist, seenlist, rated).
 */
@Serializable
data class ProfilePageResponse(
    val success: Boolean = false,
    val items: List<ProfileTitleItem> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false
)

/**
 * A review item with nested title info.
 */
@Serializable
data class ReviewItem(
    val title: ReviewTitleInfo? = null,
    val content: String? = null,
    val tima: Long? = null // unix timestamp
)

@Serializable
data class ReviewTitleInfo(
    val id: Long,
    val nama: String? = null,
    val typa: String? = null,
    val startYear: String? = null,
    @SerialName("poster_path_small")
    val posterPathSmall: String? = null,
    @SerialName("poster_path")
    val posterPath: String? = null
)

/**
 * Paginated response for reviews.
 */
@Serializable
data class ReviewPageResponse(
    val success: Boolean = false,
    val items: List<ReviewItem> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false
)

// ─────────────────────────────────────────────
// Custom user lists
// ─────────────────────────────────────────────

@Serializable
data class UserList(
    val id: String,
    val nama: String,
    val privacy: String? = null,
    val hash: String? = null
)

@Serializable
data class UserListsResponse(
    val lists: List<UserList> = emptyList()
)

@Serializable
data class UserListItem(
    val id: Long,
    val nama: String? = null,
    val startYear: String? = null,
    @SerialName("poster_path_small")
    val posterPathSmall: String? = null,
    @SerialName("poster_path")
    val posterPath: String? = null
) {
    val displayTitle: String
        get() = if (startYear != null) "${nama ?: "Unknown"} ($startYear)" else (nama ?: "Unknown")
}

@Serializable
data class UserListItemsResponse(
    val items: List<UserListItem> = emptyList()
)

// ─────────────────────────────────────────────
// Tagging
// ─────────────────────────────────────────────

@Serializable
data class TagResponse(
    val success: Boolean? = null,
    val vote: String? = null,     // "up" or "down"
    val list: String? = null,     // timestamp string if on watchlist
    val seen: String? = null,     // timestamp string if seen
    val star: String? = null,     // "1"-"5" rating
    @SerialName("star_rating")
    val starRating: String? = null, // alternative field name for star rating
    val share: String? = null     // timestamp string if shared
)

// ─────────────────────────────────────────────
// User profile
// ─────────────────────────────────────────────

@Serializable
data class ProfileResponse(
    val success: Boolean = false,
    val defaultshare: String? = null // default share list id
)
