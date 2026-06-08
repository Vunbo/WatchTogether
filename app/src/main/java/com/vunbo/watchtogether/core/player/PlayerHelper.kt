package com.vunbo.watchtogether.data.util

object PlayerHelper {
    const val PLAYER_TYPE_EXO = 2

    const val SCALE_DEFAULT = 0
    val scaleNames = arrayOf("默认", "16:9", "4:3", "填充", "原始", "裁剪")

    fun getScaleDisplay(scale: Int): String {
        return scaleNames.getOrElse(scale) { "默认" }
    }

    fun getPlayerName(type: Int = PLAYER_TYPE_EXO): String = "Exo播放器"
}
