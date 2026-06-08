package com.vunbo.watchtogether.data.api

import android.util.Log
import com.vunbo.watchtogether.WatchTogetherApp
import com.vunbo.watchtogether.data.util.MD5
import com.vunbo.watchtogether.data.util.OkHttpHelper
import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderNull
import dalvik.system.DexClassLoader
import dalvik.system.InMemoryDexClassLoader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

/**
 * JAR 爬虫加载器 — 与 TVBoxOS JarLoader 功能一致
 * 负责下载远程 JAR、DexClassLoader 加载、Spider 实例化
 */
class JarLoader {
    companion object {
        private const val TAG = "JarLoader"
        private val DEX_MAGIC = byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte())
        private val ZIP_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03, 0x04)
    }

    private val classLoaders = ConcurrentHashMap<String, ClassLoader>()
    private val spiders = ConcurrentHashMap<String, Spider>()
    private var recentJarKey = "main"

    private data class DexEntry(val order: Int, val bytes: ByteArray)

    fun clear() {
        spiders.clear()
        classLoaders.clear()
    }

    private fun startsWith(bytes: ByteArray, magic: ByteArray): Boolean {
        if (bytes.size < magic.size) return false
        return magic.indices.all { bytes[it] == magic[it] }
    }

    private fun dexOrder(name: String): Int {
        val fileName = name.substringAfterLast('/')
        if (!fileName.startsWith("classes") || !fileName.endsWith(".dex")) return Int.MAX_VALUE
        val suffix = fileName.removePrefix("classes").removeSuffix(".dex")
        return if (suffix.isEmpty()) 1 else suffix.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun createDexBuffers(file: File): Array<ByteBuffer>? {
        val bytes = file.readBytes()
        if (startsWith(bytes, DEX_MAGIC)) return arrayOf(ByteBuffer.wrap(bytes))

        if (!startsWith(bytes, ZIP_MAGIC)) {
            Log.e(TAG, "JAR 格式无效: ${file.name}, size=${bytes.size}")
            return null
        }

        val dexEntries = mutableListOf<DexEntry>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                try {
                    val order = dexOrder(entry.name)
                    if (!entry.isDirectory && order != Int.MAX_VALUE) {
                        val out = ByteArrayOutputStream()
                        zip.copyTo(out)
                        val dexBytes = out.toByteArray()
                        if (startsWith(dexBytes, DEX_MAGIC)) {
                            dexEntries += DexEntry(order, dexBytes)
                        } else {
                            Log.w(TAG, "忽略无效 dex 条目: ${entry.name}")
                        }
                    }
                } finally {
                    zip.closeEntry()
                }
            }
        }

        if (dexEntries.isEmpty()) {
            Log.e(TAG, "JAR 中没有找到 classes.dex: ${file.name}")
            return null
        }

        return dexEntries
            .sortedBy { it.order }
            .map { ByteBuffer.wrap(it.bytes) }
            .toTypedArray()
    }

    private fun createDexClassLoader(file: File): ClassLoader {
        val optimizedDir = File(WatchTogetherApp.instance.cacheDir, "catvod_csp")
        if (!optimizedDir.exists()) optimizedDir.mkdirs()
        return DexClassLoader(file.absolutePath, optimizedDir.absolutePath, null, WatchTogetherApp.instance.classLoader)
    }

    private inline fun <T> withClassLoader(classLoader: ClassLoader, block: () -> T): T {
        val thread = Thread.currentThread()
        val old = thread.contextClassLoader
        thread.contextClassLoader = classLoader
        return try {
            block()
        } finally {
            thread.contextClassLoader = old
        }
    }

    private fun ensureInitDexLoader(initClass: Class<*>, classLoader: ClassLoader): Boolean {
        if (classLoader !is DexClassLoader) return true
        return try {
            val holder = initClass.getMethod("get").invoke(null)
            val dexLoaderField = initClass.declaredFields.firstOrNull {
                DexClassLoader::class.java.isAssignableFrom(it.type)
            } ?: return true
            dexLoaderField.isAccessible = true
            if (dexLoaderField.get(holder) == null) {
                dexLoaderField.set(holder, classLoader)
                Log.w(TAG, "Spider Init loader 为空，已使用当前 DexClassLoader 兜底")
            }
            dexLoaderField.get(holder) != null
        } catch (e: Throwable) {
            Log.e(TAG, "检查 Spider Init loader 失败", e)
            false
        }
    }

    private fun invokeInit(initClass: Class<*>, initMethod: Method, classLoader: ClassLoader): Throwable? {
        return try {
            withClassLoader(classLoader) {
                initMethod.invoke(null, WatchTogetherApp.instance)
            }
            if (ensureInitDexLoader(initClass, classLoader)) null else IllegalStateException("Spider Init loader is null")
        } catch (e: Throwable) {
            if (ensureInitDexLoader(initClass, classLoader)) {
                Log.w(TAG, "Spider Init 抛出异常，但 loader 已完成兜底: ${e.message}")
                null
            } else {
                e
            }
        }
    }

    private fun createClassLoader(file: File): ClassLoader {
        return try {
            val header = file.inputStream().use { input ->
                ByteArray(4).also { input.read(it) }
            }
            if (startsWith(header, ZIP_MAGIC)) {
                // TVBox-style spider jars often keep guard/native resources in ZIP assets.
                // DexClassLoader preserves those resources; InMemoryDexClassLoader only sees dex bytes.
                return createDexClassLoader(file)
            }
            val buffers = createDexBuffers(file) ?: throw IllegalArgumentException("No valid dex in ${file.name}")
            InMemoryDexClassLoader(buffers, WatchTogetherApp.instance.classLoader)
        } catch (e: Throwable) {
            Log.w(TAG, "内存加载 JAR 失败，尝试 DexClassLoader: ${file.name}", e)
            createDexClassLoader(file)
        }
    }

    /** 用 InMemoryDexClassLoader 从内存加载 JAR/DEX（避免 Android 10+ 可写文件限制） */
    @Synchronized
    private fun loadClassLoader(jarPath: String, key: String): Boolean {
        if (classLoaders.containsKey(key)) return true
        return try {
            val file = File(jarPath)
            file.setReadOnly()
            file.setWritable(false, false)
            val classLoader = createClassLoader(file)
            var success = false
            var retry = 0
            do {
                try {
                    val initClass = classLoader.loadClass("com.github.catvod.spider.Init")
                    val initMethod = initClass.getMethod("init", android.content.Context::class.java)
                    val initError = AtomicReference<Throwable?>(null)
                    val thread = Thread {
                        val error = invokeInit(initClass, initMethod, classLoader)
                        if (error != null) {
                            val e = error
                            initError.set(e)
                            Log.e(TAG, "Spider Init 执行失败: $key", e)
                        }
                    }
                    thread.contextClassLoader = classLoader
                    thread.start()
                    thread.join(5000)
                    val error = initError.get()
                    if (thread.isAlive) {
                        Log.e(TAG, "Spider Init 超时: $key")
                    } else if (error == null) {
                        Log.i(TAG, "Spider JAR 加载成功: $key")
                        success = true
                    }
                    break
                } catch (e: Throwable) {
                    Thread.sleep(200)
                }
                retry++
            } while (retry < 2)

            if (success) {
                classLoaders[key] = classLoader
                Log.i(TAG, "JAR ClassLoader 已缓存: $key")
            }
            success
        } catch (e: Throwable) {
            Log.e(TAG, "JAR 加载失败: $key", e)
            false
        }
    }

    /** 检查 JAR 是否需要更新（超过 7 天） */
    private fun isWeekAgo(file: File): Boolean {
        return System.currentTimeMillis() - file.lastModified() > 7 * 24 * 3600 * 1000L
    }

    private fun prepareWritableFile(file: File) {
        if (!file.exists()) return
        try {
            file.setWritable(true, true)
            if (!file.delete()) {
                Log.w(TAG, "删除旧 JAR 缓存失败，尝试覆盖: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "准备覆盖旧 JAR 缓存失败: ${file.absolutePath}", e)
        }
    }

    /** 从远程下载 JAR 并加载 */
    @Synchronized
    private fun loadJarInternal(jarUrl: String, md5: String?, key: String): ClassLoader? {
        if (classLoaders.containsKey(key)) return classLoaders[key]

        // JAR 存储位置随意（InMemoryDexClassLoader 从内存加载，不受路径限制）
        val jarDir = File(WatchTogetherApp.instance.filesDir, "csp")
        if (!jarDir.exists()) jarDir.mkdirs()
        val cacheFile = File(jarDir, "$key.jar")

        try {
            // 检查缓存
            if (cacheFile.exists()) {
                val useCache = if (!md5.isNullOrEmpty()) {
                    MD5.getFileMd5(cacheFile).equals(md5, ignoreCase = true)
                } else {
                    !isWeekAgo(cacheFile)
                }
                if (useCache && loadClassLoader(cacheFile.absolutePath, key)) {
                    return classLoaders[key]
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查缓存失败", e)
        }

        // 从网络下载
        return try {
            Log.i(TAG, "正在下载 JAR: $jarUrl")
            val bytes = OkHttpHelper.getBodyBytes(jarUrl)
            if (bytes == null) {
                Log.e(TAG, "JAR 下载返回 null: $jarUrl")
                return null
            }
            prepareWritableFile(cacheFile)
            FileOutputStream(cacheFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            Log.i(TAG, "JAR 下载完成: $jarUrl (${bytes.size} bytes)")
            // Android 10+ 禁止加载可写 dex 文件，设为只读
            cacheFile.setReadOnly()
            cacheFile.setWritable(false, false)
            if (loadClassLoader(cacheFile.absolutePath, key)) classLoaders[key] else null
        } catch (e: Exception) {
            Log.e(TAG, "JAR 下载失败: $jarUrl", e)
            null
        }
    }

    /** 获取 Spider 实例 */
    fun getSpider(key: String, cls: String, ext: String?, jar: String?): Spider {
        if (spiders.containsKey(key)) return spiders[key]!!

        val clsKey = cls.replace("csp_", "")
        val jarKey: String
        val jarUrl: String
        val jarMd5: String?

        if (jar.isNullOrEmpty()) {
            jarKey = "main"
            jarUrl = ""
            jarMd5 = null
        } else {
            val parts = jar.split(";md5;")
            jarUrl = parts[0]
            jarKey = MD5.encode(jarUrl)
            jarMd5 = parts.getOrNull(1)?.trim()
        }

        recentJarKey = jarKey

        val classLoader = if (jarKey == "main") {
            classLoaders["main"]
        } else {
            loadJarInternal(jarUrl, jarMd5, jarKey)
        }

        if (classLoader == null) {
            Log.w(TAG, "JAR 未加载，使用 SpiderNull: cls=$cls, key=$key")
            return SpiderNull()
        }

        return try {
            val className = "com.github.catvod.spider.$clsKey"
            Log.i(TAG, "加载 Spider 类: $className")
            val sp = withClassLoader(classLoader) {
                val clazz = classLoader.loadClass(className)
                clazz.getDeclaredConstructor().newInstance() as Spider
            }
            withClassLoader(classLoader) {
                sp.init(WatchTogetherApp.instance, ext)
            }
            spiders[key] = sp
            sp
        } catch (e: Throwable) {
            Log.e(TAG, "Spider 实例化失败: $key", e)
            SpiderNull()
        }
    }

    /** 加载主 JAR（config 中的 spider 字段），返回 classLoader */
    fun loadMainJar(spiderUrl: String): Boolean {
        val parts = spiderUrl.split(";md5;")
        val url = parts[0]
        val md5 = parts.getOrNull(1)?.trim()
        recentJarKey = "main"

        // 如果是相对路径，基于当前配置 URL 构建完整路径
        val fullUrl = if (url.startsWith("./") || url.startsWith("/")) {
            val configFile = File(WatchTogetherApp.instance.filesDir, "config_url")
            val baseUrl = if (configFile.exists()) {
                configFile.readText().trim()
            } else ""
            Log.i(TAG, "config_url 文件存在: ${configFile.exists()}, 内容: $baseUrl")
            if (baseUrl.isNotEmpty()) {
                val base = baseUrl.substringBeforeLast("/")
                "$base/${url.removePrefix("./")}"
            } else {
                Log.e(TAG, "config_url 为空，无法解析相对路径: $url")
                url
            }
        } else {
            url
        }

        Log.i(TAG, "加载主 JAR, 解析后 URL: $fullUrl")
        val jarKey = MD5.encode(url)
        Log.i(TAG, "JAR key: $jarKey, 开始下载...")
        val loader = loadJarInternal(fullUrl, md5, jarKey)
        Log.i(TAG, "loadJarInternal 结果: ${loader != null}")
        if (loader != null) {
            classLoaders["main"] = loader
            Log.i(TAG, "主 JAR 加载成功，classLoaders[main] 已设置")
            return true
        }
        Log.e(TAG, "主 JAR 加载失败")
        return false
    }

    /** 获取当前 Spider 数量（调试用） */
    fun getSpiderCount(): Int = spiders.size
}
