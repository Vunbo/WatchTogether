package com.vunbo.watchtogether.data.model

data class AbsSortJson(
    var code: Int = 0,
    var msg: String? = null,
    var `class`: MutableList<AbsJsonClass> = mutableListOf(),
    var list: MutableList<AbsJsonVod> = mutableListOf()
) {
    fun toAbsSortXml(): AbsSortXml {
        val sortXml = AbsSortXml()
        sortXml.classes = MovieSort()
        `class`.forEach { jsonClass ->
            sortXml.classes!!.sortList.add(
                MovieSort.SortData(
                    id = jsonClass.typeId ?: "",
                    name = jsonClass.typeName ?: "",
                    sort = (jsonClass.typeSort?.toIntOrNull() ?: 0),
                    flag = jsonClass.typeFlag
                )
            )
        }
        sortXml.videoList = list.map { it.toXmlVideo() }.toMutableList()
        return sortXml
    }
}
