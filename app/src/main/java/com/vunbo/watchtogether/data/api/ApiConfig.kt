package com.vunbo.watchtogether.data.api

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vunbo.watchtogether.WatchTogetherApp
import com.vunbo.watchtogether.data.model.*
import com.vunbo.watchtogether.data.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

class ApiConfig private constructor() {

    companion object {
        @Volatile
        private var instance: ApiConfig? = null

        fun get(): ApiConfig {
            return instance ?: synchronized(this) {
                instance ?: ApiConfig().also { instance = it }
            }
        }
    }

    private val context: Context get() = WatchTogetherApp.instance

    // Source beans (all loaded sources, keyed by source key)
    val sourceBeanList = linkedMapOf<String, SourceBean>()
    var homeSource: SourceBean? = null
    var defaultParse: ParseBean? = null

    // Live
    val liveChannelGroupList = mutableListOf<LiveChannelGroup>()

    // Parsers
    val parseBeanList = mutableListOf<ParseBean>()
    val vipParseFlags = mutableListOf<String>()

    // Hosts mapping
    val hostsMap = mutableMapOf<String, String>()

    // IJK codecs
    val ijkCodes = mutableListOf<IJKCode>()

    // Ad blocking
    val adHosts = mutableListOf<String>()

    // Spider
    var spiderUrl: String? = null

    // Video sniffing rules
    val videoParseRules = mutableListOf<VideoParseRule>()

    // Wallpaper
    var wallpaperUrl: String? = null

    // DOH
    var dohUrl: String? = null

    // Spider JAR 加载器
    val jarLoader = JarLoader()

    private val loadMutex = Mutex()
    private var loadedConfigUrl: String = ""

    data class VideoParseRule(
        val host: String? = null,
        val rule: String? = null,
        val filter: String? = null,
        val hosts: List<String>? = null,
        val regex: String? = null,
        val script: String? = null
    )

    // 用于解密响应体的密钥（从 URL 的 ;pk; 中提取）
    private var tempKey: String? = null
    private var lastConfigUrl: String = ""

    /**
     * URL 预处理，与 TVBoxOS 的 configUrl() 一致：
     * - 没有协议的自动加 http://
     * - 提取 ;pk; 分隔的 TempKey
     * - 处理 clan:// 协议
     */
    private fun configUrl(apiUrl: String): String {
        tempKey = null
        var url = apiUrl.replace("file://", "clan://localhost/")

        if (url.contains(";pk;")) {
            val parts = url.split(";pk;")
            if (parts.size >= 2) {
                tempKey = parts[1]
                url = if (url.startsWith("clan")) {
                    // clan:// 协议暂不做地址转换，保持原样
                    parts[0]
                } else if (url.startsWith("http")) {
                    parts[0]
                } else {
                    "http://${parts[0]}"
                }
            }
        } else if (!url.startsWith("http") && !url.startsWith("clan")) {
            url = "http://$url"
        }
        return url
    }

    /**
     * 修复响应中相对路径（./xxx 转绝对路径）
     */
    private fun fixContentPath(url: String, content: String): String {
        if (!content.contains("\"./")) return content
        var baseUrl = url.replace("file://", "clan://localhost/")
        if (!baseUrl.startsWith("http") && !baseUrl.startsWith("clan://")) {
            baseUrl = "http://$baseUrl"
        }
        val basePath = baseUrl.substring(0, baseUrl.lastIndexOf("/") + 1)
        return content.replace("./", basePath)
    }

