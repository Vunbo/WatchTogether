package com.vunbo.watchtogether.data.util

object PlayerHelper {
    const val PLAYER_TYPE_SYSTEM = 0
    const val PLAYER_TYPE_IJK = 1
    const val PLAYER_TYPE_EXO = 2

    const val SCALE_DEFAULT = 0
    val scaleNames = arrayOf("默认", "16:9", "4:3", "填充", "原始", "裁剪")

    fun getScaleDisplay(scale: Int): String {
        return scaleNames.getOrElse(scale) { "默认" }
    }

    fun getPlayerName(type: Int): String = when (type) {
        PLAYER_TYPE_SYSTEM -> "系统播放器"
        PLAYER_TYPE_IJK -> "IJK播放器"
        PLAYER_TYPE_EXO -> "Exo播放器"
        else -> "未知播放器"
    }
}
