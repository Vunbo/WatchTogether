package com.vunbo.watchtogether.data.live

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vunbo.watchtogether.data.model.LiveChannelItem
import com.vunbo.watchtogether.data.model.LiveFavorite
import com.vunbo.watchtogether.data.model.LiveSource
import com.vunbo.watchtogether.core.storage.HawkConfig
import com.vunbo.watchtogether.core.storage.PrefsManager

object LiveFavoriteStore {
    private val gson = Gson()
    private val listType = object : TypeToken<List<LiveFavorite>>() {}.type

    fun load(): List<LiveFavorite> {
        val raw = PrefsManager.getString(HawkConfig.LIVE_FAVORITES).trim()
        if (raw.isBlank()) return emptyList()
        return runCatching { gson.fromJson<List<LiveFavorite>>(raw, listType) }
            .getOrDefault(emptyList())
            .distinctBy { it.key }
    }

    fun save(favorites: List<LiveFavorite>) {
        PrefsManager.putString(HawkConfig.LIVE_FAVORITES, gson.toJson(favorites.distinctBy { it.key }))
    }

    fun buildFavorite(source: LiveSource, groupName: String, channel: LiveChannelItem): LiveFavorite {
        return LiveFavorite(
            sourceId = source.id,
            sourceName = source.name,
            groupName = groupName,
            channelName = channel.name,
            epgName = channel.epgName
        )
    }
}
