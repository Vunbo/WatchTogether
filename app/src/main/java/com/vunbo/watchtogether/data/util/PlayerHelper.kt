package com.vunbo.watchtogether.data.util

object PlayerHelper {
    const val PLAYER_TYPE_SYSTEM = 0
    const val PLAYER_TYPE_IJK = 1
    const val PLAYER_TYPE_EXO = 2

    const val RENDER_TEXTURE = 0
    const val RENDER_SURFACE = 1

    const val SCALE_DEFAULT = 0
    const val SCALE_16_9 = 1
    const val SCALE_4_3 = 2
    const val SCALE_FILL = 3
    const val SCALE_ORIGINAL = 4
    const val SCALE_CROP = 5

    val scaleNames = arrayOf("默认", "16:9", "4:3", "填充", "原始", "裁剪")

    val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)

    fun getSpeedIndex(speed: Float): Int {
        return speedValues.indexOfFirst { it == speed }.coerceAtLeast(0)
    }

    fun getNextSpeed(current: Float): Float {
        val idx = getSpeedIndex(current)
        val next = (idx + 1) % speedValues.size
        return speedValues[next]
    }

    fun getSpeedDisplay(speed: Float): String {
        return "${speed}x"
    }

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
