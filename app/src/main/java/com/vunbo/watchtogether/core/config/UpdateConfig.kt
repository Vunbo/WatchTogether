package com.vunbo.watchtogether.core.config

object UpdateConfig {
    const val UPDATE_JSON_URL =
        "https://github.com/Vunbo/WatchTogether/releases/latest/download/update.json"

    const val AUTO_CHECK_DELAY_MS = 2_500L
    const val DISMISSED_VERSION_SILENCE_MS = 12L * 60L * 60L * 1000L
    const val DOWNLOAD_DIR = "updates"
}
