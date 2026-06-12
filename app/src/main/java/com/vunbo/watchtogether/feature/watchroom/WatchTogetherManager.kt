package com.vunbo.watchtogether.feature.watchroom

import android.os.Handler
import android.os.Looper
import com.vunbo.watchtogether.core.storage.HawkConfig
import com.vunbo.watchtogether.core.storage.PrefsManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class WatchTogetherManager {
    private var webSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val userId = UUID.randomUUID().toString().take(8)
    private val userName = buildUserName()
    private var currentRoomCode: String = ""
    private var currentServerUrl: String = ""
    private var isHost: Boolean = false
    private var userLeaving = false
    private var reconnectAttempts = 0
    private var reconnectRunnable: Runnable? = null
    private var lastMediaState: MediaSyncState? = null
    private var onRoomState: ((RoomState) -> Unit)? = null
    private var onSync: ((MediaSyncState) -> Unit)? = null
    private var onChat: ((ChatMessage) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onConnected: ((WebSocket) -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun createRoom(
        serverUrl: String,
        mediaState: MediaSyncState,
        onState: (RoomState) -> Unit,
        onSyncMessage: (MediaSyncState) -> Unit,
        onChatMessage: (ChatMessage) -> Unit,
        onErrorMessage: (String) -> Unit
    ) {
        val roomCode = generateRoomCode()
        lastMediaState = mediaState
        connect(
            serverUrl = serverUrl,
            roomCode = roomCode,
            host = true,
            create = true,
            onState = onState,
            onSyncMessage = onSyncMessage,
            onChatMessage = onChatMessage,
            onErrorMessage = onErrorMessage
        ) { ws ->
            sendJoin(ws, roomCode, create = true, mediaState = mediaState)
            syncPlayback(mediaState.copy(action = "media_change"))
        }
    }

    fun joinRoom(
        serverUrl: String,
        roomCode: String,
        onState: (RoomState) -> Unit,
        onSyncMessage: (MediaSyncState) -> Unit,
        onChatMessage: (ChatMessage) -> Unit,
        onErrorMessage: (String) -> Unit
    ) {
        connect(
            serverUrl = serverUrl,
            roomCode = roomCode.trim().uppercase(Locale.ROOT),
            host = false,
            create = false,
            onState = onState,
            onSyncMessage = onSyncMessage,
            onChatMessage = onChatMessage,
            onErrorMessage = onErrorMessage
        ) { ws ->
            sendJoin(ws, currentRoomCode, create = false, mediaState = null)
        }
    }

    fun leaveRoom() {
        userLeaving = true
        cancelReconnect()
        runCatching {
            webSocket?.send(JSONObject().put("type", "leave").toString())
        }
        webSocket?.close(1000, "leave")
        clearConnection(clearRoom = true)
    }

    fun disconnect() {
        userLeaving = true
        cancelReconnect()
        webSocket?.close(1000, "disconnect")
        clearConnection(clearRoom = true)
    }

    fun syncPlayback(state: MediaSyncState) {
        if (!isHost) return
        lastMediaState = state
        webSocket?.send(buildSyncJson(state).toString())
    }

    fun requestRoomState() {
        webSocket?.send(JSONObject().put("type", "request_state").toString())
    }

    fun sendChat(message: String) {
        val clean = message.trim()
        if (clean.isBlank()) return
        val msg = JSONObject().apply {
            put("type", "chat")
            put("message", clean.take(300))
        }
        webSocket?.send(msg.toString())
    }

    fun getDefaultServerUrl(): String {
        return PrefsManager.getString(HawkConfig.TOGETHER_SERVER_URL, DEFAULT_SERVER_URL)
            .ifBlank { DEFAULT_SERVER_URL }
    }

    fun saveServerUrl(serverUrl: String) {
        PrefsManager.putString(HawkConfig.TOGETHER_SERVER_URL, normalizeServerUrl(serverUrl))
    }

    fun currentUserId(): String = userId

    private fun connect(
        serverUrl: String,
        roomCode: String,
        host: Boolean,
        create: Boolean,
        onState: (RoomState) -> Unit,
        onSyncMessage: (MediaSyncState) -> Unit,
        onChatMessage: (ChatMessage) -> Unit,
        onErrorMessage: (String) -> Unit,
        afterOpen: (WebSocket) -> Unit
    ) {
        closeSocketOnly()
        userLeaving = false
        currentServerUrl = normalizeServerUrl(serverUrl)
        currentRoomCode = roomCode
        isHost = host
        onRoomState = onState
        onSync = onSyncMessage
        onChat = onChatMessage
        onError = onErrorMessage
        onConnected = afterOpen
        saveServerUrl(currentServerUrl)

        val request = Request.Builder().url(currentServerUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                post { onConnected?.invoke(webSocket) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                this@WatchTogetherManager.webSocket = null
                if (!userLeaving && currentRoomCode.isNotBlank()) {
                    post {
                        notifyConnectionState(reconnecting = true)
                        onError?.invoke("连接已断开，正在重连")
                    }
                    scheduleReconnect(create)
                } else {
                    post { onError?.invoke("连接失败：${friendlyMessage(t.message)}") }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@WatchTogetherManager.webSocket = null
                if (!userLeaving && currentRoomCode.isNotBlank()) {
                    post { notifyConnectionState(reconnecting = true) }
                    scheduleReconnect(create = false)
                }
            }
        })
    }

    private fun scheduleReconnect(create: Boolean) {
        cancelReconnect()
        val delays = longArrayOf(1_000L, 3_000L, 6_000L, 10_000L)
        val delayMs = delays.getOrElse(reconnectAttempts.coerceAtLeast(0)) { 10_000L }
        reconnectAttempts += 1
        reconnectRunnable = Runnable {
            if (userLeaving || currentServerUrl.isBlank() || currentRoomCode.isBlank()) return@Runnable
            val request = Request.Builder().url(currentServerUrl).build()
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectAttempts = 0
                    sendJoin(webSocket, currentRoomCode, create = create && isHost, mediaState = if (isHost) lastMediaState else null)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    this@WatchTogetherManager.webSocket = null
                    post { notifyConnectionState(reconnecting = true) }
                    scheduleReconnect(create = false)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    this@WatchTogetherManager.webSocket = null
                    if (!userLeaving) {
                        post { notifyConnectionState(reconnecting = true) }
                        scheduleReconnect(create = false)
                    }
                }
            })
        }
        mainHandler.postDelayed(reconnectRunnable!!, delayMs)
    }

    private fun sendJoin(ws: WebSocket, roomCode: String, create: Boolean, mediaState: MediaSyncState?) {
        val msg = JSONObject().apply {
            put("type", if (reconnectAttempts > 0) "rejoin" else "join")
            put("roomCode", roomCode)
            put("userId", userId)
            put("userName", userName)
            put("create", create)
            put("isHost", isHost)
            mediaState?.let { put("mediaState", mediaStateToJson(it)) }
        }
        ws.send(msg.toString())
    }

    private fun buildSyncJson(state: MediaSyncState): JSONObject {
        return mediaStateToJson(state).apply {
            put("type", "sync")
            put("timestamp", System.currentTimeMillis())
        }
    }

    private fun mediaStateToJson(state: MediaSyncState): JSONObject {
        return JSONObject().apply {
            put("url", state.url)
            put("videoTitle", state.videoTitle)
            put("sourceKey", state.sourceKey)
            put("vodId", state.vodId)
            put("playFlag", state.playFlag)
            put("playIndex", state.playIndex)
            put("episodeName", state.episodeName)
            put("position", state.position.coerceAtLeast(0L))
            put("speed", state.speed.toDouble())
            if (state.temporarySpeed != null) {
                put("temporarySpeed", state.temporarySpeed.toDouble())
            } else {
                put("temporarySpeed", JSONObject.NULL)
            }
            put("isPlaying", state.isPlaying)
            put("action", state.action)
        }
    }

    private fun handleMessage(text: String) {
        runCatching {
            val msg = JSONObject(text)
            when (msg.optString("type")) {
                "room_state" -> handleRoomStateMessage(msg)
                "members_update" -> handleMembersMessage(msg)
                "sync", "media_change" -> {
                    parseMediaState(msg.optJSONObject("mediaState") ?: msg)?.let { state ->
                        lastMediaState = state
                        post { onSync?.invoke(state) }
                    }
                }
                "chat" -> {
                    val senderId = msg.optString("userId")
                    post {
                        onChat?.invoke(
                            ChatMessage(
                                userId = senderId,
                                userName = msg.optString("userName", "匿名"),
                                message = msg.optString("message"),
                                timestamp = msg.optLong("timestamp", System.currentTimeMillis()),
                                isSystem = senderId == "system" || msg.optString("type") == "system",
                                isSelf = senderId == userId
                            )
                        )
                    }
                }
                "room_closed" -> {
                    val message = msg.optString("message", "房间已结束")
                    post {
                        onError?.invoke(message)
                        onChat?.invoke(ChatMessage(userId = "system", userName = "系统", message = message, isSystem = true))
                    }
                    clearConnection(clearRoom = true)
                }
                "error" -> post { onError?.invoke(msg.optString("error", "一起看服务异常")) }
                "pong" -> Unit
            }
        }.onFailure {
            post { onError?.invoke("消息解析失败") }
        }
    }

    private fun handleRoomStateMessage(msg: JSONObject) {
        val roomCode = msg.optString("roomCode", currentRoomCode)
        if (roomCode.isNotBlank()) currentRoomCode = roomCode
        val members = parseMembers(msg.optJSONArray("members"))
        members.firstOrNull { it.userId == userId }?.let { isHost = it.isHost }
        if (msg.has("isHost") && msg.optBoolean("isHost")) isHost = true
        val state = RoomState(
            roomCode = currentRoomCode,
            members = members,
            mediaState = parseMediaState(msg.optJSONObject("mediaState")),
            hasFreshMediaState = msg.has("mediaState") && !msg.isNull("mediaState"),
            isHost = isHost,
            userId = userId,
            serverUrl = currentServerUrl,
            connected = true,
            reconnecting = false
        )
        lastMediaState = state.mediaState ?: lastMediaState
        post { onRoomState?.invoke(state) }
    }

    private fun handleMembersMessage(msg: JSONObject) {
        val members = parseMembers(msg.optJSONArray("members"))
        members.firstOrNull { it.userId == userId }?.let { isHost = it.isHost }
        post {
            onRoomState?.invoke(
                RoomState(
                    roomCode = currentRoomCode,
                    members = members,
                    mediaState = lastMediaState,
                    hasFreshMediaState = false,
                    isHost = isHost,
                    userId = userId,
                    serverUrl = currentServerUrl,
                    connected = webSocket != null,
                    reconnecting = false
                )
            )
        }
    }

    private fun parseMembers(array: JSONArray?): List<RoomMember> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                RoomMember(
                    userId = item.optString("userId"),
                    userName = item.optString("userName", "匿名"),
                    isHost = item.optBoolean("isHost"),
                    joinedAt = item.optLong("joinedAt"),
                    connected = item.optBoolean("connected", true)
                )
            }
        }
    }

    private fun parseMediaState(obj: JSONObject?): MediaSyncState? {
        if (obj == null) return null
        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
        val lastUpdateTime = obj.optLong("lastUpdateTime", timestamp)
        val temporarySpeed = if (obj.has("temporarySpeed") && !obj.isNull("temporarySpeed")) {
            obj.optDouble("temporarySpeed", 0.0).toFloat().takeIf { it > 0f }
        } else {
            null
        }
        return MediaSyncState(
            url = obj.optString("url"),
            videoTitle = obj.optString("videoTitle"),
            sourceKey = obj.optString("sourceKey"),
            vodId = obj.optString("vodId"),
            playFlag = obj.optString("playFlag"),
            playIndex = obj.optInt("playIndex", 0),
            episodeName = obj.optString("episodeName"),
            isPlaying = obj.optBoolean("isPlaying"),
            position = obj.optLong("position"),
            speed = obj.optDouble("speed", 1.0).toFloat(),
            temporarySpeed = temporarySpeed,
            action = obj.optString("action", "sync"),
            timestamp = timestamp,
            lastUpdateTime = lastUpdateTime
        )
    }

    private fun notifyConnectionState(reconnecting: Boolean) {
        onRoomState?.invoke(
            RoomState(
                roomCode = currentRoomCode,
                mediaState = lastMediaState,
                isHost = isHost,
                userId = userId,
                serverUrl = currentServerUrl,
                connected = !reconnecting,
                reconnecting = reconnecting
            )
        )
    }

    private fun normalizeServerUrl(raw: String): String {
        val trimmed = raw.trim().ifBlank { DEFAULT_SERVER_URL }
        val withScheme = when {
            trimmed.startsWith("ws://", ignoreCase = true) -> trimmed
            trimmed.startsWith("wss://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> "ws://" + trimmed.removePrefix("http://")
            trimmed.startsWith("https://", ignoreCase = true) -> "wss://" + trimmed.removePrefix("https://")
            else -> "ws://$trimmed"
        }
        return withScheme.trimEnd('/')
    }

    private fun clearConnection(clearRoom: Boolean) {
        webSocket = null
        isHost = false
        onConnected = null
        if (clearRoom) {
            currentRoomCode = ""
            lastMediaState = null
        }
    }

    private fun closeSocketOnly() {
        cancelReconnect()
        webSocket?.close(1000, "reconnect")
        webSocket = null
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun post(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun buildUserName(): String {
        return "影友${userId.take(4).uppercase(Locale.ROOT)}"
    }

    private fun friendlyMessage(raw: String?): String {
        if (raw.isNullOrBlank()) return "请检查服务地址"
        if (raw.contains("Software caused connection abort", ignoreCase = true)) {
            return "连接被系统中断"
        }
        return raw
    }

    companion object {
        const val DEFAULT_SERVER_URL = "ws://10.0.2.2:3000"
    }
}
