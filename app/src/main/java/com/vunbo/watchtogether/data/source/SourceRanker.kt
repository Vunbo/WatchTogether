package com.vunbo.watchtogether.data.source

import com.vunbo.watchtogether.data.model.Movie
import com.vunbo.watchtogether.data.model.SourceBean
import com.vunbo.watchtogether.data.model.VodSeriesFlag

object SourceRanker {
    private val cloudKeywords = listOf(
        "网盘", "云盘", "阿里", "夸克", "百度", "迅雷", "磁力", "115", "uc",
        "pan", "drive", "aliyun", "quark", "baidu", "magnet", "bt", "ed2k"
    )
    private val lowValueKeywords = listOf("预告", "花絮", "解说", "片段", "资讯", "cut")
    private val qualityKeywords = listOf("4k", "2160", "1080", "蓝光", "超清", "高清", "hd", "完结", "全集")

    fun sortSources(sources: List<SourceBean>, reputation: Map<String, SourceReputation>): List<SourceBean> {
        val scoreCache = sources.associate { source -> source.key to sourceScore(source, reputation[source.key]) }
        return sources.sortedWith(
            compareByDescending<SourceBean> { scoreCache[it.key] ?: 0 }
                .thenBy { source -> source.name.ifBlank { source.key } }
        )
    }

    fun sortSearchResults(
        results: List<Movie.Video>,
        keyword: String,
        sourceNames: Map<String, String>,
        reputation: Map<String, SourceReputation>
    ): List<Movie.Video> {
        val scoreCache = results.associateWith { video ->
            searchScore(
                video = video,
                keyword = keyword,
                sourceName = sourceNames[video.sourceKey.orEmpty()].orEmpty(),
                reputation = reputation[video.sourceKey.orEmpty()]
            )
        }
        return results.sortedWith(
            compareByDescending<Movie.Video> { scoreCache[it] ?: 0 }
                .thenBy { it.name.orEmpty() }
        )
    }

    fun sortFlags(
        sourceKey: String,
        flags: List<VodSeriesFlag>,
        reputation: SourceReputation?
    ): List<VodSeriesFlag> {
        val scoreCache = flags.associateWith { flag -> lineScore(sourceKey, flag.name, flag.url, reputation) }
        return flags.sortedWith(
            compareByDescending<VodSeriesFlag> { scoreCache[it] ?: 0 }
                .thenBy { it.name }
        )
    }

    fun isCloudLike(vararg values: String?): Boolean {
        val label = values.filterNotNull().joinToString(" ").lowercase()
        return cloudKeywords.any { it.lowercase() in label }
    }

    private fun sourceScore(source: SourceBean, reputation: SourceReputation?): Int {
        var score = reputationScore(reputation)
        if (isCloudLike(source.key, source.name, source.api, source.ext, source.playerUrl)) score -= 500
        return score
    }

    private fun searchScore(
        video: Movie.Video,
        keyword: String,
        sourceName: String,
        reputation: SourceReputation?
    ): Int {
        var score = 0
        score += titleScore(video.name.orEmpty(), keyword)
        score += reputationScore(reputation)
        if (isCloudLike(video.sourceKey, sourceName, video.name, video.note, video.tag, video.des)) score -= 500
        if (lowValueKeywords.any { it in video.name.orEmpty().lowercase() || it in video.note.orEmpty().lowercase() }) {
            score -= 120
        }
        val qualityText = listOf(video.name, video.note, video.state, video.tag).joinToString(" ").lowercase()
        if (qualityKeywords.any { it in qualityText }) score += 20
        return score
    }

    private fun lineScore(
        sourceKey: String,
        flagName: String,
        flagUrl: String,
        reputation: SourceReputation?
    ): Int {
        var score = reputationScore(reputation) / 3
        if (isCloudLike(sourceKey, flagName, flagUrl)) score -= 800
        return score
    }

    private fun reputationScore(reputation: SourceReputation?): Int {
        if (reputation == null) return 0
        var score = 0
        score += (reputation.playSuccessCount.coerceAtMost(20) * 45)
        score -= (reputation.playFailCount.coerceAtMost(20) * 55)
        score -= (reputation.detailFailCount.coerceAtMost(20) * 35)
        if (reputation.lastPlaySuccessAt > 0L) {
            val ageDays = ((System.currentTimeMillis() - reputation.lastPlaySuccessAt) / 86_400_000L).coerceAtLeast(0L)
            score += when {
                ageDays <= 3L -> 120
                ageDays <= 14L -> 70
                ageDays <= 45L -> 30
                else -> 0
            }
        }
        return score
    }

    private fun titleScore(title: String, keyword: String): Int {
        val normalizedTitle = normalizeTitle(title)
        val normalizedKeyword = normalizeTitle(keyword)
        if (normalizedKeyword.isBlank()) return 0
        return when {
            normalizedTitle == normalizedKeyword -> 400
            normalizedTitle.removeYear() == normalizedKeyword.removeYear() -> 340
            normalizedTitle.startsWith(normalizedKeyword) -> 260
            normalizedTitle.contains(normalizedKeyword) -> 180
            else -> 0
        }
    }

    private fun normalizeTitle(value: String): String {
        return value.lowercase()
            .replace(Regex("[\\s\\p{Punct}《》【】（）()\\[\\]·._-]+"), "")
            .trim()
    }

    private fun String.removeYear(): String {
        return replace(Regex("(19|20)\\d{2}"), "")
    }
}
