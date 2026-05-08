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
    var catchup: String? = null
)

data class LiveDayListGroup(
    var date: String = "",
    var epgList: MutableList<Epginfo> = mutableListOf()
)

data class Epginfo(
    var start: String = "",
    var end: String = "",
    var title: String = "",
    var desc: String? = null
)

data class LiveEpgDate(
    var date: String = "",
    var dayOfWeek: String = ""
)
