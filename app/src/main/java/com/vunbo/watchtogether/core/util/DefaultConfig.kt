package com.vunbo.watchtogether.data.util

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import android.net.Uri

object DefaultConfig {
    val videoFormats = listOf(
        ".mp4", ".m3u8", ".flv", ".avi", ".mkv", ".wmv", ".rmvb", ".mov",
        ".ts", ".webm", ".mpg", ".mpeg", ".ogv", ".3gp", ".f4v", ".asf"
    )

    private val sniffPattern = Regex(
        """(?i)(https?://.+\.(m3u8|mp4|flv|avi|mkv|wmv|rmvb|mov|ts|webm|mpg|mpeg|ogv|3gp|f4v|asf)(\?.*)?$)|(https?://.+/(download|video|play|stream)([/?].*)?$)"""
    )

    fun isVideoFormat(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return false
            val path = uri.path?.lowercase().orEmpty()
            if (path.isEmpty()) return false
            sniffPattern.containsMatchIn(url) || videoFormats.any { path.endsWith(it) }
        } catch (e: Exception) {
            false
        }
    }

    fun isVideoFormatSuffix(url: String): Boolean {
        val path = url.split("?")[0].split("#")[0].lowercase()
        return videoFormats.any { path.endsWith(it) }
    }

    /** 安全地从 JsonObject 获取字符串，如果值是对象/数组则用 toString() */
    fun safeJsonString(obj: JsonObject, key: String, defaultVal: String = ""): String {
        return try {
            if (!obj.has(key)) return defaultVal
            val el: JsonElement = obj.get(key)
            if (el.isJsonObject || el.isJsonArray) el.toString().trim()
            else el.asString.trim()
        } catch (e: Exception) {
            defaultVal
        }
    }

    /** 安全地从 JsonObject 获取整数 */
    fun safeJsonInt(obj: JsonObject, key: String, defaultVal: Int = 0): Int {
        return try {
            if (!obj.has(key)) return defaultVal
            obj.getAsJsonPrimitive(key).asInt
        } catch (e: Exception) {
            defaultVal
        }
    }

    /** 安全地从 JsonObject 获取字符串列表，支持数组和逗号分隔的字符串 */
    fun safeJsonStringList(obj: JsonObject, key: String): List<String> {
        return try {
            if (!obj.has(key)) return emptyList()
            val el = obj.get(key)
            if (el.isJsonArray) {
                el.asJsonArray.map { it.asString }
            } else if (el.isJsonPrimitive) {
                el.asString.split(",").map { it.trim() }
            } else {
                listOf(el.toString())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
