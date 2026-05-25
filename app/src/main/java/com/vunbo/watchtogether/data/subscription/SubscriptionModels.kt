package com.vunbo.watchtogether.data.subscription

enum class SubscriptionType {
    Vod,
    Live
}

data class SubscriptionGroup(
    val id: String = "",
    val name: String = "",
    val sourceUrl: String = "",
    val type: SubscriptionType = SubscriptionType.Vod,
    val stores: List<SubscriptionStore> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class SubscriptionStore(
    val id: String = "",
    val name: String = "",
    val url: String = ""
)

data class SubscriptionSelection(
    val groupId: String = "",
    val storeId: String = ""
)

data class SubscriptionSummary(
    val title: String = "",
    val subtitle: String = "未配置，点击添加",
    val lastRefreshText: String = "尚未手动刷新"
)

data class SubscriptionApplyResult(
    val success: Boolean,
    val message: String = ""
)

sealed class SubscriptionValidationResult {
    data class Success(
        val group: SubscriptionGroup,
        val applied: Boolean = false,
        val message: String = ""
    ) : SubscriptionValidationResult()
    data class Failure(val reason: String) : SubscriptionValidationResult()
}
