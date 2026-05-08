package com.vunbo.watchtogether.data.util

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.IDN
import java.net.ProtocolException
import java.net.URI
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object OkHttpHelper {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 15L
    private const val DEFAULT_READ_TIMEOUT_SECONDS = 25L
    private const val DEFAULT_WRITE_TIMEOUT_SECONDS = 25L
    private const val CONFIG_READ_TIMEOUT_SECONDS = 35L
    private const val JAR_READ_TIMEOUT_SECONDS = 60L
    private const val REQUEST_RETRY_COUNT = 2

    // 与 TVBoxOS 一致的请求头
    private const val USER_AGENT = "okhttp/3.15"
    private const val REQUEST_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"

    private fun baseBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
    }

    val client: OkHttpClient by lazy {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
                override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            val sslFactory: SSLSocketFactory = sslContext.socketFactory

            baseBuilder()
                .sslSocketFactory(sslFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            baseBuilder().build()
        }
    }

    private val parserClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    private val configClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(CONFIG_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val jarClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(JAR_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(JAR_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** 编码 URL 中的非 ASCII 域名（中文域名转 Punycode） */
    fun encodeUrl(rawUrl: String): String {
        return try {
            val uri = URI(rawUrl)
            val host = uri.host ?: return rawUrl
            val asciiHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED)
            if (asciiHost != host) {
                val scheme = uri.scheme ?: "http"
                val port = if (uri.port > 0) ":${uri.port}" else ""
                val path = uri.rawPath?.ifEmpty { "/" } ?: "/"
                val query = uri.rawQuery?.let { "?$it" } ?: ""
                "$scheme://$asciiHost$port$path$query"
            } else {
                rawUrl
            }
        } catch (e: Exception) {
            rawUrl
        }
    }

    /** 为所有请求添加浏览器伪装头 */
    private fun buildRequest(url: String, method: String, body: RequestBody? = null): Request {
        val encoded = encodeUrl(url)
        val builder = Request.Builder()
            .url(encoded)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", REQUEST_ACCEPT)
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
        return when (method) {
            "POST" -> builder.post(body!!).build()
            "POST_FORM" -> builder.post(body!!).build()
            else -> builder.get().build()
        }
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): Response {
        val request = buildRequest(url, "GET").newBuilder().apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()
        return client.newCall(request).execute()
    }

    fun getWithClient(
        url: String,
        headers: Map<String, String> = emptyMap(),
        useParserClient: Boolean = false,
        useConfigClient: Boolean = false,
        useJarClient: Boolean = false
    ): Response {
        val request = buildRequest(url, "GET").newBuilder().apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        val selectedClient = when {
            useJarClient -> jarClient
            useConfigClient -> configClient
            useParserClient -> parserClient
            else -> client
        }
        return selectedClient.newCall(request).execute()
    }

    fun getAsync(url: String, headers: Map<String, String> = emptyMap(), callback: (String?) -> Unit) {
        val request = buildRequest(url, "GET").newBuilder().apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(null) }
            override fun onResponse(call: Call, response: Response) {
                callback(response.body?.string())
            }
        })
    }

    fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): Response {
        val reqBody = body.toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest(url, "POST", reqBody).newBuilder().apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()
        return client.newCall(request).execute()
    }

    fun postForm(url: String, formData: Map<String, String>, headers: Map<String, String> = emptyMap()): Response {
        val formBuilder = FormBody.Builder()
        formData.forEach { (k, v) -> formBuilder.add(k, v) }
        val request = buildRequest(url, "POST_FORM", formBuilder.build()).newBuilder().apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()
        return client.newCall(request).execute()
    }

    fun getBody(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            get(url, headers).use { response ->
                if (response.isSuccessful) response.body?.string() else {
                    android.util.Log.e("OkHttp", "HTTP ${response.code}: $url")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OkHttp", "Request failed: $url - ${e.message}")
            null
        }
    }

    fun getConfigBody(url: String, headers: Map<String, String> = emptyMap()): String? {
        return getBodyWithRetry(url, headers, useConfigClient = true, useJarClient = false)
    }

    private fun getBodyWithRetry(
        url: String,
        headers: Map<String, String>,
        useConfigClient: Boolean,
        useJarClient: Boolean
    ): String? {
        repeat(REQUEST_RETRY_COUNT + 1) { attempt ->
            try {
                getWithClient(
                    url = url,
                    headers = headers,
                    useConfigClient = useConfigClient,
                    useJarClient = useJarClient
                ).use { response ->
                    if (response.isSuccessful) return response.body?.string()
                    android.util.Log.e("OkHttp", "HTTP ${response.code}: $url")
                    if (response.code in 400..499) return null
                }
            } catch (e: Exception) {
                android.util.Log.e("OkHttp", "Request failed(${attempt + 1}): $url - ${e.message}")
            }
            if (attempt < REQUEST_RETRY_COUNT) {
                try {
                    Thread.sleep(300L * (attempt + 1))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
        }
        return null
    }

    fun getBodyForParser(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val response = getWithClient(url, headers + parserHeaders(), useParserClient = true)
            if (response.isSuccessful) response.body?.string() else {
                android.util.Log.w("OkHttp", "Parser HTTP ${response.code}: $url")
                null
            }
        } catch (e: ProtocolException) {
            android.util.Log.w("OkHttp", "Parser bad stream: $url - ${e.message}")
            null
        } catch (e: Exception) {
            android.util.Log.w("OkHttp", "Parser request failed: $url - ${e.message}")
            null
        }
    }

    private fun parserHeaders(): Map<String, String> = mapOf(
        "Connection" to "close",
        "Accept-Encoding" to "identity",
        "Referer" to "https://www.iqiyi.com/"
    )

    fun getBodyBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray? {
        repeat(REQUEST_RETRY_COUNT + 1) { attempt ->
            try {
                getWithClient(url, headers, useJarClient = true).use { response ->
                    if (response.isSuccessful) return response.body?.bytes()
                    android.util.Log.e("OkHttp", "getBodyBytes 失败: HTTP ${response.code} $url")
                    if (response.code in 400..499) return null
                }
            } catch (e: Exception) {
                android.util.Log.e("OkHttp", "getBodyBytes 异常(${attempt + 1}): $url - ${e.message}", e)
            }
            if (attempt < REQUEST_RETRY_COUNT) {
                try {
                    Thread.sleep(500L * (attempt + 1))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
        }
        return null
    }
}
