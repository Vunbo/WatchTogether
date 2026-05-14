package com.vunbo.watchtogether.data.model

import java.io.Serializable

data class VodInfo(
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
    var seriesFlags: MutableList<VodSeriesFlag> = mutableListOf(),
    var seriesMap: MutableMap<String, MutableList<VodSeries>> = linkedMapOf(),
    var playFlag: String? = null,
    var playIndex: Int = 0,
    var playNote: String? = null,
    var playPosition: Long = 0L,
    var playDuration: Long = 0L,
    var playUpdateTime: Long = 0L,
    var playerCfg: String? = null,
    var reverseSort: Boolean = false
) : Serializable {
    fun setVideo(video: Movie.Video) {
        id = video.id
        tid = video.tid
        name = video.name
        type = video.type
        pic = video.pic
        lang = video.lang
        area = video.area
        year = video.year
        state = video.state
        note = video.note
        actor = video.actor
        director = video.director
        des = video.des
        last = video.last
        sourceKey = video.sourceKey
        urlBeanToSeries(video.urlBean)
    }

    private fun urlBeanToSeries(urlBean: Movie.UrlBean?) {
        seriesFlags.clear()
        seriesMap.clear()
        urlBean?.urlInfoList?.forEach { urlInfo ->
            val flag = urlInfo.flag ?: "默认"
            val seriesList = mutableListOf<VodSeries>()
            urlInfo.beanList.forEach { bean ->
                seriesList.add(VodSeries(bean.name ?: "", bean.url ?: ""))
            }
            if (seriesList.size <= 5) {
                seriesList.reverse()
                reverseSort = true
            }
            seriesFlags.add(VodSeriesFlag(flag, urlInfo.urls ?: ""))
            seriesMap[flag] = seriesList
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

data class VodSeriesFlag(
    var name: String,
    var url: String
) : Serializable

data class VodSeries(
    var name: String,
    var url: String
) : Serializable
