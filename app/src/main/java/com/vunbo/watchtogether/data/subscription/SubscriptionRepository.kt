package com.vunbo.watchtogether.data.subscription

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vunbo.watchtogether.data.source.ApiConfig
import com.vunbo.watchtogether.data.live.LiveRepository
import com.vunbo.watchtogether.data.live.LiveSourceLoader
import com.vunbo.watchtogether.data.model.LiveSourceEntry
import com.vunbo.watchtogether.core.event.AppEvent
import com.vunbo.watchtogether.core.event.AppEventBus
import com.vunbo.watchtogether.core.storage.HawkConfig
import com.vunbo.watchtogether.core.util.MD5
import com.vunbo.watchtogether.core.storage.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubscriptionRepository(
    private val apiConfig: ApiConfig = ApiConfig.get(),
    private val liveRepository: LiveRepository = LiveRepository(apiConfig)
) {
    private val gson = Gson()

    fun getGroups(type: SubscriptionType): List<SubscriptionGroup> {
        migrateLegacyIfNeeded(type)
        return loadGroups(type)
    }

    fun getSelection(type: SubscriptionType): SubscriptionSelection {
        return SubscriptionSelection(
            groupId = PrefsManager.getString(selectedGroupKey(type)),
            storeId = PrefsManager.getString(selectedStoreKey(type))
        )
    }

    fun getSummary(type: SubscriptionType): SubscriptionSummary {
        val groups = getGroups(type)
        val selection = getSelection(type)
        val group = groups.firstOrNull { it.id == selection.groupId } ?: groups.firstOrNull()
        val store = group?.stores?.firstOrNull { it.id == selection.storeId } ?: group?.stores?.firstOrNull()
        return if (group != null && store != null) {
            SubscriptionSummary(
                title = store.name.ifBlank { group.name },
                subtitle = "${group.name.ifBlank { DEFAULT_GROUP_NAME }} · ${store.name.ifBlank { store.url }}",
                lastRefreshText = lastRefreshText(type)
            )
        } else {
            SubscriptionSummary()
        }
    }

    suspend fun addSubscription(
        type: SubscriptionType,
        name: String,
        url: String
    ): SubscriptionValidationResult = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        if (cleanName.isBlank()) return@withContext SubscriptionValidationResult.Failure("请填写配置名称")
        if (cleanUrl.isBlank()) return@withContext SubscriptionValidationResult.Failure("请填写配置地址")
        if (!looksLikeUrl(cleanUrl)) return@withContext SubscriptionValidationResult.Failure("地址格式不可用")

        val validation = when (type) {
            SubscriptionType.Vod -> validateVod(cleanName, cleanUrl)
            SubscriptionType.Live -> validateLive(cleanName, cleanUrl)
        }
        if (validation !is SubscriptionValidationResult.Success) return@withContext validation

        val group = upsertGroup(type, validation.group)
        val storeId = validation.group.stores.firstOrNull()?.id.orEmpty()
        val currentSelection = getSelection(type)
        val shouldAutoSelect = currentSelection.groupId.isBlank() || currentSelection.storeId.isBlank()
        val applyResult = if (shouldAutoSelect && storeId.isNotBlank()) {
            selectStore(type, group.id, storeId)
        } else {
            SubscriptionApplyResult(success = false, message = "已添加，请在列表中选择需要使用的线路")
        }
        if (!shouldAutoSelect || !applyResult.success) {
            AppEventBus.post(AppEvent.SubscriptionChanged(type.name))
        }
        SubscriptionValidationResult.Success(
            group = group,
            applied = applyResult.success,
            message = if (applyResult.success) "已添加并切换成功" else applyResult.message.ifBlank { "已添加，请手动选择可用线路" }
        )
    }

    suspend fun selectStore(
        type: SubscriptionType,
        groupId: String,
        storeId: String
    ): SubscriptionApplyResult = withContext(Dispatchers.IO) {
        val group = getGroups(type).firstOrNull { it.id == groupId }
            ?: return@withContext SubscriptionApplyResult(false, "订阅配置不存在")
        val store = group.stores.firstOrNull { it.id == storeId }
            ?: return@withContext SubscriptionApplyResult(false, "线路不存在")
        when (type) {
            SubscriptionType.Vod -> selectVodStore(group, store)
            SubscriptionType.Live -> selectLiveStore(group, store)
        }
    }

    suspend fun deleteGroup(type: SubscriptionType, groupId: String): SubscriptionApplyResult = withContext(Dispatchers.IO) {
        val before = getSelection(type)
        val groups = getGroups(type).filterNot { it.id == groupId }
        saveGroups(type, groups)
        if (before.groupId != groupId) {
            AppEventBus.post(AppEvent.SubscriptionChanged(type.name))
            return@withContext SubscriptionApplyResult(true, "已删除")
        }
        applyFallbackSelection(type, groups)
    }

    suspend fun deleteStore(type: SubscriptionType, groupId: String, storeId: String): SubscriptionApplyResult = withContext(Dispatchers.IO) {
        val before = getSelection(type)
        val groups = getGroups(type).mapNotNull { group ->
            if (group.id != groupId) {
                group
            } else {
                val stores = group.stores.filterNot { it.id == storeId }
                group.copy(stores = stores).takeIf { stores.isNotEmpty() }
            }
        }
        saveGroups(type, groups)
        if (before.groupId != groupId || before.storeId != storeId) {
            AppEventBus.post(AppEvent.SubscriptionChanged(type.name))
            return@withContext SubscriptionApplyResult(true, "已删除")
        }
        applyFallbackSelection(type, groups)
    }

    suspend fun refreshSelected(type: SubscriptionType): SubscriptionApplyResult = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded(type)
        val selection = getSelection(type)
        val groups = getGroups(type)
        val group = groups.firstOrNull { it.id == selection.groupId }
            ?: return@withContext SubscriptionApplyResult(false, "请先选择需要刷新的订阅")
        val store = group.stores.firstOrNull { it.id == selection.storeId }
            ?: group.stores.firstOrNull()
            ?: return@withContext SubscriptionApplyResult(false, "当前订阅没有可用线路")

        when (type) {
            SubscriptionType.Vod -> refreshSelectedVod(group, store)
            SubscriptionType.Live -> refreshSelectedLive(group, store)
        }
    }

    private suspend fun validateVod(name: String, url: String): SubscriptionValidationResult {
        val stores = apiConfig.previewApiStores(url)
        val group = if (stores.isNotEmpty()) {
            SubscriptionGroup(
                id = buildGroupId(SubscriptionType.Vod, url),
                name = name,
                sourceUrl = url,
                type = SubscriptionType.Vod,
                stores = stores.map { store ->
                    SubscriptionStore(
                        id = buildStoreId(store.url),
                        name = store.name.ifBlank { store.url },
                        url = store.url
                    )
                }
            )
        } else {
            val snapshot = ConfigSnapshot.capture()
            try {
                PrefsManager.putString(HawkConfig.API_URL, url)
                PrefsManager.remove(HawkConfig.API_STORE_LIST)
                PrefsManager.remove(HawkConfig.API_STORE_SELECTED)
                PrefsManager.remove(HawkConfig.API_EFFECTIVE_URL)
                val success = withTimeoutOrNull(VOD_VALIDATE_TIMEOUT_MS) {
                    apiConfig.loadConfig(useCache = false, forceReload = true)
                } == true
                if (!success || apiConfig.sourceBeanList.isEmpty()) {
                    return SubscriptionValidationResult.Failure("配置加载失败，请检查地址是否可用")
                }
            } catch (e: Exception) {
                return SubscriptionValidationResult.Failure("配置验证失败：${e.message ?: "未知错误"}")
            } finally {
                snapshot.restore()
                apiConfig.loadConfig(useCache = true, forceReload = false)
            }
            SubscriptionGroup(
                id = fixedGroupId(SubscriptionType.Vod),
                name = DEFAULT_GROUP_NAME,
                sourceUrl = "",
                type = SubscriptionType.Vod,
                stores = listOf(
                    SubscriptionStore(
                        id = buildStoreId(url),
                        name = name,
                        url = url
                    )
                )
            )
        }
        return SubscriptionValidationResult.Success(group)
    }

    private suspend fun validateLive(name: String, url: String): SubscriptionValidationResult {
        val groups = withTimeoutOrNull(LIVE_VALIDATE_TIMEOUT_MS) {
            LiveSourceLoader.load(LiveSourceEntry(name = name, url = url), baseUrl = "")
        }.orEmpty()
        if (groups.isEmpty() || groups.sumOf { it.channels.size } == 0) {
            return SubscriptionValidationResult.Failure("直播源不可用或未解析到频道")
        }
        val group = SubscriptionGroup(
            id = fixedGroupId(SubscriptionType.Live),
            name = DEFAULT_GROUP_NAME,
            sourceUrl = "",
            type = SubscriptionType.Live,
            stores = listOf(
                SubscriptionStore(
                    id = buildStoreId(url),
                    name = name,
                    url = url
                )
            )
        )
        return SubscriptionValidationResult.Success(group)
    }

    private suspend fun selectVodStore(group: SubscriptionGroup, store: SubscriptionStore): SubscriptionApplyResult {
        if (group.name == DEFAULT_GROUP_NAME) {
            PrefsManager.putString(HawkConfig.API_URL, store.url)
            PrefsManager.remove(HawkConfig.API_STORE_LIST)
            PrefsManager.remove(HawkConfig.API_STORE_SELECTED)
            PrefsManager.remove(HawkConfig.API_EFFECTIVE_URL)
        } else {
            PrefsManager.putString(HawkConfig.API_URL, group.sourceUrl.ifBlank { store.url })
            saveApiStoresForGroup(group)
            PrefsManager.putString(HawkConfig.API_STORE_SELECTED, store.url)
            PrefsManager.putString(HawkConfig.API_EFFECTIVE_URL, store.url)
        }
        val success = apiConfig.loadConfig(useCache = true, forceReload = false)
        if (success && apiConfig.homeSource != null) {
            PrefsManager.putString(HawkConfig.VOD_SUBSCRIPTION_SELECTED_GROUP, group.id)
            PrefsManager.putString(HawkConfig.VOD_SUBSCRIPTION_SELECTED_STORE, store.id)
            liveRepository.warmUp(forceRefresh = false)
            AppEventBus.post(AppEvent.SubscriptionChanged(SubscriptionType.Vod.name))
            AppEventBus.post(AppEvent.ApiUrlChange(store.url))
            return SubscriptionApplyResult(true, "影视订阅已切换")
        }
        return SubscriptionApplyResult(false, "该仓库加载失败，请检查地址是否可用")
    }

    private suspend fun refreshSelectedVod(
        group: SubscriptionGroup,
        store: SubscriptionStore
    ): SubscriptionApplyResult {
        val targetGroup = if (group.name == DEFAULT_GROUP_NAME || group.sourceUrl.isBlank()) {
            group
        } else {
            val stores = apiConfig.previewApiStores(group.sourceUrl)
            if (stores.isEmpty()) {
                return SubscriptionApplyResult(false, "多仓列表刷新失败，已保留当前缓存")
            }
            group.copy(
                stores = stores.map { apiStore ->
                    SubscriptionStore(
                        id = buildStoreId(apiStore.url),
                        name = apiStore.name.ifBlank { apiStore.url },
                        url = apiStore.url
                    )
                }
            )
        }
        val targetStore = targetGroup.stores.firstOrNull { it.url == store.url }
            ?: targetGroup.stores.firstOrNull()
            ?: return SubscriptionApplyResult(false, "刷新失败，当前订阅没有可用仓库")
        val snapshot = ConfigSnapshot.capture()
        val result = forceSelectVodStore(targetGroup, targetStore)
        if (result.success) {
            replaceGroup(SubscriptionType.Vod, targetGroup)
            saveLastRefreshTime(SubscriptionType.Vod)
            val switchedMessage = if (targetStore.url != store.url) {
                "原仓库已失效，已切换到 ${targetStore.name.ifBlank { targetStore.url }}"
            } else {
                "影视订阅已刷新"
            }
            AppEventBus.post(AppEvent.SubscriptionChanged(SubscriptionType.Vod.name))
            AppEventBus.post(AppEvent.ApiUrlChange(targetStore.url))
            return result.copy(message = switchedMessage)
        }
        snapshot.restore()
        apiConfig.loadConfig(useCache = true, forceReload = false)
        return result.copy(message = result.message.ifBlank { "影视订阅刷新失败，已保留当前缓存" })
    }

    private suspend fun forceSelectVodStore(
        group: SubscriptionGroup,
        store: SubscriptionStore
    ): SubscriptionApplyResult {
        if (group.name == DEFAULT_GROUP_NAME) {
            PrefsManager.putString(HawkConfig.API_URL, store.url)
            PrefsManager.remove(HawkConfig.API_STORE_LIST)
            PrefsManager.remove(HawkConfig.API_STORE_SELECTED)
            PrefsManager.remove(HawkConfig.API_EFFECTIVE_URL)
        } else {
            PrefsManager.putString(HawkConfig.API_URL, group.sourceUrl.ifBlank { store.url })
            saveApiStoresForGroup(group)
            PrefsManager.putString(HawkConfig.API_STORE_SELECTED, store.url)
            PrefsManager.putString(HawkConfig.API_EFFECTIVE_URL, store.url)
        }
        val success = apiConfig.loadConfig(useCache = false, forceReload = true)
        return if (success && apiConfig.homeSource != null) {
            PrefsManager.putString(HawkConfig.VOD_SUBSCRIPTION_SELECTED_GROUP, group.id)
            PrefsManager.putString(HawkConfig.VOD_SUBSCRIPTION_SELECTED_STORE, store.id)
            try {
                liveRepository.warmUp(forceRefresh = true)
            } catch (_: Exception) {
                // 影视订阅刷新成功后仅尽力刷新直播缓存，不能反向影响影视订阅结果。
            }
            SubscriptionApplyResult(true, "影视订阅已刷新")
        } else {
            SubscriptionApplyResult(false, "影视订阅刷新失败，已保留当前缓存")
        }
    }

    private suspend fun selectLiveStore(group: SubscriptionGroup, store: SubscriptionStore): SubscriptionApplyResult {
        PrefsManager.putString(HawkConfig.LIVE_API_URL, store.url)
        PrefsManager.putString(HawkConfig.LIVE_SUBSCRIPTION_SELECTED_GROUP, group.id)
        PrefsManager.putString(HawkConfig.LIVE_SUBSCRIPTION_SELECTED_STORE, store.id)
        PrefsManager.remove(HawkConfig.LIVE_SOURCE_ID)
        PrefsManager.putInt(HawkConfig.LIVE_SOURCE_INDEX, 0)
        PrefsManager.putInt(HawkConfig.LIVE_GROUP_INDEX, 0)
        PrefsManager.putInt(HawkConfig.LIVE_LINE_INDEX, 0)
        val ok = liveRepository.warmUp(forceRefresh = false)
        AppEventBus.post(AppEvent.SubscriptionChanged(SubscriptionType.Live.name))
        AppEventBus.post(AppEvent.LiveSourceChange(store.url))
        return if (ok) {
            SubscriptionApplyResult(true, "直播订阅已切换")
        } else {
            SubscriptionApplyResult(false, "直播源加载失败，请检查地址是否可用")
        }
    }

    private suspend fun refreshSelectedLive(
        group: SubscriptionGroup,
        store: SubscriptionStore
    ): SubscriptionApplyResult {
        val previousLiveUrl = PrefsManager.getString(HawkConfig.LIVE_API_URL)
        val previousGroup = PrefsManager.getString(HawkConfig.LIVE_SUBSCRIPTION_SELECTED_GROUP)
        val previousStore = PrefsManager.getString(HawkConfig.LIVE_SUBSCRIPTION_SELECTED_STORE)
        PrefsManager.putString(HawkConfig.LIVE_API_URL, store.url)
        val ok = liveRepository.warmUp(forceRefresh = true)
        return if (ok) {
            PrefsManager.putString(HawkConfig.LIVE_SUBSCRIPTION_SELECTED_GROUP, group.id)
            PrefsManager.putString(HawkConfig.LIVE_SUBSCRIPTION_SELECTED_STORE, store.id)
            saveLastRefreshTime(SubscriptionType.Live)
            AppEventBus.post(AppEvent.SubscriptionChanged(SubscriptionType.Live.name))
            AppEventBus.post(AppEvent.LiveSourceChange(store.url))
            SubscriptionApplyResult(true, "直播订阅已刷新")
        } else {
            PrefsManager.putString(HawkConfig.LIVE_API_URL, previousLiveUrl)
            PrefsManager.putString(HawkConfig.LIVE_SUBSCRIPTION_SELECTED_GROUP, previousGroup)
            PrefsManager.putString(HawkConfig.LIVE_SUBSCRIPTION_SELECTED_STORE, previousStore)
            SubscriptionApplyResult(false, "直播订阅刷新失败，已保留当前缓存")
        }
    }

    private fun upsertGroup(type: SubscriptionType, group: SubscriptionGroup): SubscriptionGroup {
        val groups = getGroups(type)
        val nextGroup = if (group.name == DEFAULT_GROUP_NAME) {
            val existing = groups.firstOrNull { it.id == fixedGroupId(type) }
            val stores = ((existing?.stores.orEmpty() + group.stores)
                .filter { it.url.isNotBlank() })
                .distinctBy { it.url }
            (existing ?: group.copy(id = fixedGroupId(type), sourceUrl = "")).copy(
                name = DEFAULT_GROUP_NAME,
                sourceUrl = "",
                stores = stores
            )
        } else {
            group
        }
        val next = (groups.filterNot { it.id == nextGroup.id || (it.sourceUrl.isNotBlank() && it.sourceUrl == nextGroup.sourceUrl) } + nextGroup)
            .sortedBy { it.createdAt }
        saveGroups(type, next)
        return nextGroup
    }

    private suspend fun applyFallbackSelection(
        type: SubscriptionType,
        groups: List<SubscriptionGroup>
    ): SubscriptionApplyResult {
        val fallbackGroup = groups.firstOrNull()
        val fallbackStore = fallbackGroup?.stores?.firstOrNull()
        return if (fallbackGroup != null && fallbackStore != null) {
            selectStore(type, fallbackGroup.id, fallbackStore.id)
        } else {
            clearActiveSubscription(type)
            SubscriptionApplyResult(true, "已清空订阅")
        }
    }

    private fun clearActiveSubscription(type: SubscriptionType) {
        PrefsManager.putString(selectedGroupKey(type), "")
        PrefsManager.putString(selectedStoreKey(type), "")
        when (type) {
            SubscriptionType.Vod -> {
                PrefsManager.remove(HawkConfig.API_URL)
                PrefsManager.remove(HawkConfig.API_EFFECTIVE_URL)
                PrefsManager.remove(HawkConfig.API_STORE_LIST)
                PrefsManager.remove(HawkConfig.API_STORE_SELECTED)
                apiConfig.clearConfigState()
                AppEventBus.post(AppEvent.SubscriptionChanged(type.name))
                AppEventBus.post(AppEvent.ApiUrlChange(""))
            }
            SubscriptionType.Live -> {
                PrefsManager.remove(HawkConfig.LIVE_API_URL)
                PrefsManager.remove(HawkConfig.LIVE_SOURCE_ID)
                PrefsManager.putInt(HawkConfig.LIVE_SOURCE_INDEX, 0)
                PrefsManager.putInt(HawkConfig.LIVE_GROUP_INDEX, 0)
                PrefsManager.putInt(HawkConfig.LIVE_LINE_INDEX, 0)
                AppEventBus.post(AppEvent.SubscriptionChanged(type.name))
                AppEventBus.post(AppEvent.LiveSourceChange(""))
            }
        }
    }

    private fun migrateLegacyIfNeeded(type: SubscriptionType) {
        if (loadGroups(type).isNotEmpty()) return
        when (type) {
            SubscriptionType.Vod -> {
                val apiUrl = PrefsManager.getString(HawkConfig.API_URL).trim()
                if (apiUrl.isBlank()) return
                val stores = apiConfig.getSavedApiStores()
                val group = if (stores.isNotEmpty()) {
                    SubscriptionGroup(
                        id = buildGroupId(type, apiUrl),
                        name = "默认影视订阅",
                        sourceUrl = apiUrl,
                        type = type,
                        stores = stores.map { store ->
                            SubscriptionStore(buildStoreId(store.url), store.name.ifBlank { store.url }, store.url)
                        }
                    )
                } else {
                    SubscriptionGroup(
                        id = fixedGroupId(type),
                        name = DEFAULT_GROUP_NAME,
                        sourceUrl = "",
                        type = type,
                        stores = listOf(SubscriptionStore(buildStoreId(apiUrl), "默认影视订阅", apiUrl))
                    )
                }
                saveGroups(type, listOf(group))
                PrefsManager.putString(selectedGroupKey(type), group.id)
                PrefsManager.putString(selectedStoreKey(type), group.stores.firstOrNull()?.id.orEmpty())
            }
            SubscriptionType.Live -> {
                val liveUrl = PrefsManager.getString(HawkConfig.LIVE_API_URL).trim()
                if (liveUrl.isBlank()) return
                val group = SubscriptionGroup(
                    id = fixedGroupId(type),
                    name = DEFAULT_GROUP_NAME,
                    sourceUrl = "",
                    type = type,
                    stores = listOf(SubscriptionStore(buildStoreId(liveUrl), "默认直播源", liveUrl))
                )
                saveGroups(type, listOf(group))
                PrefsManager.putString(selectedGroupKey(type), group.id)
                PrefsManager.putString(selectedStoreKey(type), group.stores.firstOrNull()?.id.orEmpty())
            }
        }
    }

    private fun loadGroups(type: SubscriptionType): List<SubscriptionGroup> {
        val json = PrefsManager.getString(groupsKey(type))
        if (json.isBlank()) return emptyList()
        return runCatching {
            val typeToken = object : TypeToken<List<SubscriptionGroup>>() {}.type
            gson.fromJson<List<SubscriptionGroup>>(json, typeToken).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun saveGroups(type: SubscriptionType, groups: List<SubscriptionGroup>) {
        PrefsManager.putString(groupsKey(type), gson.toJson(groups))
    }

    private fun replaceGroup(type: SubscriptionType, group: SubscriptionGroup) {
        val groups = getGroups(type).map { current ->
            if (current.id == group.id) group else current
        }
        saveGroups(type, groups.ifEmpty { listOf(group) })
    }

    private fun saveLastRefreshTime(type: SubscriptionType) {
        PrefsManager.putLong(lastRefreshKey(type), System.currentTimeMillis())
    }

    private fun lastRefreshText(type: SubscriptionType): String {
        val timestamp = PrefsManager.getLong(lastRefreshKey(type), 0L)
        if (timestamp <= 0L) return "尚未手动刷新"
        val text = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
        return "上次更新：$text"
    }

    private fun saveApiStoresForGroup(group: SubscriptionGroup) {
        val root = com.google.gson.JsonObject()
        val urls = com.google.gson.JsonArray()
        group.stores.forEach { store ->
            urls.add(com.google.gson.JsonObject().apply {
                addProperty("name", store.name)
                addProperty("url", store.url)
            })
        }
        root.add("urls", urls)
        PrefsManager.putString(HawkConfig.API_STORE_LIST, root.toString())
    }

    private fun groupsKey(type: SubscriptionType): String = when (type) {
        SubscriptionType.Vod -> HawkConfig.VOD_SUBSCRIPTION_GROUPS
        SubscriptionType.Live -> HawkConfig.LIVE_SUBSCRIPTION_GROUPS
    }

    private fun selectedGroupKey(type: SubscriptionType): String = when (type) {
        SubscriptionType.Vod -> HawkConfig.VOD_SUBSCRIPTION_SELECTED_GROUP
        SubscriptionType.Live -> HawkConfig.LIVE_SUBSCRIPTION_SELECTED_GROUP
    }

    private fun selectedStoreKey(type: SubscriptionType): String = when (type) {
        SubscriptionType.Vod -> HawkConfig.VOD_SUBSCRIPTION_SELECTED_STORE
        SubscriptionType.Live -> HawkConfig.LIVE_SUBSCRIPTION_SELECTED_STORE
    }

    private fun lastRefreshKey(type: SubscriptionType): String = when (type) {
        SubscriptionType.Vod -> HawkConfig.VOD_SUBSCRIPTION_LAST_REFRESH
        SubscriptionType.Live -> HawkConfig.LIVE_SUBSCRIPTION_LAST_REFRESH
    }

    private fun buildGroupId(type: SubscriptionType, url: String): String {
        return "${type.name.lowercase()}_${MD5.encode(url.trim())}"
    }

    private fun fixedGroupId(type: SubscriptionType): String = "${type.name.lowercase()}_collection"

    private fun buildStoreId(url: String): String = MD5.encode(url.trim())

    private fun looksLikeUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
    }

    private data class ConfigSnapshot(
        val apiUrl: String,
        val effectiveUrl: String,
        val storeList: String,
        val selectedStore: String
    ) {
        fun restore() {
            PrefsManager.putString(HawkConfig.API_URL, apiUrl)
            restoreKey(HawkConfig.API_EFFECTIVE_URL, effectiveUrl)
            restoreKey(HawkConfig.API_STORE_LIST, storeList)
            restoreKey(HawkConfig.API_STORE_SELECTED, selectedStore)
        }

        private fun restoreKey(key: String, value: String) {
            if (value.isBlank()) PrefsManager.remove(key) else PrefsManager.putString(key, value)
        }

        companion object {
            fun capture(): ConfigSnapshot {
                return ConfigSnapshot(
                    apiUrl = PrefsManager.getString(HawkConfig.API_URL),
                    effectiveUrl = PrefsManager.getString(HawkConfig.API_EFFECTIVE_URL),
                    storeList = PrefsManager.getString(HawkConfig.API_STORE_LIST),
                    selectedStore = PrefsManager.getString(HawkConfig.API_STORE_SELECTED)
                )
            }
        }
    }

    companion object {
        const val DEFAULT_GROUP_NAME = "线路合集"
        private const val VOD_VALIDATE_TIMEOUT_MS = 45_000L
        private const val LIVE_VALIDATE_TIMEOUT_MS = 15_000L
    }
}
