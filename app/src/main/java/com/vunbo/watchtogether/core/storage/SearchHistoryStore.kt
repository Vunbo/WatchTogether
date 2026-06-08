package com.vunbo.watchtogether.core.storage

object SearchHistoryStore {
    private const val MAX_HISTORY = 12

    fun load(): List<String> {
        val raw = PrefsManager.getString(HawkConfig.SEARCH_HISTORY)
        if (raw.isBlank()) return emptyList()
        return raw.split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_HISTORY)
    }

    fun save(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.length < 2) return
        val updated = buildList {
            add(normalized)
            addAll(load().filterNot { it.equals(normalized, ignoreCase = true) })
        }.take(MAX_HISTORY)
        PrefsManager.putString(HawkConfig.SEARCH_HISTORY, updated.joinToString("\n"))
    }

    fun clear() {
        PrefsManager.remove(HawkConfig.SEARCH_HISTORY)
    }
}
