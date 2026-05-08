package com.vunbo.watchtogether.data.model

import com.google.gson.annotations.SerializedName

data class AbsJson(
    var code: Int = 0,
    var msg: String? = null,
    var page: Int = 1,
    var pagecount: Int = 0,
    var limit: Int = 20,
    var total: Int = 0,
    var list: MutableList<AbsJsonVod> = mutableListOf(),
    var `class`: MutableList<AbsJsonClass>? = null
)

data class AbsJsonVod(
    @SerializedName("vod_id") var vodId: String? = null,
    @SerializedName("vod_name") var vodName: String? = null,
    @SerializedName("type_name") var typeName: String? = null,
    @SerializedName("vod_pic") var vodPic: String? = null,
    @SerializedName("vod_remarks") var vodRemarks: String? = null,
    @SerializedName("vod_year") var vodYear: String? = null,
    @SerializedName("vod_area") var vodArea: String? = null,
    @SerializedName("vod_actor") var vodActor: String? = null,
    @SerializedName("vod_director") var vodDirector: String? = null,
    @SerializedName("vod_content") var vodContent: String? = null,
    @SerializedName("vod_play_from") var vodPlayFrom: String? = null,
    @SerializedName("vod_play_url") var vodPlayUrl: String? = null,
    @SerializedName("vod_lang") var vodLang: String? = null,
    @SerializedName("vod_time") var vodTime: String? = null,
    @SerializedName("vod_state") var vodState: String? = null,
    // Additional fields used by various APIs
    @SerializedName("type_id") var typeId: String? = null,
    @SerializedName("vod_tag") var vodTag: String? = null,
    @SerializedName("vod_total") var vodTotal: String? = null,
    @SerializedName("vod_author") var vodAuthor: String? = null,
    @SerializedName("vod_douban_score") var vodDoubanScore: String? = null,
    @SerializedName("vod_score") var vodScore: String? = null,
    @SerializedName("vod_weekday") var vodWeekday: String? = null,
    @SerializedName("vod_pic_thumb") var vodPicThumb: String? = null,
    @SerializedName("vod_pic_slide") var vodPicSlide: String? = null,
    @SerializedName("vod_play_server") var vodPlayServer: String? = null,
    @SerializedName("vod_play_note") var vodPlayNote: String? = null,
    @SerializedName("vod_down_from") var vodDownFrom: String? = null,
    @SerializedName("vod_down_url") var vodDownUrl: String? = null,
    @SerializedName("vod_down_note") var vodDownNote: String? = null,
    @SerializedName("vod_is_down") var vodIsDown: Int = 0
) {
    fun toXmlVideo(): Movie.Video {
        return Movie.Video(
            id = vodId,
            name = vodName,
            pic = vodPic,
            note = vodRemarks,
            type = typeName,
            year = vodYear,
            area = vodArea,
            actor = vodActor,
            director = vodDirector,
            des = vodContent,
            lang = vodLang,
            state = vodState,
            last = vodTime
        ).also { video ->
            val urlBean = Movie.UrlBean()
            urlBean.urlInfoList = parsePlayUrls()
            video.urlBean = urlBean
        }
    }

    private fun parsePlayUrls(): MutableList<Movie.UrlInfo> {
        val urlInfoList = mutableListOf<Movie.UrlInfo>()
        val playFroms = vodPlayFrom?.split("$$$") ?: listOf(vodPlayFrom ?: "默认")
        val playUrls = vodPlayUrl?.split("$$$") ?: listOf(vodPlayUrl ?: "")

        playFroms.forEachIndexed { index, flag ->
            val urlInfo = Movie.UrlInfo()
            urlInfo.flag = flag
            val urls = playUrls.getOrElse(index) { playUrls.lastOrNull() ?: "" }
            urlInfo.urls = urls
            urlInfo.beanList = parseUrlList(urls)
            urlInfoList.add(urlInfo)
        }
        return urlInfoList
    }

    private fun parseUrlList(urls: String): MutableList<Movie.InfoBean> {
        val beanList = mutableListOf<Movie.InfoBean>()
        urls.split("#").forEach { item ->
            val parts = item.split("$", limit = 2)
            if (parts.size >= 2) {
                beanList.add(Movie.InfoBean(parts[0], parts[1]))
            }
        }
        return beanList
    }
}

data class AbsJsonClass(
    @SerializedName("type_id") var typeId: String? = null,
    @SerializedName("type_name") var typeName: String? = null,
    @SerializedName("type_sort") var typeSort: String? = null,
    @SerializedName("type_flag") var typeFlag: String? = null
)
