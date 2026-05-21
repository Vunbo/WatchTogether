package com.vunbo.watchtogether.data.live

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vunbo.watchtogether.data.model.Epginfo
import com.vunbo.watchtogether.data.model.LiveChannelItem
import com.vunbo.watchtogether.data.util.HawkConfig
import com.vunbo.watchtogether.data.util.OkHttpHelper
import com.vunbo.watchtogether.data.util.PrefsManager
import java.net.URLEncoder
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class LiveProgramPreview(
    val current: String,
    val next: String?,
    val programs: List<Epginfo> = emptyList()
)

object LiveEpgResolver {
    fun loadCurrentProgram(channelName: String): LiveProgramPreview? {
        return loadPrograms(channelName)
    }

    fun loadPrograms(channelName: String, date: Date = Date()): LiveProgramPreview? {
        val epgUrl = PrefsManager.getString(HawkConfig.EPG_API_URL).trim()
        if (epgUrl.isBlank() || channelName.isBlank()) return null
        val dateText = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(date)
        val encodedName = URLEncoder.encode(channelName, "UTF-8")
        val candidates = buildList {
            if (epgUrl.contains("{name}") || epgUrl.contains("{date}") || epgUrl.contains("{ch}")) {
                add(
                    epgUrl
                        .replace("{name}", encodedName)
                        .replace("{ch}", encodedName)
                        .replace("{date}", dateText)
                )
            }
            val separator = if (epgUrl.contains("?")) "&" else "?"
            add("$epgUrl${separator}ch=$encodedName&date=$dateText")
        }.distinct()

        candidates.forEach { url ->
            val body = OkHttpHelper.getBodyForParser(url) ?: return@forEach
            parseProgram(body, date)?.let { return it }
        }
        return null
    }

    fun buildCatchupUrl(channel: LiveChannelItem, program: Epginfo, liveUrl: String): String? {
        val template = channel.catchupSource?.takeIf { it.isNotBlank() }
            ?: channel.catchup?.takeIf { it.contains("{") || it.contains("\$") }
            ?: return null
        val startDate = parseProgramTime(program.start) ?: return null
        val endDate = parseProgramTime(program.end) ?: Date(startDate.time + DEFAULT_CATCHUP_DURATION_MS)
        val duration = ((endDate.time - startDate.time) / 1000).coerceAtLeast(1)
        val startSeconds = startDate.time / 1000
        val endSeconds = endDate.time / 1000
        val startText = CATCHUP_TIME_FORMAT.format(startDate)
        val endText = CATCHUP_TIME_FORMAT.format(endDate)
        return template
            .replace("{url}", liveUrl)
            .replace("{name}", channel.name)
            .replace("{tvg-id}", channel.tvgId.orEmpty())
            .replace("{tvgId}", channel.tvgId.orEmpty())
            .replace("{start}", startSeconds.toString())
            .replace("{end}", endSeconds.toString())
            .replace("{duration}", duration.toString())
            .replace("{utc}", startText)
            .replace("{utcend}", endText)
            .replace("\${start}", startSeconds.toString())
            .replace("\${end}", endSeconds.toString())
            .replace("\${duration}", duration.toString())
            .takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    fun isPast(program: Epginfo): Boolean {
        val end = parseProgramTime(program.end) ?: return false
        return end.before(Date())
    }

    private fun parseProgram(body: String, date: Date): LiveProgramPreview? {
        val root = runCatching { JsonParser.parseString(body) }.getOrNull() ?: return null
        val array = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> root.asJsonObject.findProgramArray()
            else -> null
        } ?: return null
        val items = array.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val obj = element.asJsonObject
            val start = obj.string("start") ?: obj.string("starttime") ?: obj.string("time")
            val end = obj.string("end") ?: obj.string("endtime")
            val title = obj.string("title") ?: obj.string("name") ?: obj.string("program")
            val desc = obj.string("desc") ?: obj.string("description")
            if (title.isNullOrBlank()) null else Epginfo(
                start = normalizeProgramTime(start.orEmpty(), date),
                end = normalizeProgramTime(end.orEmpty(), date),
                title = title,
                desc = desc
            )
        }
        if (items.isEmpty()) return null
        val nowText = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date())
        val currentIndex = items.indexOfLast { item ->
            val start = item.start.takeLast(5)
            start.length == 5 && start <= nowText
        }.takeIf { it >= 0 } ?: 0
        val current = items[currentIndex]
        val next = items.getOrNull(currentIndex + 1)
        return LiveProgramPreview(
            current = formatProgram("正在播", current),
            next = next?.let { formatProgram("即将播", it) },
            programs = items
        )
    }

    private fun JsonObject.findProgramArray(): JsonArray? {
        listOf("epg_data", "data", "list", "programs", "items").forEach { key ->
            val value = get(key) ?: return@forEach
            if (value.isJsonArray) return value.asJsonArray
            if (value.isJsonObject) value.asJsonObject.findProgramArray()?.let { return it }
        }
        return null
    }

    private fun JsonObject.string(key: String): String? {
        val value = get(key) ?: return null
        return if (value.isJsonPrimitive) value.asString.trim().takeIf { it.isNotBlank() } else null
    }

    private fun formatProgram(prefix: String, item: Epginfo): String {
        val time = item.start.takeLast(5).takeIf { it.length == 5 }
        return listOfNotNull(prefix, time, item.title).joinToString(" ")
    }

    private fun normalizeProgramTime(value: String, date: Date): String {
        val clean = value.trim()
        if (clean.isBlank()) return ""
        if (FULL_TIME_FORMAT.parse(clean, ParsePosition(0)) != null) return clean
        val time = clean.takeLast(5).takeIf { it.matches(Regex("""\d{2}:\d{2}""")) } ?: return clean
        val dateText = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(date)
        return "$dateText $time"
    }

    private fun parseProgramTime(value: String): Date? {
        val clean = value.trim()
        if (clean.isBlank()) return null
        FULL_TIME_FORMAT.parse(clean, ParsePosition(0))?.let { return it }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        return FULL_TIME_FORMAT.parse("$today ${clean.takeLast(5)}", ParsePosition(0))
    }

    private val FULL_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val CATCHUP_TIME_FORMAT = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val DEFAULT_CATCHUP_DURATION_MS = 30L * 60L * 1000L
}
