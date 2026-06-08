package com.vunbo.watchtogether.feature.home

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vunbo.watchtogether.data.model.Movie
import com.vunbo.watchtogether.core.network.OkHttpHelper
import com.vunbo.watchtogether.core.storage.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class HomeSection(
    val key: String,
    val title: String,
    val videos: List<Movie.Video>
)

enum class HomeRankDefinition(
    val key: String,
    val title: String,
    val cat: Int? = null,
    val fallbackCat: Int? = null
) {
    Douban("douban", "豆瓣推荐"),
    Tv("tv", "电视榜", cat = 3),
    Movie("movie", "电影榜", cat = 2),
    Variety("variety", "综艺榜", cat = 4),
    Child("child", "儿童榜", cat = 1, fallbackCat = 7);

    companion object {
        fun fromKey(key: String): HomeRankDefinition? = entries.firstOrNull { it.key == key }
    }
}

object HomeRankProvider {
    private const val CACHE_TTL_MS = 12L * 60L * 60L * 1000L
    private const val CACHE_PREFIX = "home_rank_"
    private const val CACHE_TIME_SUFFIX = "_time"
    private const val DOUBAN_CACHE_KEY = "home_rank_douban_json"
    private const val DOUBAN_CACHE_TIME = "home_rank_douban_time"
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
        "Referer" to "https://www.360kan.com/rank/general"
    )

    suspend fun loadHomeSections(): List<HomeSection> {
        return HomeRankDefinition.entries.mapNotNull { definition ->
            val videos = loadRank(definition)
            if (videos.isEmpty()) null else HomeSection(definition.key, definition.title, videos)
        }
    }

    suspend fun loadRank(definition: HomeRankDefinition): List<Movie.Video> = withContext(Dispatchers.IO) {
        when (definition) {
            HomeRankDefinition.Douban -> loadDoubanHot()
            else -> load360Rank(definition)
        }
    }

    private fun load360Rank(definition: HomeRankDefinition): List<Movie.Video> {
        val cat = definition.cat ?: return emptyList()
        val cacheKey = CACHE_PREFIX + definition.key
        val cacheTimeKey = cacheKey + CACHE_TIME_SUFFIX
        val cached = PrefsManager.getString(cacheKey)
        val cacheTime = PrefsManager.getLong(cacheTimeKey)
        val now = System.currentTimeMillis()
        if (cached.isNotBlank() && now - cacheTime < CACHE_TTL_MS) {
            parse360Videos(cached, definition).takeIf { it.isNotEmpty() }?.let { return it }
        }

        val body = fetch360Rank(cat)
        if (!body.isNullOrBlank()) {
            PrefsManager.putString(cacheKey, body)
            PrefsManager.putLong(cacheTimeKey, now)
            parse360Videos(body, definition).takeIf { it.isNotEmpty() }?.let { return it }
        }

        definition.fallbackCat?.let { fallbackCat ->
            val fallbackBody = fetch360Rank(fallbackCat)
            if (!fallbackBody.isNullOrBlank()) {
                PrefsManager.putString(cacheKey, fallbackBody)
                PrefsManager.putLong(cacheTimeKey, now)
                parse360Videos(fallbackBody, definition).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }

        return parse360Videos(cached, definition)
    }

    private fun fetch360Rank(cat: Int): String? {
        val url = "https://api.web.360kan.com/v1/rank?cat=$cat"
        return runCatching {
            OkHttpHelper.getWithClient(url, headers).use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    private fun parse360Videos(raw: String, definition: HomeRankDefinition): List<Movie.Video> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val json = unwrapJsonp(raw)
            val root = JsonParser.parseString(json).asJsonObject
            val array = root.getAsJsonArray("data") ?: return@runCatching emptyList()
            val videos = mutableListOf<Movie.Video>()
            for (i in 0 until array.size()) {
                val obj = array.get(i).asJsonObject
                val title = obj.stringValue("title")
                if (title.isBlank()) continue
                val comment = obj.stringValue("comment")
                val upInfo = obj.stringValue("upinfo")
                val movieCat = obj.stringValue("moviecat")
                val description = obj.stringValue("description")
                videos += Movie.Video(
                    id = "${definition.key}_$i",
                    name = title,
                    pic = obj.stringValue("cover").ifBlank { null },
                    note = comment.ifBlank { null },
                    type = movieCat.ifBlank { null },
                    state = upInfo.ifBlank { null },
                    des = description.ifBlank { null },
                    tag = "rank_${definition.key}"
                )
            }
            videos
        }.getOrDefault(emptyList())
    }

    private fun loadDoubanHot(): List<Movie.Video> {
        val cached = PrefsManager.getString(DOUBAN_CACHE_KEY)
        val cacheTime = PrefsManager.getLong(DOUBAN_CACHE_TIME)
        val now = System.currentTimeMillis()
        if (cached.isNotBlank() && now - cacheTime < CACHE_TTL_MS) {
            parseDoubanVideos(cached).takeIf { it.isNotEmpty() }?.let { return it }
        }

        val year = Calendar.getInstance().get(Calendar.YEAR)
        val url = "https://movie.douban.com/j/new_search_subjects" +
            "?sort=U&range=0,10&tags=&playable=1&start=0&year_range=$year,$year"
        val body = runCatching {
            OkHttpHelper.getWithClient(
                url,
                headers + ("Referer" to "https://www.douban.com/")
            ).use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()

        if (!body.isNullOrBlank()) {
            PrefsManager.putString(DOUBAN_CACHE_KEY, body)
            PrefsManager.putLong(DOUBAN_CACHE_TIME, now)
            parseDoubanVideos(body).takeIf { it.isNotEmpty() }?.let { return it }
        }

        return parseDoubanVideos(cached)
    }

    private fun parseDoubanVideos(json: String): List<Movie.Video> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            val array = root.getAsJsonArray("data") ?: return@runCatching emptyList()
            val videos = mutableListOf<Movie.Video>()
            val limit = minOf(array.size(), 25)
            for (i in 0 until limit) {
                val obj = array.get(i).asJsonObject
                val title = obj.stringValue("title")
                if (title.isBlank()) continue
                val rate = obj.stringValue("rate")
                videos += Movie.Video(
                    id = "douban_$i",
                    name = title,
                    pic = obj.stringValue("cover").ifBlank { null },
                    note = if (rate.isNotBlank()) rate else null,
                    year = obj.stringValue("year").ifBlank { null },
                    des = obj.stringValue("url").ifBlank { null },
                    tag = "rank_douban"
                )
            }
            videos
        }.getOrDefault(emptyList())
    }

    private fun unwrapJsonp(raw: String): String {
        val text = raw.trim()
        val open = text.indexOf('(')
        val close = text.lastIndexOf(')')
        return if (open > 0 && close > open && text.substring(0, open).all { it.isLetterOrDigit() || it == '_' || it == '$' }) {
            text.substring(open + 1, close)
        } else {
            text
        }
    }

    private fun JsonObject.stringValue(key: String): String {
        return try {
            get(key)?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
