package com.vunbo.watchtogether.data.model

data class MovieSort(
    var sortList: MutableList<SortData> = mutableListOf()
) {
    data class SortData(
        var id: String = "",
        var name: String = "",
        var sort: Int = 0,
        var flag: String? = null,
        var filters: MutableList<SortFilter> = mutableListOf(),
        var filterSelect: MutableMap<String, String> = mutableMapOf()
    )

    data class SortFilter(
        var key: String = "",
        var name: String = "",
        var values: MutableMap<String, String> = linkedMapOf()
    )
}