    /** 响应体解密处理，与 TVBoxOS FindResult 一致 */
    private fun findResult(content: String): String? {
        var data = content.trim().removePrefix("\uFEFF")

        // 如果已经是 JSON，直接返回
        if (AES.isJson(data)) return data

        // Base64 with prefix: "[8 chars]**base64" (注意：是 [A-Za-z0]{8} 不是 [A-Za-z0-9]{8})
        val base64Pattern = Regex("[A-Za-z0]{8}\\*\\*")
        val matcher = base64Pattern.find(data)
        if (matcher != null) {
            data = try {
                val body = data.substring(data.indexOf(matcher.value) + 10)
                String(android.util.Base64.decode(body, android.util.Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                return null
            }
        }

        if (data.startsWith("2423")) {
            val markerIndex = data.indexOf("2324")
            if (markerIndex < 0 || data.length <= 26) return null

            val encrypted = data.substring(markerIndex + 4, data.length - 26)
            val hexText = try {
                String(AES.toBytes(data), Charset.defaultCharset()).lowercase()
            } catch (e: Exception) {
                return null
            }
            val keyStart = hexText.indexOf("$#")
            val keyEnd = hexText.indexOf("#$")
            if (keyStart < 0 || keyEnd <= keyStart) return null

            val key = AES.rightPadding(hexText.substring(keyStart + 2, keyEnd), "0", 16)
            val iv = AES.rightPadding(hexText.substring(hexText.length - 13), "0", 16)
            return AES.CBC(encrypted, key, iv)
        }

        val configKey = tempKey
        if (!configKey.isNullOrEmpty() && !AES.isJson(data)) {
            return AES.ECB(data, configKey)
        }

        return data
    }

    fun clearConfigState() {
        sourceBeanList.clear()
        homeSource = null
        defaultParse = null
        liveChannelGroupList.clear()
        parseBeanList.clear()
        vipParseFlags.clear()
        hostsMap.clear()
        ijkCodes.clear()
        adHosts.clear()
        spiderUrl = null
        videoParseRules.clear()
        wallpaperUrl = null
        dohUrl = null
        jarLoader.clear()
        loadedConfigUrl = ""
    }

    // === Config Loading ===

    suspend fun loadConfig(useCache: Boolean = false, forceReload: Boolean = false): Boolean = loadMutex.withLock {
        loadConfigLocked(useCache, forceReload)
    }

    private suspend fun loadConfigLocked(useCache: Boolean = false, forceReload: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiUrl = PrefsManager.getString(HawkConfig.API_URL)
            if (apiUrl.isEmpty()) return@withContext false

            // 预处理 URL
            val requestUrl = configUrl(apiUrl)
            lastConfigUrl = requestUrl

            if (!forceReload && loadedConfigUrl == requestUrl && sourceBeanList.isNotEmpty()) {
                return@withContext true
            }

            // 保存 config_url 供 JAR 相对路径解析
            File(context.filesDir, "config_url").writeText(requestUrl)

            val cacheFile = File(context.filesDir, "cache_${MD5.encode(requestUrl)}")

            if (useCache && cacheFile.exists()) {
                parseJson(requestUrl, cacheFile.readText())
            } else {
                val response = OkHttpHelper.getConfigBody(requestUrl) ?: run {
                    // 网络请求失败，尝试从缓存加载
                    if (cacheFile.exists()) {
                        android.util.Log.w("ApiConfig", "配置网络加载失败，使用本地缓存: $requestUrl")
                        parseJson(requestUrl, cacheFile.readText())
                    } else {
                        return@withContext false
                    }
                    null
                }
                if (response != null) {
                    // 解密/解码响应体
                    val result = findResult(response)
                        ?: return@withContext false

                    // 修复相对路径
                    val fixed = fixContentPath(requestUrl, result)
                    parseJson(requestUrl, fixed)
                    cacheFile.writeText(fixed)
                }
            }

            // 解析完配置后立即加载 JAR（在 IO 线程内）
            val spider = spiderUrl
            if (!spider.isNullOrEmpty()) {
                android.util.Log.i("ApiConfig", "开始加载 JAR: $spider (base=$requestUrl)")
                val jarOk = loadMainJarInternal(spider)
                android.util.Log.i("ApiConfig", "JAR 加载结果: $jarOk")
            }
            loadedConfigUrl = requestUrl
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun parseJson(apiUrl: String, jsonStr: String) {
        clearConfigState()
        val root = JsonParser.parseString(jsonStr).asJsonObject

        // Spider
        spiderUrl = DefaultConfig.safeJsonString(root, "spider").ifEmpty { null }

        // Wallpaper
        wallpaperUrl = DefaultConfig.safeJsonString(root, "wallpaper").ifEmpty { null }

            // Sites - 每个站点单独 try，一个站点解析失败不影响其他
            val sitesArray = safeGetArray(root, "sites")
            if (sitesArray != null) {
                for (i in 0 until sitesArray.size()) {
                    try {
                        val site = sitesArray.get(i).asJsonObject
                        val source = SourceBean(
                            key = DefaultConfig.safeJsonString(site, "key"),
                            name = DefaultConfig.safeJsonString(site, "name"),
                            api = DefaultConfig.safeJsonString(site, "api"),
                            type = DefaultConfig.safeJsonInt(site, "type", 1),
                            searchable = DefaultConfig.safeJsonInt(site, "searchable", 1),
                            quickSearch = DefaultConfig.safeJsonInt(site, "quickSearch", 1),
                            filterable = if (DefaultConfig.safeJsonString(site, "key").startsWith("py_")) {
                                1
                            } else {
                                DefaultConfig.safeJsonInt(site, "filterable", 1)
                            },
                            changeable = DefaultConfig.safeJsonInt(site, "changeable", 1),
                            indexs = DefaultConfig.safeJsonInt(site, "indexs", 1),
                            timeout = DefaultConfig.safeJsonInt(site, "timeout"),
                            playerUrl = DefaultConfig.safeJsonString(site, "playUrl").ifEmpty { null },
                            ext = DefaultConfig.safeJsonString(site, "ext").ifEmpty { null },
                            jar = DefaultConfig.safeJsonString(site, "jar").ifEmpty { null },
                            categories = DefaultConfig.safeJsonString(site, "categories").ifEmpty { null },
                            playerType = DefaultConfig.safeJsonString(site, "playerType").ifEmpty { null },
                            clickSelector = DefaultConfig.safeJsonString(site, "click").ifEmpty { null },
                            style = DefaultConfig.safeJsonString(site, "style").ifEmpty { null }
                        )
                        if (source.key.isNotEmpty()) {
                            sourceBeanList[source.key] = source
                        }
                    } catch (e: Exception) {
                        // 单个站点解析失败，跳过继续
                        e.printStackTrace()
                    }
                }
            }

            // 如果没有 sites 但有 class（直接站点 API），创建一个默认源
            if (sourceBeanList.isEmpty() && root.has("class")) {
                val defaultSource = SourceBean(
                    key = "default",
                    name = "默认站点",
                    api = apiUrl,
                    type = 1,
                    searchable = 1,
                    filterable = 1
                )
                sourceBeanList["default"] = defaultSource
            }

            // Set home source
            val savedHome = PrefsManager.getString(HawkConfig.HOME_API)
            val savedSource = sourceBeanList[savedHome]
            homeSource = if (savedSource != null && savedSource.isHomeUsable()) {
                savedSource
            } else {
                chooseDefaultHomeSource()
                    ?: savedSource
                    ?: sourceBeanList.values.firstOrNull()
            }
            homeSource?.let { PrefsManager.putString(HawkConfig.HOME_API, it.key) }

            // Parses
            safeGetArray(root, "parses")?.forEach { parseEl ->
                val parse = parseEl.asJsonObject
                parseBeanList.add(ParseBean(
                    name = DefaultConfig.safeJsonString(parse, "name"),
                    url = DefaultConfig.safeJsonString(parse, "url"),
                    ext = DefaultConfig.safeJsonString(parse, "ext").ifEmpty { null },
                    type = DefaultConfig.safeJsonInt(parse, "type")
                ))
            }

            // Add super parse (type 4) at position 0
            if (parseBeanList.none { it.type == 4 }) {
                parseBeanList.add(0, ParseBean(name = "超级解析", type = 4))
            }

            // Flags
            safeGetArray(root, "flags")?.forEach {
                vipParseFlags.add(it.asString)
            }

            // Live channels - 支持两种格式
            safeGetArray(root, "lives")?.forEach { liveEl ->
                try {
                    val live = liveEl.asJsonObject
                    val hasChannels = live.has("channels")
                    val hasGroup = live.has("group")

                    if (hasChannels && hasGroup) {
                        // 格式1: {"group":"...", "channels":[...]}
                        val groupName = DefaultConfig.safeJsonString(live, "group", "默认")
                        parseLiveChannels(live, groupName)
                    } else if (hasChannels) {
                        // 格式2: {"name":"...", "channels":[...]}
                        val groupName = DefaultConfig.safeJsonString(live, "name", "默认")
                        parseLiveChannels(live, groupName)
                    }
                    // 格式3: {"name":"...", "type":0, "url":"live.txt"} 这种是 URL 直播源，暂存
                    // 格式由 TVBoxOS 的 loadLiveApi 处理，这里简单记录
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // EPG URL
            val epgUrl = DefaultConfig.safeJsonString(root, "epgUrl")
            if (epgUrl.isNotEmpty()) {
                PrefsManager.putString(HawkConfig.EPG_API_URL, epgUrl)
            }

            // Hosts
            safeGetObject(root, "hosts")?.entrySet()?.forEach { (host, ip) ->
                hostsMap[host] = ip.asString
            }

            // Rules - 容错处理：rule 可能是字符串也可能是数组
            safeGetArray(root, "rules")?.forEach { ruleEl ->
                try {
                    val rule = ruleEl.asJsonObject
                    val host = DefaultConfig.safeJsonString(rule, "host")
                    // rule 字段可能是字符串或数组
                    val ruleValue = if (rule.has("rule")) {
                        val r = rule.get("rule")
                        if (r.isJsonArray) r.asJsonArray.joinToString(",") { it.asString }
                        else r.asString
                    } else null

                    val filterValue = if (rule.has("filter")) {
                        val f = rule.get("filter")
                        if (f.isJsonArray) f.asJsonArray.joinToString(",") { it.asString }
                        else f.asString
                    } else null

                    videoParseRules.add(VideoParseRule(
                        host = host.ifEmpty { null },
                        rule = ruleValue,
                        filter = filterValue,
                        hosts = safeGetArray(rule, "hosts")?.map { it.asString },
                        regex = DefaultConfig.safeJsonString(rule, "regex").ifEmpty { null },
                        script = DefaultConfig.safeJsonString(rule, "script").ifEmpty { null }
                    ))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Ads
            safeGetArray(root, "ads")?.forEach {
                adHosts.add(it.asString)
            }

            // IJK Codecs
            safeGetArray(root, "ijk")?.forEach { ijkEl ->
                try {
                    val ijk = ijkEl.asJsonObject
                    ijkCodes.add(IJKCode(
                        name = DefaultConfig.safeJsonString(ijk, "name"),
                        code = DefaultConfig.safeJsonString(ijk, "code"),
                        player = DefaultConfig.safeJsonInt(ijk, "player", 1)
                    ))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // DOH
            dohUrl = DefaultConfig.safeJsonString(root, "doh").ifEmpty { null }
            dohUrl?.let { PrefsManager.putString(HawkConfig.DOH_URL, it) }

            // Set default parse
            if (parseBeanList.isNotEmpty()) {
                defaultParse = parseBeanList.firstOrNull { it.type != 4 }
            }
    }

    private fun chooseDefaultHomeSource(): SourceBean? {
        val sources = sourceBeanList.values.toList()
        return sources.firstOrNull { it.isPreferredHomeSource() }
            ?: sources.firstOrNull { it.isHomeUsable() }
            ?: sources.firstOrNull { it.filterable == 1 }
    }

    private fun SourceBean.isPreferredHomeSource(): Boolean {
        return isHomeUsable() &&
            indexs != 0 &&
            (changeable != 0 || searchable == 1 || quickSearch == 1)
    }

    private fun SourceBean.isHomeUsable(): Boolean {
        if (key.isBlank() || api.isBlank()) return false
        if (filterable == 0) return false
        val label = "$key $name"
        if (label.contains("点我切源", ignoreCase = true)) return false
        if (label.contains("切源", ignoreCase = true) && searchable == 0 && quickSearch == 0) return false
        if (name.contains("我配置") && searchable == 0 && quickSearch == 0) return false
        return true
    }

    /** 解析嵌套的直播频道 */
    private fun parseLiveChannels(live: JsonObject, groupName: String) {
        val group = LiveChannelGroup(name = groupName)
        safeGetArray(live, "channels")?.forEach { chEl ->
            try {
                val ch = chEl.asJsonObject
                val urls = mutableListOf<String>()
                safeGetArray(ch, "urls")?.forEach { urls.add(it.asString) }
                group.channels.add(LiveChannelItem(
                    name = DefaultConfig.safeJsonString(ch, "name"),
                    urls = urls,
                    epgName = DefaultConfig.safeJsonString(ch, "epgName").ifEmpty { null },
                    playerType = DefaultConfig.safeJsonInt(ch, "playerType"),
                    userAgent = DefaultConfig.safeJsonString(ch, "userAgent").ifEmpty { null },
                    catchup = DefaultConfig.safeJsonString(ch, "catchup").ifEmpty { null }
                ))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (group.channels.isNotEmpty()) {
            liveChannelGroupList.add(group)
        }
    }

    // === Source Switching ===

    fun setSourceBean(source: SourceBean?) {
        homeSource = source
        if (source != null) {
            PrefsManager.putString(HawkConfig.HOME_API, source.key)
        }
    }

    // === Spider Support ===

    fun getSpider(sourceKey: String, api: String, ext: String?, jar: String?): com.github.catvod.crawler.Spider {
        return jarLoader.getSpider(sourceKey, api, ext, jar)
    }

    /** 在 IO 线程中加载主 JAR（非 suspend，内部调用） */
    private fun loadMainJarInternal(spider: String): Boolean {
        return jarLoader.loadMainJar(spider)
    }

    /** 加载主 JAR（配置中的 spider 字段），必须在 IO 线程调用 */
    suspend fun loadMainJar(): Boolean = withContext(Dispatchers.IO) {
        val url = spiderUrl ?: return@withContext false
        jarLoader.loadMainJar(url)
    }

    // === Utility ===

    fun getSource(key: String?): SourceBean? {
        return sourceBeanList[key]
    }

    fun getAllSources(): List<SourceBean> {
        return sourceBeanList.values.toList()
    }

    fun getSearchableSources(): List<SourceBean> {
        return sourceBeanList.values.filter { it.searchable == 1 }
    }

    fun getLivePlayerType(): Int {
        return PrefsManager.getInt(HawkConfig.PLAY_TYPE, 1)
    }
}

private fun safeGetArray(obj: JsonObject, key: String): JsonArray? {
    return try { obj.getAsJsonArray(key) } catch (e: Exception) { null }
}

private fun safeGetObject(obj: JsonObject, key: String): JsonObject? {
    return try { obj.getAsJsonObject(key) } catch (e: Exception) { null }
}
