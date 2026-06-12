package com.vunbo.watchtogether.feature.live.model

import com.vunbo.watchtogether.core.player.PlayerHelper
import com.vunbo.watchtogether.data.live.LiveFavoriteStore
import com.vunbo.watchtogether.data.model.Epginfo
import com.vunbo.watchtogether.data.model.LiveChannelGroup
import com.vunbo.watchtogether.data.model.LiveChannelItem
import com.vunbo.watchtogether.data.model.LiveFavorite
import com.vunbo.watchtogether.data.model.LiveSource

data class LiveUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val userMessage: String? = null,
    val isBuffering: Boolean = false,
    val isPlaying: Boolean = false,
    val sources: List<LiveSource> = emptyList(),
    val selectedSourceIndex: Int = 0,
    val selectedGroupIndex: Int = 0,
    val selectedChannelIndex: Int = 0,
    val selectedLineIndex: Int = 0,
    val selectedDisplayGroupSourceIndex: Int = 0,
    val currentProgram: String = NO_PROGRAM_TEXT,
    val nextProgram: String = NO_PROGRAM_TEXT,
    val programList: List<Epginfo> = emptyList(),
    val favorites: List<LiveFavorite> = emptyList(),
    val searchQuery: String = "",
    val playbackMode: LivePlaybackMode = LivePlaybackMode.Live,
    val catchupProgram: Epginfo? = null,
    val playerType: Int = PlayerHelper.PLAYER_TYPE_EXO,
    val scaleType: Int = PlayerHelper.SCALE_DEFAULT,
    val switchTimeoutSeconds: Int = 10
) {
    val currentSource: LiveSource?
        get() = sources.getOrNull(selectedSourceIndex)

    val groups: List<LiveChannelGroup>
        get() = currentSource?.groups.orEmpty()

    val favoriteChannelRefs: List<LiveChannelRef>
        get() {
            val source = currentSource ?: return emptyList()
            val favoriteKeys = favorites.map { it.key }.toSet()
            return groups.flatMapIndexed { groupIndex, group ->
                group.channels.mapIndexedNotNull { channelIndex, channel ->
                    val favorite = LiveFavoriteStore.buildFavorite(source, group.name, channel)
                    if (favorite.key in favoriteKeys) {
                        LiveChannelRef(groupIndex, channelIndex, group.name, channel)
                    } else {
                        null
                    }
                }
            }
        }

    val displayGroups: List<LiveDisplayGroup>
        get() {
            val query = searchQuery.trim()
            val baseGroups = groups.mapIndexed { index, group ->
                LiveDisplayGroup(
                    name = group.name,
                    sourceGroupIndex = index,
                    channels = group.channels.mapIndexed { channelIndex, channel ->
                        LiveChannelRef(index, channelIndex, group.name, channel)
                    }
                )
            }
            val favoriteGroup = LiveDisplayGroup(
                name = "收藏",
                sourceGroupIndex = FAVORITE_GROUP_INDEX,
                channels = favoriteChannelRefs
            )
            val source = if (favoriteGroup.channels.isNotEmpty()) listOf(favoriteGroup) + baseGroups else baseGroups
            if (query.isBlank()) return source
            return source.mapNotNull { group ->
                val matchedChannels = group.channels.filter { ref ->
                    group.name.contains(query, ignoreCase = true) ||
                        ref.channel.name.contains(query, ignoreCase = true) ||
                        ref.channel.epgName.orEmpty().contains(query, ignoreCase = true) ||
                        ref.channel.tvgId.orEmpty().contains(query, ignoreCase = true)
                }
                if (matchedChannels.isEmpty()) null else group.copy(channels = matchedChannels)
            }
        }

    val selectedDisplayGroupIndex: Int
        get() {
            val groups = displayGroups
            val selected = groups.indexOfFirst { it.sourceGroupIndex == selectedDisplayGroupSourceIndex }
            return if (selected >= 0) selected else 0
        }

    val displayChannels: List<LiveChannelRef>
        get() = displayGroups.getOrNull(selectedDisplayGroupIndex)?.channels.orEmpty()

    val selectedDisplayChannelIndex: Int
        get() {
            val channels = displayChannels
            val selected = channels.indexOfFirst {
                it.groupIndex == selectedGroupIndex && it.channelIndex == selectedChannelIndex
            }
            return selected
        }

    val channels: List<LiveChannelItem>
        get() = groups.getOrNull(selectedGroupIndex)?.channels.orEmpty()

    val currentChannel: LiveChannelItem?
        get() = channels.getOrNull(selectedChannelIndex)

    val currentUrl: String?
        get() = currentChannel?.urls?.getOrNull(selectedLineIndex)

    val hasProgram: Boolean
        get() = currentProgram != NO_PROGRAM_TEXT || nextProgram != NO_PROGRAM_TEXT

    val isCurrentFavorite: Boolean
        get() {
            val source = currentSource ?: return false
            val groupName = groups.getOrNull(selectedGroupIndex)?.name ?: return false
            val channel = currentChannel ?: return false
            val key = LiveFavoriteStore.buildFavorite(source, groupName, channel).key
            return favorites.any { it.key == key }
        }

    val canCatchup: Boolean
        get() = currentChannel?.let { !it.catchupSource.isNullOrBlank() || !it.catchup.isNullOrBlank() } == true

    companion object {
        const val NO_PROGRAM_TEXT = "暂无播放预告"
        const val FAVORITE_GROUP_INDEX = -1
    }
}

data class LiveDisplayGroup(
    val name: String,
    val sourceGroupIndex: Int,
    val channels: List<LiveChannelRef>
)

data class LiveChannelRef(
    val groupIndex: Int,
    val channelIndex: Int,
    val groupName: String,
    val channel: LiveChannelItem
)

enum class LivePlaybackMode {
    Live,
    Catchup
}
