package com.vunbo.watchtogether.data.live

import com.vunbo.watchtogether.data.source.ApiConfig
import com.vunbo.watchtogether.data.model.LiveChannelGroup
import com.vunbo.watchtogether.data.model.LiveSource
import com.vunbo.watchtogether.data.model.LiveSourceEntry
import com.vunbo.watchtogether.core.storage.HawkConfig
import com.vunbo.watchtogether.core.util.MD5
import com.vunbo.watchtogether.core.storage.PrefsManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

class LiveRepository(
    private val apiConfig: ApiConfig = ApiConfig.get()
) {
    suspend fun loadSources(forceRefresh: Boolean = false): List<LiveSource> {
        val cacheKey = currentCacheKey()
        if (!forceRefresh) {
            cachedSources?.takeIf { cachedKey == cacheKey && it.isNotEmpty() }?.let { return it.copyLiveSources() }
        }

        val loaded = loadSourcesInternal()
        if (loaded.isNotEmpty()) {
            cachedKey = currentCacheKey()
            cachedSources = loaded.copyLiveSources()
            return loaded
        }

        cachedSources?.takeIf { cachedKey == cacheKey && it.isNotEmpty() }?.let { return it.copyLiveSources() }
        return emptyList()
    }

    suspend fun warmUp(forceRefresh: Boolean = true): Boolean {
        return loadSources(forceRefresh = forceRefresh).isNotEmpty()
    }

    private suspend fun loadSourcesInternal(): List<LiveSource> = coroutineScope {
        if (apiConfig.sourceBeanList.isEmpty()) {
            apiConfig.loadConfig(useCache = true)
        }

        val baseUrl = apiConfig.currentConfigUrl
        val customLiveUrl = PrefsManager.getString(HawkConfig.LIVE_API_URL).trim()
        val customTask = customLiveUrl.takeIf { it.isNotBlank() }?.let { url ->
            async {
                loadExternalSource(
                    entry = LiveSourceEntry(name = "自定义直播", url = url),
                    baseUrl = baseUrl,
                    custom = true
                )
            }
        }

        val embeddedSource = apiConfig.liveChannelGroupList
            .mapNotNull { group -> group.copyLiveGroup().takeIf { it.channels.isNotEmpty() } }
            .takeIf { it.isNotEmpty() }
            ?.let { groups ->
                LiveSource(
                    id = "embedded",
                    name = "订阅直播",
                    groups = groups
                )
            }

        val externalTasks = apiConfig.liveSourceList.map { entry ->
            async { loadExternalSource(entry, baseUrl, custom = false) }
        }

        buildList {
            customTask?.await()?.let { add(it) }
            embeddedSource?.let { add(it) }
            externalTasks.awaitAll().filterNotNull().forEach { add(it) }
        }
    }

    private suspend fun loadExternalSource(
        entry: LiveSourceEntry,
        baseUrl: String,
        custom: Boolean
    ): LiveSource? {
        val resolvedUrl = LiveSourceLoader.resolveUrl(baseUrl, entry.url)
        val groups = withTimeoutOrNull(LIVE_SOURCE_TIMEOUT_MS) {
            runCatching { LiveSourceLoader.load(entry, baseUrl) }.getOrDefault(emptyList())
        }.orEmpty()
        if (groups.isEmpty()) return null
        return LiveSource(
            id = if (custom) "custom:${MD5.encode(resolvedUrl)}" else "source:${MD5.encode(resolvedUrl)}",
            name = entry.name.ifBlank { if (custom) "自定义直播" else "直播源" },
            url = resolvedUrl,
            groups = groups.map { it.copyLiveGroup() },
            custom = custom
        )
    }

    private fun LiveChannelGroup.copyLiveGroup(): LiveChannelGroup {
        return copy(channels = channels.map { channel ->
            channel.copy(urls = channel.urls.toMutableList())
        }.toMutableList())
    }

    private fun List<LiveSource>.copyLiveSources(): List<LiveSource> {
        return map { source ->
            source.copy(groups = source.groups.map { it.copyLiveGroup() })
        }
    }

    private fun currentCacheKey(): String {
        val customLiveUrl = PrefsManager.getString(HawkConfig.LIVE_API_URL).trim()
        val liveEntries = apiConfig.liveSourceList.joinToString("|") { "${it.name}:${it.url}:${it.userAgent.orEmpty()}" }
        val embeddedShape = "${apiConfig.liveChannelGroupList.size}:${apiConfig.liveChannelGroupList.sumOf { it.channels.size }}"
        return listOf(apiConfig.currentConfigUrl, customLiveUrl, liveEntries, embeddedShape).joinToString("::")
    }

    companion object {
        private const val LIVE_SOURCE_TIMEOUT_MS = 12_000L
        @Volatile private var cachedKey: String? = null
        @Volatile private var cachedSources: List<LiveSource>? = null
    }
}
