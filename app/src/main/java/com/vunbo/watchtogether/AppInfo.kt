package com.vunbo.watchtogether

object AppInfo {
    const val APP_NAME = "WatchTogether"

    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val versionCode: Int
        get() = BuildConfig.VERSION_CODE
}
