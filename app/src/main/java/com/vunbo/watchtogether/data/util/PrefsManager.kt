package com.vunbo.watchtogether.data.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PrefsManager {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "watch_together_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun getInt(key: String, default: Int = 0): Int =
        prefs.getInt(key, default)

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun getLong(key: String, default: Long = 0L): Long =
        prefs.getLong(key, default)

    fun getFloat(key: String, default: Float = 0f): Float =
        prefs.getFloat(key, default)

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun putFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

object HawkConfig {
    // API
    const val API_URL = "api_url"
    const val API_EFFECTIVE_URL = "api_effective_url"
    const val API_STORE_LIST = "api_store_list"
    const val API_STORE_SELECTED = "api_store_selected"
    const val LIVE_API_URL = "live_api_url"
    const val VOD_SUBSCRIPTION_GROUPS = "vod_subscription_groups"
    const val VOD_SUBSCRIPTION_SELECTED_GROUP = "vod_subscription_selected_group"
    const val VOD_SUBSCRIPTION_SELECTED_STORE = "vod_subscription_selected_store"
    const val VOD_SUBSCRIPTION_LAST_REFRESH = "vod_subscription_last_refresh"
    const val LIVE_SUBSCRIPTION_GROUPS = "live_subscription_groups"
    const val LIVE_SUBSCRIPTION_SELECTED_GROUP = "live_subscription_selected_group"
    const val LIVE_SUBSCRIPTION_SELECTED_STORE = "live_subscription_selected_store"
    const val LIVE_SUBSCRIPTION_LAST_REFRESH = "live_subscription_last_refresh"
    const val EPG_API_URL = "epg_api_url"
    const val HOME_API = "home_api"
    const val HOME_REC = "home_rec"
    const val HOME_REC_STYLE = "home_rec_style"

    // Player
    const val PLAY_TYPE = "play_type"
    const val PLAY_RENDER = "play_render"
    const val PLAY_SCALE = "play_scale"
    const val PLAY_SPEED = "play_speed"
    const val IJK_CODEC = "ijk_codec"
    const val PLAYER_IS_LIVE = "player_is_live"
    const val SHOW_PREVIEW = "show_preview"
    const val TOGETHER_SERVER_URL = "together_server_url"

    // Subtitle
    const val SUBTITLE_SIZE = "subtitle_size"
    const val SUBTITLE_DELAY = "subtitle_delay"

    // Live
    const val LIVE_CHANNEL = "live_channel"
    const val LIVE_SOURCE_ID = "live_source_id"
    const val LIVE_SOURCE_INDEX = "live_source_index"
    const val LIVE_GROUP_INDEX = "live_group_index"
    const val LIVE_LINE_INDEX = "live_line_index"
    const val LIVE_CONNECT_TIMEOUT = "live_connect_timeout"
    const val CROSS_GROUP_SWITCH = "cross_group_switch"
    const val LIVE_SHOW_TIME = "live_show_time"
    const val LIVE_SHOW_SPEED = "live_show_speed"
    const val LIVE_CHANNEL_REVERSE = "live_channel_reverse"
    const val LIVE_SKIP_PASSWORD = "live_skip_password"
    const val LIVE_FAVORITES = "live_favorites"

    // Search
    const val SEARCH_HISTORY = "search_history"
    const val SEARCH_VIEW = "search_view"
    const val FAST_SEARCH_MODE = "fast_search_mode"

    // Debug
    const val DEBUG_OPEN = "debug_open"

    // Display
    const val SHOW_WALLPAPER = "show_wallpaper"
    const val WALLPAPER_URL = "wallpaper_url"

    // M3U8
    const val M3U8_PURIFY = "m3u8_purify"

    // DOH
    const val DOH_URL = "doh_url"

    // Other
    const val HISTORY_NUM = "history_num"
    const val PARSE_DEFAULT = "parse_default"
    const val REMOTE_TVBOX = "remote_tvbox"

    // Update
    const val UPDATE_LAST_AUTO_CHECK = "update_last_auto_check"
    const val UPDATE_DISMISSED_VERSION_CODE = "update_dismissed_version_code"
    const val UPDATE_DISMISSED_AT = "update_dismissed_at"
}
