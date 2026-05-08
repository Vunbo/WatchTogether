package com.vunbo.watchtogether.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class CacheManager(private val context: Context) {
    private val gson = Gson()
    private val cacheFile: File get() = File(context.filesDir, "app_cache.json")
    private val cacheMap: MutableMap<String, String> by lazy { loadCache() }

    fun save(key: String, obj: Any) {
        cacheMap[key] = gson.toJson(obj)
        persistCache()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> load(key: String): T? {
        val json = cacheMap[key] ?: return null
        return try { gson.fromJson(json, Any::class.java) as? T } catch (e: Exception) { null }
    }

    fun delete(key: String) {
        cacheMap.remove(key)
        persistCache()
    }

    fun clear() {
        cacheMap.clear()
        cacheFile.delete()
    }

    private fun loadCache(): MutableMap<String, String> {
        if (!cacheFile.exists()) return mutableMapOf()
        val json = cacheFile.readText()
        return try {
            gson.fromJson(json, object : TypeToken<MutableMap<String, String>>() {}.type) ?: mutableMapOf()
        } catch (e: Exception) { mutableMapOf() }
    }

    private fun persistCache() {
        cacheFile.writeText(gson.toJson(cacheMap))
    }
}
