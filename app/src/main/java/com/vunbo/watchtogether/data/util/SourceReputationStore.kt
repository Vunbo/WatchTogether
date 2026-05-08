package com.vunbo.watchtogether.data.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

data class SourceReputation(
    var sourceKey: String = "",
    var playSuccessCount: Int = 0,
    var playFailCount: Int = 0,
    var detailFailCount: Int = 0,
    var lastPlaySuccessAt: Long = 0L,
    var lastFailAt: Long = 0L
)

object SourceReputationStore {
    private const val KEY_PREFIX = "source_reputation_"
    private const val MAX_RECORDS = 300
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<SourceReputation>>() {}.type

    fun currentSubscriptionHash(): String {
        return hash(PrefsManager.getString(HawkConfig.API_URL))
    }

    fun load(subscriptionHash: String = currentSubscriptionHash()): Map<String, SourceReputation> {
        val raw = PrefsManager.getString(KEY_PREFIX + subscriptionHash)
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val records: MutableList<SourceReputation> = gson.fromJson(raw, listType) ?: mutableListOf()
            records.filter { it.sourceKey.isNotBlank() }.associateBy { it.sourceKey }
        }.getOrDefault(emptyMap())
    }

    fun recordPlaySuccess(sourceKey: String) {
        if (sourceKey.isBlank()) return
        update(sourceKey) { record, now ->
            record.playSuccessCount += 1
            record.lastPlaySuccessAt = now
        }
    }

    fun recordPlayFailure(sourceKey: String) {
        if (sourceKey.isBlank()) return
        update(sourceKey) { record, now ->
            record.playFailCount += 1
            record.lastFailAt = now
        }
    }

    fun recordDetailFailure(sourceKey: String) {
        if (sourceKey.isBlank()) return
        update(sourceKey) { record, now ->
            record.detailFailCount += 1
            record.lastFailAt = now
        }
    }

    private fun update(sourceKey: String, block: (SourceReputation, Long) -> Unit) {
        val subscriptionHash = currentSubscriptionHash()
        val records = load(subscriptionHash).toMutableMap()
        val record = records[sourceKey] ?: SourceReputation(sourceKey = sourceKey)
        block(record, System.currentTimeMillis())
        records[sourceKey] = record
        save(subscriptionHash, records.values.toList())
    }

    private fun save(subscriptionHash: String, records: List<SourceReputation>) {
        val trimmed = records
            .sortedWith(
                compareByDescending<SourceReputation> { it.lastPlaySuccessAt }
                    .thenByDescending { it.lastFailAt }
                    .thenBy { it.sourceKey }
            )
            .take(MAX_RECORDS)
        PrefsManager.putString(KEY_PREFIX + subscriptionHash, gson.toJson(trimmed))
    }

    private fun hash(value: String): String {
        if (value.isBlank()) return "default"
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }
}
