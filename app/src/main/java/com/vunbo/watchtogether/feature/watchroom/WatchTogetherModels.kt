package com.vunbo.watchtogether.feature.watchroom

data class RoomState(
    val roomCode: String = "",
    val members: List<RoomMember> = emptyList(),
    val mediaState: MediaSyncState? = null,
    val hasFreshMediaState: Boolean = false,
    val isHost: Boolean = false,
    val userId: String = "",
    val serverUrl: String = "",
    val connected: Boolean = false,
    val reconnecting: Boolean = false
)

data class RoomMember(
    val userId: String = "",
    val userName: String = "",
    val isHost: Boolean = false,
    val joinedAt: Long = 0L,
    val connected: Boolean = true
)

data class MediaSyncState(
    val url: String = "",
    val videoTitle: String = "",
    val sourceKey: String = "",
    val vodId: String = "",
    val playFlag: String = "",
    val playIndex: Int = 0,
    val episodeName: String = "",
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val speed: Float = 1.0f,
    val temporarySpeed: Float? = null,
    val action: String = "sync",
    val timestamp: Long = System.currentTimeMillis(),
    val lastUpdateTime: Long = timestamp
)

data class ChatMessage(
    val userId: String = "",
    val userName: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSystem: Boolean = false,
    val isSelf: Boolean = false
)

data class WatchTogetherUiState(
    val connecting: Boolean = false,
    val error: String? = null
)

enum class WatchTogetherNoticeLevel {
    Normal,
    Warning
}

data class WatchTogetherNoticeState(
    val unreadCount: Int = 0,
    val message: String? = null,
    val level: WatchTogetherNoticeLevel = WatchTogetherNoticeLevel.Normal,
    val timestamp: Long = 0L
)
