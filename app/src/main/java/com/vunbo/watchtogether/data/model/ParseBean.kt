package com.vunbo.watchtogether.data.model

data class ParseBean(
    var name: String = "",
    var url: String = "",
    var ext: String? = null,
    var type: Int = 0,       // 0=webview sniff, 1=json, 2=json ext, 3=mix, 4=super
    var isDefault: Boolean = false
)
