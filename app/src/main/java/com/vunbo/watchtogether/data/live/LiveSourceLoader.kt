package com.vunbo.watchtogether.data.live

import android.util.Log
import com.vunbo.watchtogether.app.WatchTogetherApp
import com.vunbo.watchtogether.data.model.LiveChannelGroup
import com.vunbo.watchtogether.data.model.LiveChannelItem
import com.vunbo.watchtogether.data.model.LiveSourceEntry
import com.vunbo.watchtogether.core.util.MD5
import com.vunbo.watchtogether.core.network.OkHttpHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

object LiveSourceLoader {
    private val cacheDir: File
        get() = File(WatchTogetherApp.instance.filesDir, "live_cache").apply { mkdirs() }

    suspend fun load(entry: LiveSourceEntry, baseUrl: String): List<LiveChannelGroup> = withContext(Dispatchers.IO) {
        val url = resolveUrl(baseUrl, entry.url)
        val cacheFile = File(cacheDir, "${MD5.encode(url)}.txt")
        val headers = entry.userAgent?.takeIf { it.isNotBlank() }?.let { mapOf("User-Agent" to it) }.orEmpty()
        val content = getLiveBody(url, headers)?.also { body ->
            cacheFile.writeText(body, Charsets.UTF_8)
        } ?: if (cacheFile.exists()) {
            cacheFile.readText(Charsets.UTF_8)
        } else {
            ""
        }
        parse(content, entry)
    }

    private fun getLiveBody(url: String, headers: Map<String, String>): String? {
        return try {
            OkHttpHelper.getWithClient(
                url = url,
                headers = headers,
                useParserClient = true
            ).use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.w(TAG, "直播源加载失败: HTTP ${response.code} $url")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "直播源加载异常: $url - ${e.message}")
            null
        }
    }

    fun resolveUrl(baseUrl: String, rawUrl: String): String {
        val clean = rawUrl.trim()
        if (clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true)
        ) {
            return clean
        }
        return runCatching { URI(baseUrl).resolve(clean).toString() }
            .getOrElse { clean }
    }

    fun parse(content: String, entry: LiveSourceEntry = LiveSourceEntry()): List<LiveChannelGroup> {
        val clean = content.trim().removePrefix("\uFEFF")
        if (clean.isBlank()) return emptyList()
        return if (clean.lineSequence().any { it.trim().startsWith("#EXTM3U", ignoreCase = true) }) {
            parseM3u(clean, entry)
        } else {
            parseTxt(clean, entry)
        }
    }

    private fun parseTxt(content: String, entry: LiveSourceEntry): List<LiveChannelGroup> {
        val groups = linkedMapOf<String, LinkedHashMap<String, LiveChannelItem>>()
        var currentGroup = entry.name.ifBlank { "默认" }
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("//") }
            .forEach { line ->
                val parts = line.split(",", limit = 2)
                if (parts.size < 2) return@forEach
                val name = parts[0].trim().ifBlank { return@forEach }
                val value = parts[1].trim().ifBlank { return@forEach }
                if (value.equals("#genre#", ignoreCase = true)) {
                    currentGroup = name
                    return@forEach
                }
                val urls = value.split("#", "|", "&&")
                    .map { it.trim() }
                    .filter { isPlayableUrl(it) }
                    .distinct()
                if (urls.isEmpty()) return@forEach
                addChannel(groups, currentGroup, name, urls, entry)
            }
        return groups.toLiveGroups()
    }

    private fun parseM3u(content: String, entry: LiveSourceEntry): List<LiveChannelGroup> {
        val groups = linkedMapOf<String, LinkedHashMap<String, LiveChannelItem>>()
        var pendingName: String? = null
        var pendingGroup: String? = null
        var pendingEpgName: String? = null
        var pendingTvgId: String? = null
        var pendingCatchup: String? = null
        var pendingCatchupSource: String? = null
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                if (line.startsWith("#EXTINF", ignoreCase = true)) {
                    pendingName = line.substringAfterLast(",", "").trim().ifBlank { null }
                    pendingEpgName = extractAttr(line, "tvg-name") ?: pendingName
                    pendingTvgId = extractAttr(line, "tvg-id")
                    pendingCatchup = extractAttr(line, "catchup")
                    pendingCatchupSource = extractAttr(line, "catchup-source")
                    pendingGroup = extractAttr(line, "group-title")
                        ?: extractAttr(line, "group")
                        ?: entry.name.ifBlank { "默认" }
                } else if (!line.startsWith("#") && isPlayableUrl(line)) {
                    val name = pendingName ?: line.substringAfterLast("/").substringBefore("?").ifBlank { "频道" }
                    val group = pendingGroup ?: entry.name.ifBlank { "默认" }
                    addChannel(
                        groups = groups,
                        groupName = group,
                        channelName = name,
                        urls = listOf(line),
                        entry = entry,
                        epgName = pendingEpgName,
                        tvgId = pendingTvgId,
                        catchup = pendingCatchup,
                        catchupSource = pendingCatchupSource
                    )
                    pendingName = null
                    pendingGroup = null
                    pendingEpgName = null
                    pendingTvgId = null
                    pendingCatchup = null
                    pendingCatchupSource = null
                }
            }
        return groups.toLiveGroups()
    }

    private fun extractAttr(line: String, key: String): String? {
        val regex = Regex("""$key\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
        return regex.find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun addChannel(
        groups: LinkedHashMap<String, LinkedHashMap<String, LiveChannelItem>>,
        groupName: String,
        channelName: String,
        urls: List<String>,
        entry: LiveSourceEntry,
        epgName: String? = null,
        tvgId: String? = null,
        catchup: String? = null,
        catchupSource: String? = null
    ) {
        val group = groups.getOrPut(groupName.ifBlank { "默认" }) { linkedMapOf() }
        val channel = group.getOrPut(channelName) {
            LiveChannelItem(
                name = channelName,
                epgName = epgName,
                playerType = entry.playerType,
                userAgent = entry.userAgent,
                catchup = catchup,
                catchupSource = catchupSource,
                tvgId = tvgId
            )
        }
        if (channel.epgName.isNullOrBlank()) channel.epgName = epgName
        if (channel.tvgId.isNullOrBlank()) channel.tvgId = tvgId
        if (channel.catchup.isNullOrBlank()) channel.catchup = catchup
        if (channel.catchupSource.isNullOrBlank()) channel.catchupSource = catchupSource
        urls.forEach { url ->
            if (channel.urls.none { it.equals(url, ignoreCase = true) }) {
                channel.urls.add(url)
            }
        }
    }

    private fun LinkedHashMap<String, LinkedHashMap<String, LiveChannelItem>>.toLiveGroups(): List<LiveChannelGroup> {
        return mapNotNull { (name, channels) ->
            channels.values.toMutableList().takeIf { it.isNotEmpty() }?.let {
                LiveChannelGroup(name = name, channels = it)
            }
        }
    }

    private fun isPlayableUrl(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("rtmp://") ||
            lower.startsWith("rtsp://")
    }

    private const val TAG = "LiveSourceLoader"
}
