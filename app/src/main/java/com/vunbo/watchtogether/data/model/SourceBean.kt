package com.vunbo.watchtogether.data.model

data class SourceBean(
    var key: String = "",
    var name: String = "",
    var api: String = "",
    var type: Int = 0,       // 0=xml, 1=json, 3=spider, 4=remote
    var searchable: Int = 0,
    var quickSearch: Int = 0,
    var filterable: Int = 0,
    var changeable: Int = 1,
    var indexs: Int = 1,
    var timeout: Int = 0,
    var playerUrl: String? = null,
    var ext: String? = null,
    var jar: String? = null,
    var categories: String? = null,
    var playerType: String? = null,
    var clickSelector: String? = null,
    var style: String? = null
)
