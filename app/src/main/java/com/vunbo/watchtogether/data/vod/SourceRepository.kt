package com.vunbo.watchtogether.data.vod

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vunbo.watchtogether.data.source.ApiConfig
import com.vunbo.watchtogether.data.spider.SpiderClient
import com.vunbo.watchtogether.data.model.*
import com.vunbo.watchtogether.core.util.DefaultConfig
import com.vunbo.watchtogether.core.util.MD5
import com.vunbo.watchtogether.core.network.OkHttpHelper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class SourceRepository(private val apiConfig: ApiConfig) {

    companion object {
        private const val TAG = "SourceRepository"
    }

    private val gson = Gson()

    // Sort cache (LRU-like, max 5 entries)
    private val sortCache = object : LinkedHashMap<String, AbsSortXml>(5, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AbsSortXml>): Boolean {
            return size > 5
        }
    }

    // Extend cache
    private val extendCache = ConcurrentHashMap<String, String>()

    private val spiderClient = SpiderClient()

    // === State Flows ===

    private val _sortResult = MutableStateFlow<AbsSortXml?>(null)
    val sortResult: StateFlow<AbsSortXml?> = _sortResult.asStateFlow()

    private val _listResult = MutableStateFlow<AbsXml?>(null)
    val listResult: StateFlow<AbsXml?> = _listResult.asStateFlow()

    private val _detailResult = MutableStateFlow<AbsXml?>(null)
    val detailResult: StateFlow<AbsXml?> = _detailResult.asStateFlow()

    private val _playResult = MutableStateFlow<JsonObject?>(null)
    val playResult: StateFlow<JsonObject?> = _playResult.asStateFlow()

    private val _searchResult = MutableStateFlow<AbsXml?>(null)
    val searchResult: StateFlow<AbsXml?> = _searchResult.asStateFlow()

    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    // === Category / Sort ===

    suspend fun getSort(sourceKey: String) = withContext(Dispatchers.IO) {
        _loadingState.value = LoadingState.Loading

        // Check cache
        sortCache[sourceKey]?.let {
            _sortResult.value = it
            _loadingState.value = LoadingState.Success
            return@withContext
        }

        val source = apiConfig.getSource(sourceKey) ?: run {
            _loadingState.value = LoadingState.Error("Source not found")
            return@withContext
        }

        try {
            when (source.type) {
                0 -> getSortXml(source)
                1 -> getSortJson(source)
                3 -> getSortSpider(source)
                4 -> getSortRemote(source)
                else -> {
                    _loadingState.value = LoadingState.Error("Unknown source type: ${source.type}")
                }
            }
            _loadingState.value = LoadingState.Success
        } catch (e: Exception) {
            _loadingState.value = LoadingState.Error(e.message ?: "Unknown error")
        }
    }

    private fun getSortXml(source: SourceBean) {
        val body = OkHttpHelper.getBody("${source.api}?ac=list") ?: return
        val result = parseXmlToSort(body)
        sortCache[source.key] = result
        _sortResult.value = result
    }

    private fun getSortJson(source: SourceBean) {
        // JSON 类型站点使用 ac=videolist 获取分类。
        val body = OkHttpHelper.getBody("${source.api}?ac=videolist") ?: return
        try {
            val sortJson = gson.fromJson(body, AbsSortJson::class.java)
            val result = sortJson.toAbsSortXml()
            sortCache[source.key] = result
            _sortResult.value = result
        } catch (e: Exception) {
            // Try parsing flat JSON structure
            try {
                val absJson = gson.fromJson(body, AbsJson::class.java)
                val sortXml = AbsSortXml()
                sortXml.videoList = absJson.list.map { it.toXmlVideo() }.toMutableList()
                sortCache[source.key] = sortXml
                _sortResult.value = sortXml
            } catch (e2: Exception) {
                _loadingState.value = LoadingState.Error("JSON解析失败: ${e2.message}")
            }
        }
    }

    private suspend fun getSortSpider(source: SourceBean) {
        try {
            val json = spiderClient.homeContent(source, true)
            val result = if (json.isNotEmpty() && json != "{}") parseSpiderHome(json, source.api) else AbsSortXml()
            sortCache[source.key] = result
            _sortResult.value = result
        } catch (e: Exception) {
            _loadingState.value = LoadingState.Error("Spider timeout: ${e.message}")
        }
    }

    /** 解析 Spider.homeContent() 返回的 JSON */
    private fun parseSpiderHome(json: String, baseUrl: String? = null): AbsSortXml {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val sortXml = AbsSortXml()
            val classes = MovieSort()
            root.getAsJsonArray("class")?.forEach { cls ->
                val obj = cls.asJsonObject
                val id = obj.get("type_id")?.asString.orEmpty()
                val name = obj.get("type_name")?.asString.orEmpty()
                if (id.isNotBlank() && name.isNotBlank()) {
                    classes.sortList.add(MovieSort.SortData(id = id, name = name))
                }
            }
            sortXml.classes = classes
            // 首页推荐视频
            root.getAsJsonArray("list")?.let { list ->
                sortXml.videoList = mutableListOf()
                for (i in 0 until list.size()) {
                    val v = list.get(i).asJsonObject
                    sortXml.videoList!!.add(Movie.Video(
                        id = v.get("vod_id")?.asString,
                        name = v.get("vod_name")?.asString,
                        pic = pickPoster(v, baseUrl),
                        note = v.get("vod_remarks")?.asString
                    ))
                }
            }
            sortXml
        } catch (e: Exception) {
            AbsSortXml()
        }
    }

    private fun getSortRemote(source: SourceBean) {
        val body = OkHttpHelper.getBody(source.api) ?: return
        try {
            val sortJson = gson.fromJson(body, AbsSortJson::class.java)
            _sortResult.value = sortJson.toAbsSortXml()
        } catch (e: Exception) {
            _loadingState.value = LoadingState.Error("Remote parse error")
        }
    }

    // === Video List ===

    suspend fun getList(sortData: MovieSort.SortData, page: Int) = withContext(Dispatchers.IO) {
        _loadingState.value = LoadingState.Loading
        val source = apiConfig.homeSource ?: return@withContext
        if (sortData.id.isBlank()) {
            _listResult.value = AbsXml()
            _loadingState.value = LoadingState.Success
            return@withContext
        }

        try {
            val result = when (source.type) {
                3 -> {
                    val json = spiderClient.categoryContent(source, sortData.id, page.toString(), true, sortData.filterSelect)
                    if (json.isNotEmpty() && json != "{}") parseSpiderList(json, source.api) else AbsXml()
                }
                else -> {
                    val url = buildListUrl(source, sortData, page)
                    val body = OkHttpHelper.getBody(url) ?: return@withContext
                    when (source.type) {
                        1 -> parseJsonToList(body)
                        else -> parseXmlToList(body)
                    }
                }
            }
            result.movie?.page = page
            _listResult.value = result
            _loadingState.value = LoadingState.Success
        } catch (e: Exception) {
            _loadingState.value = LoadingState.Error(e.message ?: "Load list error")
        }
    }

    private fun buildListUrl(source: SourceBean, sortData: MovieSort.SortData, page: Int): String {
        val base = source.api
        val params = mutableListOf<String>()

        when (source.type) {
            0 -> {
                params.add("ac=videolist")
                params.add("t=${sortData.id}")
                params.add("pg=$page")
            }
            1 -> {
                params.add("ac=videolist")
                params.add("t=${sortData.id}")
                params.add("pg=$page")
            }
        }

        sortData.filterSelect.forEach { (k, v) ->
            if (v.isNotEmpty()) params.add("$k=$v")
        }

        return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
    }

    // === Detail ===

    suspend fun getDetail(sourceKey: String, vodId: String) = withContext(Dispatchers.IO) {
        _loadingState.value = LoadingState.Loading
        val source = apiConfig.getSource(sourceKey) ?: return@withContext

        try {
            _detailResult.value = loadDetail(source, vodId)
            _loadingState.value = LoadingState.Success
        } catch (e: Exception) {
            _loadingState.value = LoadingState.Error(e.message ?: "Detail error")
        }
    }

    suspend fun getDetailOnce(sourceKey: String, vodId: String): AbsXml? = withContext(Dispatchers.IO) {
        val source = apiConfig.getSource(sourceKey) ?: return@withContext null
        try {
            loadDetail(source, vodId)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun loadDetail(source: SourceBean, vodId: String): AbsXml {
        return when (source.type) {
            1 -> {
                val body = OkHttpHelper.getBody("${source.api}?ac=detail&ids=$vodId")
                if (body != null) {
                    val absJson = gson.fromJson(body, AbsJson::class.java)
                    val vod = absJson.list.firstOrNull()?.toXmlVideo()
                    AbsXml().apply {
                        if (vod != null) movie = Movie().apply { videoList.add(vod) }
                    }
                } else {
                    AbsXml()
                }
            }
            3 -> {
                val json = spiderClient.detailContent(source, listOf(vodId))
                if (json.isNotEmpty() && json != "{}") parseSpiderDetail(json, source.api) else AbsXml()
            }
            else -> {
                val body = OkHttpHelper.getBody("${source.api}?ac=videolist&ids=$vodId")
                parseXmlToList(body)
            }
        }
    }

    // === Search ===

    suspend fun getSearch(sourceKey: String, keyword: String) = withContext(Dispatchers.IO) {
        val source = apiConfig.getSource(sourceKey) ?: return@withContext

        try {
            val encoded = java.net.URLEncoder.encode(keyword, "UTF-8")
            when (source.type) {
                1 -> {
                    val body = OkHttpHelper.getBody("${source.api}?ac=videolist&wd=$encoded")
                    if (body != null) {
                        val result = parseJsonToList(body)
                        result.movie?.videoList?.forEach { it.sourceKey = source.key }
                        _searchResult.value = result
                    }
                }
                3 -> {
                    val json = spiderClient.searchContent(source, keyword, false)
                    if (json.isNotEmpty() && json != "{}") {
                        val result = parseSpiderList(json, source.api)
                        result.movie?.videoList?.forEach { it.sourceKey = source.key }
                        _searchResult.value = result
                    }
                }
                else -> {
                    val body = OkHttpHelper.getBody("${source.api}?ac=videolist&wd=$encoded")
                    if (body != null) {
                        val result = parseXmlToList(body)
                        result.movie?.videoList?.forEach { it.sourceKey = source.key }
                        _searchResult.value = result
                    }
                }
            }
        } catch (e: Exception) {
            // Search errors are non-critical
        }
    }

    // === Play URL ===

    suspend fun getPlay(
        sourceKey: String,
        playFlag: String,
        progressKey: String,
        url: String,
        subtitleKey: String? = null
    ) = withContext(Dispatchers.IO) {
        _playResult.value = resolvePlay(sourceKey, playFlag, url)
    }

    suspend fun resolvePlay(
        sourceKey: String,
        playFlag: String,
        url: String,
        preferredParserName: String? = null
    ): JsonObject? = withContext(Dispatchers.IO) {
        val source = apiConfig.getSource(sourceKey)
        val result = JsonObject()

        if (source == null) {
            result.addProperty("parse", 0)
            result.addProperty("url", url)
            return@withContext result
        }

        try {
            val resolved = when (source.type) {
                3 -> {
                    try {
                        val flags = apiConfig.vipParseFlags.toList()
                        val playerJson = spiderClient.playerContent(source, playFlag, url, flags)
                        Log.d(TAG, "Spider playerContent flag=$playFlag input=${maskUrl(url)} result=${maskUrl(playerJson)}")
                        if (playerJson.isNotEmpty() && playerJson != "{}") {
                            try {
                                JsonParser.parseString(playerJson).asJsonObject
                            } catch (e: Exception) {
                                JsonObject().apply {
                                    addProperty("parse", 0)
                                    addProperty("url", url)
                                    addProperty("flag", playFlag)
                                }
                            }
                        } else {
                            JsonObject().apply {
                                addProperty("parse", 0)
                                addProperty("url", url)
                                addProperty("flag", playFlag)
                            }
                        }
                    } catch (e: Exception) {
                        result.addProperty("parse", 1)
                        result.addProperty("playUrl", source.playerUrl ?: "")
                        result.addProperty("url", url)
                        result.addProperty("flag", playFlag)
                        result
                    }
                }
                0, 1 -> {
                    if (isPlayableUrl(url)) {
                        result.addProperty("parse", 0)
                        result.addProperty("url", url)
                    } else {
                        result.addProperty("parse", 1)
                        result.addProperty("playUrl", source.playerUrl ?: "")
                        result.addProperty("url", url)
                    }
                    result.addProperty("flag", playFlag)
                    result
                }
                4 -> {
                    // Remote TVBox
                    val body = OkHttpHelper.getBody("${source.api}?play=${java.net.URLEncoder.encode(url, "UTF-8")}&flag=$playFlag")
                    if (body != null) {
                        JsonParser.parseString(body).asJsonObject
                    } else {
                        result.apply {
                            addProperty("parse", 0)
                            addProperty("url", url)
                            addProperty("flag", playFlag)
                        }
                    }
                }
                else -> result
            }

            val resolvedUrl = DefaultConfig.safeJsonString(resolved, "url")
            val parse = try { resolved.get("parse")?.asInt ?: 0 } catch (e: Exception) { 0 }
            val errorMessage = DefaultConfig.safeJsonString(resolved, "errMsg")
                .ifBlank { DefaultConfig.safeJsonString(resolved, "msg") }
            val finalResult = if (parse == 0 && isPlayableUrl(resolvedUrl)) {
                resolved
            } else {
                parseVipUrl(playFlag, resolvedUrl.ifBlank { url }, preferredParserName)
                    ?: buildUnplayableResult(resolvedUrl.ifBlank { url }, playFlag, errorMessage)
            }
            Log.d(TAG, "resolvePlay flag=$playFlag input=${maskUrl(url)} parse=$parse resolved=${maskUrl(DefaultConfig.safeJsonString(finalResult, "url"))}")
            finalResult
        } catch (e: Exception) {
            result.addProperty("parse", 1)
            result.addProperty("url", url)
            result
        }
    }

    private fun isPlayableUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (lower.contains("iqiyi.com/") || lower.contains("v.qq.com/") ||
            lower.contains("youku.com/") || lower.contains("mgtv.com/") ||
            lower.contains("bilibili.com/video") || lower.contains("sohu.com/")) {
            return false
        }
        return DefaultConfig.isVideoFormat(url)
    }

    private fun buildUnplayableResult(url: String, playFlag: String, message: String = ""): JsonObject {
        return JsonObject().apply {
            addProperty("parse", 1)
            addProperty("url", url)
            addProperty("flag", playFlag)
            addProperty("error", "未解析到可播放地址")
        }
    }

    private fun maskUrl(value: String): String {
        return if (value.length > 220) value.take(180) + "..." else value
    }

    private fun parseVipUrl(
        playFlag: String,
        pageUrl: String,
        preferredParserName: String? = null
    ): JsonObject? {
        if (pageUrl.isBlank() || isPlayableUrl(pageUrl)) {
            return buildPlayResult(pageUrl)
        }

        val parsers = apiConfig.parseBeanList
            .filter { it.url.isNotBlank() && it.url != "Web" && it.url != "Demo" }
            .sortedWith(compareByDescending<ParseBean> { parserSupportsFlag(it, playFlag) }
                .thenBy { if (it.type == 0) 0 else 1 }
                .thenByDescending { parserHasExplicitFlag(it) })
            .let { sorted ->
                val preferred = preferredParserName?.takeIf { it.isNotBlank() } ?: return@let sorted
                sorted.sortedBy { if (it.name == preferred) 0 else 1 }
            }

        for (parser in parsers) {
            val headers = parserHeaders(parser)
            val parsed = when (parser.type) {
                1 -> requestJsonParser(parser, pageUrl, headers)
                0 -> sniffWebParser(parser, pageUrl, headers)
                else -> null
            }
            if (parsed != null) {
                parsed.addProperty("jxFrom", parser.name.ifBlank { parser.url })
                return parsed
            }
        }

        return null
    }

    private fun requestJsonParser(parser: ParseBean, pageUrl: String, headers: Map<String, String>): JsonObject? {
        val urls = listOf(parser.url + pageUrl, parser.url + URLEncoder.encode(pageUrl, "UTF-8")).distinct()
        for (requestUrl in urls) {
            val body = OkHttpHelper.getBodyForParser(requestUrl, headers) ?: continue
            val parsed = parseJsonParserBody(body, headers)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseJsonParserBody(body: String, fallbackHeaders: Map<String, String>): JsonObject? {
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val candidateUrls = mutableListOf<String>()
            candidateUrls += DefaultConfig.safeJsonString(root, "url")
            candidateUrls += DefaultConfig.safeJsonString(root, "playUrl")
            safeGetObject(root, "data")?.let { data ->
                candidateUrls += DefaultConfig.safeJsonString(data, "url")
                candidateUrls += DefaultConfig.safeJsonString(data, "playUrl")
            }

            val mediaUrl = candidateUrls.firstOrNull { isPlayableUrl(it) } ?: return null
            buildPlayResult(mediaUrl, extractHeaders(root).ifEmpty { fallbackHeaders })
        } catch (e: Exception) {
            null
        }
    }

    private fun sniffWebParser(parser: ParseBean, pageUrl: String, headers: Map<String, String>): JsonObject? {
        val urls = listOf(parser.url + pageUrl, parser.url + URLEncoder.encode(pageUrl, "UTF-8")).distinct()
        for (requestUrl in urls) {
            val body = OkHttpHelper.getBodyForParser(requestUrl, headers) ?: continue
            val mediaUrl = findMediaUrl(body)
            if (mediaUrl != null) return buildPlayResult(mediaUrl, headers)
        }
        return null
    }

    private fun findMediaUrl(text: String): String? {
        val unescaped = text
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
        val regex = Regex("""https?://[^"'\\<>\s]+?\.(?:m3u8|mp4|flv)(?:\?[^"'\\<>\s]*)?""", RegexOption.IGNORE_CASE)
        return regex.find(unescaped)?.value?.takeIf { isPlayableUrl(it) }
    }

    private fun buildPlayResult(url: String, headers: Map<String, String> = emptyMap()): JsonObject {
        return JsonObject().apply {
            addProperty("parse", 0)
            addProperty("url", url)
            if (headers.isNotEmpty()) {
                val headerObj = JsonObject()
                headers.forEach { (key, value) -> headerObj.addProperty(key, value) }
                add("header", headerObj)
            }
        }
    }

    private fun parserSupportsFlag(parser: ParseBean, playFlag: String): Boolean {
        val flags = parserFlags(parser)
        return flags.isEmpty() || flags.any { it.equals(playFlag, ignoreCase = true) }
    }

    private fun parserHasExplicitFlag(parser: ParseBean): Boolean {
        return parserFlags(parser).isNotEmpty()
    }

    private fun parserFlags(parser: ParseBean): List<String> {
        val ext = parser.ext?.trim().orEmpty()
        if (!ext.startsWith("{")) return emptyList()
        return try {
            val obj = JsonParser.parseString(ext).asJsonObject
            safeGetArray(obj, "flag")?.map { it.asString } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parserHeaders(parser: ParseBean): Map<String, String> {
        val ext = parser.ext?.trim().orEmpty()
        if (!ext.startsWith("{")) return emptyMap()
        return try {
            val obj = JsonParser.parseString(ext).asJsonObject
            extractHeaders(obj)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun extractHeaders(obj: JsonObject): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers.putAll(parseHeaderElement(obj.get("header")))
        headers.putAll(parseHeaderElement(obj.get("headers")))
        obj.get("user-agent")?.asStringOrNull()?.takeIf { it.isNotBlank() }?.let {
            headers["User-Agent"] = it.trim()
        }
        obj.get("referer")?.asStringOrNull()?.takeIf { it.isNotBlank() }?.let {
            headers["Referer"] = it.trim()
        }
        return headers
    }

    private fun parseHeaderElement(element: JsonElement?): Map<String, String> {
        if (element == null || element.isJsonNull) return emptyMap()
        return when {
            element.isJsonObject -> element.asJsonObject.entrySet().mapNotNull { (key, value) ->
                value.asStringOrNull()?.takeIf { it.isNotBlank() }?.let { key to it.trim() }
            }.toMap()
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> parseHeaderString(element.asString)
            else -> emptyMap()
        }
    }

    private fun parseHeaderString(raw: String): Map<String, String> {
        val text = raw.trim()
        if (text.isBlank()) return emptyMap()
        if (text.startsWith("{") && text.endsWith("}")) {
            return runCatching {
                parseHeaderElement(JsonParser.parseString(text))
            }.getOrDefault(emptyMap())
        }
        return text.split("&", "\n", ";")
            .mapNotNull { item ->
                val index = item.indexOf('=').takeIf { it > 0 } ?: item.indexOf(':').takeIf { it > 0 }
                if (index == null) {
                    null
                } else {
                    val key = item.substring(0, index).trim()
                    val value = item.substring(index + 1).trim()
                    if (key.isNotBlank() && value.isNotBlank()) key to value else null
                }
            }
            .toMap()
    }

    private fun JsonElement.asStringOrNull(): String? {
        return runCatching {
            if (isJsonPrimitive) asString else null
        }.getOrNull()
    }

    // === Parsing Helpers ===

    private fun parseXmlToList(xml: String?): AbsXml {
        // Simplified XML parsing
        // In production, use XStream with Movie annotations
        val absXml = AbsXml()
        try {
            val json = JsonParser.parseString(xml ?: "{}").asJsonObject
            val movie = Movie()
            json.getAsJsonObject("list")?.let { listObj ->
                movie.page = listObj.get("page")?.asInt ?: 1
                movie.pagecount = listObj.get("pagecount")?.asInt ?: 0
                movie.pagesize = listObj.get("pagesize")?.asInt ?: 20
                movie.recordcount = listObj.get("recordcount")?.asInt ?: 0

                listObj.getAsJsonArray("video")?.forEach { videoEl ->
                    val v = videoEl.asJsonObject
                    val video = Movie.Video(
                        id = v.get("id")?.asString,
                        tid = v.get("tid")?.asString,
                        name = v.get("name")?.asString,
                        type = v.get("type")?.asString,
                        pic = normalizeMediaUrl(v.get("pic")?.asString),
                        lang = v.get("lang")?.asString,
                        area = v.get("area")?.asString,
                        year = v.get("year")?.asString,
                        state = v.get("state")?.asString,
                        note = v.get("note")?.asString,
                        actor = v.get("actor")?.asString,
                        director = v.get("director")?.asString,
                        des = v.get("des")?.asString,
                        last = v.get("last")?.asString
                    )
                    movie.videoList.add(video)
                }
            }
            absXml.movie = movie
        } catch (e: Exception) {
            absXml.msg = e.message
        }
        return absXml
    }

    private fun parseXmlToSort(xml: String?): AbsSortXml {
        // Simplified - parse XML to sort structure
        return AbsSortXml()
    }

    private fun parseJsonToList(json: String?): AbsXml {
        if (json == null) return AbsXml()
        return try {
            val absJson = gson.fromJson(json, AbsJson::class.java)
            val absXml = AbsXml()
            absXml.movie = Movie().apply {
                page = absJson.page
                pagecount = absJson.pagecount
                pagesize = absJson.limit
                recordcount = absJson.total
                videoList = absJson.list.map { it.toXmlVideo() }.toMutableList()
            }
            absXml
        } catch (e: Exception) {
            AbsXml()
        }
    }

    // === Spider 响应解析 ===

    /** 解析 Spider 列表响应（searchContent/categoryContent） */
    private fun parseSpiderList(json: String, baseUrl: String? = null): AbsXml {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val absXml = AbsXml()
            val movie = Movie()
            movie.page = root.get("page")?.asInt ?: 1
            movie.pagecount = root.get("pagecount")?.asInt ?: 0
            movie.recordcount = root.get("total")?.asInt ?: 0
            root.getAsJsonArray("list")?.forEach { v ->
                val obj = v.asJsonObject
                movie.videoList.add(Movie.Video(
                    id = obj.get("vod_id")?.asString,
                    name = obj.get("vod_name")?.asString,
                    pic = pickPoster(obj, baseUrl),
                    note = obj.get("vod_remarks")?.asString,
                    type = obj.get("type_name")?.asString,
                    year = obj.get("vod_year")?.asString,
                    area = obj.get("vod_area")?.asString,
                    actor = obj.get("vod_actor")?.asString,
                    director = obj.get("vod_director")?.asString,
                    des = obj.get("vod_content")?.asString
                ))
            }
            absXml.movie = movie
            absXml
        } catch (e: Exception) { AbsXml() }
    }

    /** 解析 Spider 详情响应（detailContent） */
    private fun parseSpiderDetail(json: String, baseUrl: String? = null): AbsXml {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val absXml = AbsXml()
            val movie = Movie()
            root.getAsJsonArray("list")?.let { list ->
                if (list.size() > 0) {
                    val obj = list.get(0).asJsonObject
                    val video = Movie.Video(
                        id = obj.get("vod_id")?.asString,
                        name = obj.get("vod_name")?.asString,
                        pic = pickPoster(obj, baseUrl),
                        type = obj.get("type_name")?.asString,
                        year = obj.get("vod_year")?.asString,
                        area = obj.get("vod_area")?.asString,
                        actor = obj.get("vod_actor")?.asString,
                        director = obj.get("vod_director")?.asString,
                        des = obj.get("vod_content")?.asString,
                        note = obj.get("vod_remarks")?.asString
                    )
                    // 解析播放源（vod_play_from + vod_play_url）
                    val playFrom = obj.get("vod_play_from")?.asString
                    val playUrl = obj.get("vod_play_url")?.asString
                    if (playFrom != null && playUrl != null) {
                        val urlBean = Movie.UrlBean()
                        urlBean.urlInfoList = parseSpiderPlayUrls(playFrom, playUrl)
                        video.urlBean = urlBean
                    }
                    movie.videoList.add(video)
                }
            }
            absXml.movie = movie
            absXml
        } catch (e: Exception) { AbsXml() }
    }

    private fun pickPoster(obj: JsonObject, baseUrl: String? = null): String? {
        val keys = listOf("vod_pic", "pic", "vod_pic_thumb", "vod_pic_slide", "cover", "image", "img", "thumb")
        return keys.asSequence()
            .mapNotNull { key ->
                obj.get(key)
                    ?.takeIf { !it.isJsonNull }
                    ?.asString
                    ?.trim()
                    ?.takeIf { value -> value.isNotBlank() }
            }
            .map { normalizeMediaUrl(it, baseUrl) }
            .firstOrNull { !it.isNullOrBlank() }
    }

    private fun normalizeMediaUrl(rawUrl: String?, baseUrl: String? = null): String? {
        val trimmed = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val fixedProtocol = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("https:/") && !trimmed.startsWith("https://") ->
                trimmed.replaceFirst("https:/", "https://")
            trimmed.startsWith("http:/") && !trimmed.startsWith("http://") ->
                trimmed.replaceFirst("http:/", "http://")
            else -> trimmed
        }
        if (
            fixedProtocol.startsWith("http://") ||
            fixedProtocol.startsWith("https://") ||
            fixedProtocol.startsWith("data:")
        ) {
            return fixedProtocol
        }
        val base = baseUrl?.trim()?.takeIf { it.isNotBlank() } ?: return fixedProtocol
        return try {
            URI(base).resolve(fixedProtocol).toString()
        } catch (_: Exception) {
            fixedProtocol
        }
    }

private fun parseSpiderPlayUrls(playFrom: String, playUrl: String): MutableList<Movie.UrlInfo> {
        val result = mutableListOf<Movie.UrlInfo>()
        val fromArr = playFrom.split("$$$")
        val urlArr = playUrl.split("$$$")
        for (i in fromArr.indices) {
            val info = Movie.UrlInfo()
            info.flag = fromArr[i]
            info.urls = urlArr.getOrElse(i) { urlArr.lastOrNull() ?: "" }
            info.beanList = info.urls!!.split("#").mapNotNull { part ->
                val kv = part.split("$", limit = 2)
                if (kv.size >= 2) Movie.InfoBean(kv[0], kv[1]) else null
            }.toMutableList()
            result.add(info)
        }
        return result
    }

    // === Reset ===

    fun clearCache() {
        sortCache.clear()
        extendCache.clear()
    }

    private fun safeGetArray(obj: JsonObject, key: String) = try {
        obj.getAsJsonArray(key)
    } catch (e: Exception) {
        null
    }

    private fun safeGetObject(obj: JsonObject, key: String) = try {
        obj.getAsJsonObject(key)
    } catch (e: Exception) {
        null
    }
}

sealed class LoadingState {
    object Idle : LoadingState()
    object Loading : LoadingState()
    object Success : LoadingState()
    data class Error(val message: String) : LoadingState()
}


