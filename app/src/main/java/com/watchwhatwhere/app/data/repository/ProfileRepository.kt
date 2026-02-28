package com.watchwhatwhere.app.data.repository

import android.content.Context
import android.util.Log
import com.watchwhatwhere.app.data.api.WatchWhatWhereApi
import com.watchwhatwhere.app.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val api: WatchWhatWhereApi,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ProfileRepo"
        private const val PREFS_NAME = "wwwprefs"
        private const val KEY_DEFAULT_SHARE_LIST = "default_share_list_id"
    }
    
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // ─────────────────────────────────────────────
    // Profile lists (watchlist, seenlist, rated, reviews)
    // ─────────────────────────────────────────────
    
    suspend fun getWatchlist(page: Int = 1): Result<ProfilePageResponse> {
        return fetchProfilePage("watchlist", page)
    }
    
    suspend fun getSeenList(page: Int = 1): Result<ProfilePageResponse> {
        return fetchProfilePage("seenlist", page)
    }
    
    suspend fun getRated(page: Int = 1): Result<ProfilePageResponse> {
        return fetchProfilePage("rated", page)
    }
    
    suspend fun getReviews(page: Int = 1): Result<ReviewPageResponse> {
        return try {
            val body = api.postAction(mapOf(
                "func" to "profile",
                "do" to "get",
                "dowhat" to "reviews",
                "page" to page.toString()
            )).string()
            Log.d(TAG, "reviews response: $body")
            val response = json.decodeFromString<ReviewPageResponse>(body)
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "getReviews failed", e)
            Result.failure(e)
        }
    }
    
    private suspend fun fetchProfilePage(dowhat: String, page: Int): Result<ProfilePageResponse> {
        return try {
            val body = api.postAction(mapOf(
                "func" to "profile",
                "do" to "get",
                "dowhat" to dowhat,
                "page" to page.toString()
            )).string()
            Log.d(TAG, "$dowhat response: $body")
            val response = json.decodeFromString<ProfilePageResponse>(body)
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "fetch $dowhat failed", e)
            Result.failure(e)
        }
    }
    
    // ─────────────────────────────────────────────
    // Custom user lists
    // ─────────────────────────────────────────────
    
    suspend fun getUserLists(): Result<List<UserList>> {
        return try {
            val body = api.postAction(mapOf(
                "func" to "userlist",
                "do" to "get",
                "dowhat" to "getlists"
            )).string()
            Log.d(TAG, "getUserLists response: $body")
            val response = json.decodeFromString<UserListsResponse>(body)
            Result.success(response.lists)
        } catch (e: Exception) {
            Log.e(TAG, "getUserLists failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun getListItems(listId: String): Result<List<UserListItem>> {
        return try {
            val body = api.postAction(mapOf(
                "func" to "userlist",
                "do" to "get",
                "dowhat" to "getitems",
                "listid" to listId
            )).string()
            Log.d(TAG, "getListItems response: $body")
            val response = json.decodeFromString<UserListItemsResponse>(body)
            Result.success(response.items)
        } catch (e: Exception) {
            Log.e(TAG, "getListItems failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun createList(name: String, privacy: String = "private"): Result<String> {
        return try {
            val response = api.postAction(mapOf(
                "func" to "userlist",
                "do" to "put",
                "dowhat" to "createlist",
                "nama" to name,
                "privacy" to privacy
            )).string()
            Log.d(TAG, "createList response: $response")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "createList failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun deleteList(listId: String): Result<Unit> {
        return try {
            api.postAction(mapOf(
                "func" to "userlist",
                "do" to "del",
                "dowhat" to "dellist",
                "listid" to listId
            )).string()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteList failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun renameList(listId: String, name: String): Result<Unit> {
        return try {
            api.postAction(mapOf(
                "func" to "userlist",
                "do" to "put",
                "dowhat" to "renamelist",
                "listid" to listId,
                "nama" to name
            )).string()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "renameList failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun removeFromList(listId: String, titleId: String): Result<Unit> {
        return try {
            api.postAction(mapOf(
                "func" to "userlist",
                "do" to "del",
                "dowhat" to "delitem",
                "listid" to listId,
                "titleid" to titleId
            )).string()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "removeFromList failed", e)
            Result.failure(e)
        }
    }
    
    // ─────────────────────────────────────────────
    // Tagging (vote, list, seen, star, share)
    // ─────────────────────────────────────────────
    
    suspend fun tagTitle(
        titleId: String,
        tag: String,
        action: String = "put",
        extras: Map<String, String> = emptyMap()
    ): Result<Unit> {
        return try {
            val fields = mutableMapOf(
                "func" to "tag",
                "do" to action,
                "titleid" to titleId,
                "tag" to tag
            )
            fields.putAll(extras)
            Log.d(TAG, "tagTitle request: $fields")
            val response = api.postAction(fields).string()
            Log.d(TAG, "tagTitle response: $response")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "tagTitle failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun getTags(titleId: String): Result<TagResponse> {
        return try {
            val body = api.postAction(mapOf(
                "func" to "tag",
                "do" to "get",
                "titleid" to titleId
            )).string()
            Log.d(TAG, "getTags response for $titleId: $body")
            val response = json.decodeFromString<TagResponse>(body)
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "getTags failed", e)
            Result.failure(e)
        }
    }
    
    // ─────────────────────────────────────────────
    // Reviews
    // ─────────────────────────────────────────────
    
    suspend fun getUserReview(titleId: String): Result<String?> {
        return try {
            val body = api.postAction(mapOf(
                "func" to "review",
                "do" to "get",
                "titleid" to titleId
            )).string()
            Log.d(TAG, "getUserReview response for $titleId: $body")
            if (body.isBlank() || body == "null" || body == "[]" || body == "{}") {
                Result.success(null)
            } else {
                val jsonObj = json.parseToJsonElement(body)
                val content = jsonObj.jsonObject["content"]?.jsonPrimitive?.contentOrNull
                Result.success(content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUserReview failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun submitReview(titleId: String, content: String): Result<Unit> {
        return try {
            api.postAction(mapOf(
                "func" to "review",
                "do" to "put",
                "titleid" to titleId,
                "content" to content
            )).string()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "submitReview failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun deleteReview(titleId: String): Result<Unit> {
        return try {
            api.postAction(mapOf(
                "func" to "review",
                "do" to "del",
                "titleid" to titleId
            )).string()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteReview failed", e)
            Result.failure(e)
        }
    }
    
    // ─────────────────────────────────────────────
    // User profile / default share (local storage)
    // ─────────────────────────────────────────────
    
    fun getDefaultShareListId(): String? {
        return prefs.getString(KEY_DEFAULT_SHARE_LIST, null)
    }
    
    fun setDefaultShareList(listId: String) {
        prefs.edit().putString(KEY_DEFAULT_SHARE_LIST, listId).apply()
        Log.d(TAG, "setDefaultShareList: $listId")
    }
    
    suspend fun addToList(listId: String, titleId: String): Result<Unit> {
        return try {
            val response = api.postAction(mapOf(
                "func" to "userlist",
                "do" to "put",
                "dowhat" to "additem",
                "listid" to listId,
                "titleid" to titleId
            )).string()
            Log.d(TAG, "addToList response: $response")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addToList failed", e)
            Result.failure(e)
        }
    }
}
