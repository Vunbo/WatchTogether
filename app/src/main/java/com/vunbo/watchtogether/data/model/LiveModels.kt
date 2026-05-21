package com.vunbo.watchtogether.data.model

data class LiveChannelGroup(
    var name: String = "",
    var channels: MutableList<LiveChannelItem> = mutableListOf()
)

data class LiveChannelItem(
    var name: String = "",
    var urls: MutableList<String> = mutableListOf(),
    var epgName: String? = null,
    var playerType: Int = 0,
    var userAgent: String? = null,
    var catchup: String? = null,
    var catchupSource: String? = null,
    var tvgId: String? = null
)

data class LiveSourceEntry(
    var name: String = "",
    var url: String = "",
    var type: Int = 0,
    var playerType: Int = 0,
    var userAgent: String? = null
)

data class LiveSource(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val groups: List<LiveChannelGroup> = emptyList(),
    val custom: Boolean = false
)

data class LiveFavorite(
    val sourceId: String = "",
    val sourceName: String = "",
    val groupName: String = "",
    val channelName: String = "",
    val epgName: String? = null
) {
    val key: String
        get() = listOf(sourceId, groupName, channelName).joinToString("|")
}

data class LiveDayListGroup(
    var date: String = "",
    var epgList: MutableList<Epginfo> = mutableListOf()
)

data class Epginfo(
    var start: String = "",
    var end: String = "",
    var title: String = "",
    var desc: String? = null
) {
    val timeRange: String
        get() = listOf(start.takeLast(5), end.takeLast(5))
            .filter { it.length == 5 }
            .joinToString("-")
}

data class LiveEpgDate(
    var date: String = "",
    var dayOfWeek: String = ""
)
