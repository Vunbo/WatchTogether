package com.vunbo.watchtogether.data.importing

import android.net.Uri
import com.vunbo.watchtogether.data.subscription.SubscriptionType

data class ExternalImportRequest(
    val type: SubscriptionType,
    val name: String,
    val url: String
) {
    val typeLabel: String
        get() = when (type) {
            SubscriptionType.Vod -> "影视订阅"
            SubscriptionType.Live -> "直播订阅"
        }
}

sealed class ExternalImportParseResult {
    data class Success(val request: ExternalImportRequest) : ExternalImportParseResult()
    data class Failure(val reason: String) : ExternalImportParseResult()
    data object Ignored : ExternalImportParseResult()
}

object ExternalImportParser {
    private const val SCHEME = "watchtogether"
    private const val HOST = "import"

    fun parse(uri: Uri?): ExternalImportParseResult {
        if (uri == null) return ExternalImportParseResult.Ignored
        if (!uri.scheme.equals(SCHEME, ignoreCase = true) || !uri.host.equals(HOST, ignoreCase = true)) {
            return ExternalImportParseResult.Ignored
        }

        val type = when (uri.getQueryParameter("type")?.trim()?.lowercase()) {
            "vod", "video", "movie" -> SubscriptionType.Vod
            "live", "tv" -> SubscriptionType.Live
            null, "" -> return ExternalImportParseResult.Failure("导入链接缺少 type 参数")
            else -> return ExternalImportParseResult.Failure("不支持的订阅类型")
        }
        val name = uri.getQueryParameter("name")?.trim().orEmpty()
        val url = uri.getQueryParameter("url")?.trim().orEmpty()

        if (name.isBlank()) return ExternalImportParseResult.Failure("导入链接缺少 name 参数")
        if (url.isBlank()) return ExternalImportParseResult.Failure("导入链接缺少 url 参数")
        if (!isHttpUrl(url)) return ExternalImportParseResult.Failure("只支持 http 或 https 配置地址")

        return ExternalImportParseResult.Success(
            ExternalImportRequest(
                type = type,
                name = name,
                url = url
            )
        )
    }

    private fun isHttpUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
    }
}
