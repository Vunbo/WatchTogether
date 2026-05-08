package com.vunbo.watchtogether.data.model

data class AbsSortXml(
    var msg: String? = null,
    var classes: MovieSort? = null,
    var list: Movie? = null,
    var videoList: MutableList<Movie.Video> = mutableListOf()
)
