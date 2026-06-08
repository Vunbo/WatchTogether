package com.vunbo.watchtogether.feature.importing

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vunbo.watchtogether.data.source.ApiConfig
import com.vunbo.watchtogether.data.importing.ExternalImportParseResult
import com.vunbo.watchtogether.data.importing.ExternalImportParser
import com.vunbo.watchtogether.data.importing.ExternalImportRequest
import com.vunbo.watchtogether.data.live.LiveRepository
import com.vunbo.watchtogether.data.subscription.SubscriptionRepository
import com.vunbo.watchtogether.data.subscription.SubscriptionValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ExternalImportUiState(
    val request: ExternalImportRequest? = null,
    val loading: Boolean = false,
    val message: String? = null
)

class ExternalImportViewModel : ViewModel() {
    private val apiConfig = ApiConfig.get()
    private val repository = SubscriptionRepository(apiConfig, LiveRepository(apiConfig))
    private val mutex = Mutex()

    private val _uiState = MutableStateFlow(ExternalImportUiState())
    val uiState: StateFlow<ExternalImportUiState> = _uiState.asStateFlow()

    private var lastHandledUri: String = ""

    fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val uriText = uri.toString()
        if (uriText == lastHandledUri) return
        lastHandledUri = uriText

        when (val result = ExternalImportParser.parse(uri)) {
            is ExternalImportParseResult.Success -> {
                _uiState.value = ExternalImportUiState(request = result.request)
            }
            is ExternalImportParseResult.Failure -> {
                _uiState.value = ExternalImportUiState(message = result.reason)
            }
            ExternalImportParseResult.Ignored -> Unit
        }
    }

    fun confirmImport() {
        val request = _uiState.value.request ?: return
        if (_uiState.value.loading) return
        _uiState.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            mutex.withLock {
                try {
                    when (val result = repository.addSubscription(request.type, request.name, request.url)) {
                        is SubscriptionValidationResult.Success -> {
                            _uiState.value = ExternalImportUiState(
                                message = "${request.typeLabel}导入成功：${request.name}，${result.message}"
                            )
                        }
                        is SubscriptionValidationResult.Failure -> {
                            _uiState.value = ExternalImportUiState(message = "导入失败：${result.reason}")
                        }
                    }
                } catch (e: Exception) {
                    _uiState.value = ExternalImportUiState(message = "导入失败：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    fun dismissImport() {
        lastHandledUri = ""
        _uiState.update { it.copy(request = null, loading = false) }
    }

    fun clearMessage() {
        lastHandledUri = ""
        _uiState.update { it.copy(message = null) }
    }
}
