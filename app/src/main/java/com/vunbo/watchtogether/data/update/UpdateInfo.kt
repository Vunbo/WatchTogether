package com.vunbo.watchtogether.data.update

import com.google.gson.annotations.SerializedName
import java.util.Locale

data class UpdateInfo(
    @SerializedName("schemaVersion")
    val schemaVersion: Int = 1,
    @SerializedName("versionCode")
    val versionCode: Int = 0,
    @SerializedName("versionName")
    val versionName: String? = null,
    @SerializedName("minSupportedVersionCode")
    val minSupportedVersionCode: Int = 0,
    @SerializedName("forceUpdate")
    val forceUpdate: Boolean = false,
    @SerializedName("apkUrl")
    val apkUrl: String? = null,
    @SerializedName("apkSha256")
    val apkSha256: String? = null,
    @SerializedName("apkSize")
    val apkSize: Long = 0L,
    @SerializedName("releaseNotes")
    val releaseNotes: List<String>? = emptyList(),
    @SerializedName("publishedAt")
    val publishedAt: String? = null
) {
    val safeVersionName: String
        get() = versionName?.takeIf { it.isNotBlank() } ?: versionCode.toString()

    val downloadUrl: String
        get() = apkUrl.orEmpty()

    val notes: List<String>
        get() = releaseNotes.orEmpty().filter { it.isNotBlank() }

    fun isNewerThan(currentVersionCode: Int): Boolean = versionCode > currentVersionCode

    fun isForceFor(currentVersionCode: Int): Boolean {
        return forceUpdate || (minSupportedVersionCode > 0 && currentVersionCode < minSupportedVersionCode)
    }

    fun formattedSize(): String {
        if (apkSize <= 0L) return "未知大小"
        val mb = apkSize / 1024f / 1024f
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
