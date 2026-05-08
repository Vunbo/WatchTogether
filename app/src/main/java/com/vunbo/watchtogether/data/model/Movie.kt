package com.vunbo.watchtogether.data.model

data class Movie(
    var page: Int = 1,
    var pagecount: Int = 0,
    var pagesize: Int = 20,
    var recordcount: Int = 0,
    var videoList: MutableList<Video> = mutableListOf()
) {
    data class Video(
        var id: String? = null,
        var tid: String? = null,
        var name: String? = null,
        var type: String? = null,
        var pic: String? = null,
        var lang: String? = null,
        var area: String? = null,
        var year: String? = null,
        var state: String? = null,
        var note: String? = null,
        var actor: String? = null,
        var director: String? = null,
        var des: String? = null,
        var last: String? = null,
        var sourceKey: String? = null,
        var tag: String? = null,
        var urlBean: UrlBean? = null
    )

    data class UrlBean(
        var urlInfoList: MutableList<UrlInfo> = mutableListOf()
    )

    data class UrlInfo(
        var flag: String? = null,
        var urls: String? = null,
        var beanList: MutableList<InfoBean> = mutableListOf()
    )

    data class InfoBean(
        var name: String? = null,
        var url: String? = null
    )
}
