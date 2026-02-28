package com.watchwhatwhere.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Artist/Person detail response
 */
@Serializable
data class ArtistDetail(
    val id: Long,
    val nama: String,
    val data: ArtistData? = null,
    val princ: List<ArtistCredit>? = null
)

@Serializable
data class ArtistData(
    val biography: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    @SerialName("place_of_birth")
    val placeOfBirth: String? = null,
    val gender: String? = null,
    @SerialName("known_for_department")
    val knownForDepartment: String? = null,
    val primaryProfession: String? = null,
    @SerialName("tmdb_also_known_as")
    val alsoKnownAs: String? = null,
    @SerialName("profile_path")
    val profilePath: String? = null,
    @SerialName("profile_path_small")
    val profilePathSmall: String? = null
)

@Serializable
data class ArtistCredit(
    val category: String? = null,
    val titleid: Long? = null,
    val name: String? = null,
    val data: TitleItem? = null
)

/**
 * Search result item
 */
@Serializable
data class SearchResult(
    val id: String,
    val typa: String,
    val nama: String,
    @SerialName("poster_path")
    val posterPath: String? = null,
    val startYear: String? = null,
    val rating: String? = null
) {
    fun toTitleItem(): TitleItem = TitleItem(
        id = id.toLongOrNull() ?: 0,
        nama = nama,
        typa = typa,
        posterPathSmall = posterPath,
        startYear = startYear,
        rating = rating?.toDoubleOrNull()
    )
}
