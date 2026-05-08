package com.vunbo.watchtogether.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vunbo.watchtogether.data.model.VodInfo
import java.io.File

data class VodRecord(
    val id: Long = 0,
    val vodId: String = "",
    val updateTime: Long = 0L,
    val sourceKey: String = "",
    val dataJson: String = ""
)

data class VodCollect(
    val id: Long = 0,
    val vodId: String = "",
    val updateTime: Long = 0L,
    val sourceKey: String = "",
    val name: String = "",
    val pic: String = ""
)

class RoomDataManager(private val context: Context) {
    private val gson = Gson()
    private val recordsFile: File get() = File(context.filesDir, "vod_records.json")
    private val collectsFile: File get() = File(context.filesDir, "vod_collects.json")

    fun insertVodRecord(sourceKey: String, vodInfo: VodInfo) {
        val records = loadRecords().toMutableList()
        val existingIdx = records.indexOfFirst {
            it.sourceKey == sourceKey && it.vodId == (vodInfo.id ?: "")
        }
        val json = gson.toJson(vodInfo)
        val record = VodRecord(
            id = if (existingIdx >= 0) records[existingIdx].id else (records.size + 1).toLong(),
            vodId = vodInfo.id ?: "",
            updateTime = System.currentTimeMillis(),
            sourceKey = sourceKey,
            dataJson = json
        )
        if (existingIdx >= 0) records[existingIdx] = record else records.add(0, record)
        saveRecords(records.take(100))
    }

    fun getVodInfo(sourceKey: String, vodId: String): VodInfo? {
        val record = loadRecords().find { it.sourceKey == sourceKey && it.vodId == vodId } ?: return null
        return try { gson.fromJson(record.dataJson, VodInfo::class.java) } catch (e: Exception) { null }
    }

    fun deleteVodRecord(sourceKey: String, vodInfo: VodInfo) {
        val records = loadRecords().toMutableList()
        records.removeAll { it.sourceKey == sourceKey && it.vodId == (vodInfo.id ?: "") }
        saveRecords(records)
    }

    fun getAllVodRecord(limit: Int = 100): List<VodInfo> {
        return loadRecords().take(limit).mapNotNull { record ->
            try { gson.fromJson(record.dataJson, VodInfo::class.java) } catch (e: Exception) { null }
        }
    }

    fun deleteAllVodRecords() { recordsFile.delete() }

    fun insertVodCollect(sourceKey: String, vodInfo: VodInfo) {
        val collects = loadCollects().toMutableList()
        if (collects.any { it.sourceKey == sourceKey && it.vodId == (vodInfo.id ?: "") }) return
        collects.add(0, VodCollect(
            id = System.currentTimeMillis(),
            vodId = vodInfo.id ?: "",
            updateTime = System.currentTimeMillis(),
            sourceKey = sourceKey,
            name = vodInfo.name ?: "",
            pic = vodInfo.pic ?: ""
        ))
        saveCollects(collects)
    }

    fun deleteVodCollect(id: Int) {
        val collects = loadCollects().toMutableList()
        collects.removeAll { it.id == id.toLong() }
        saveCollects(collects)
    }

    fun deleteVodCollect(sourceKey: String, vodInfo: VodInfo) {
        val collects = loadCollects().toMutableList()
        collects.removeAll { it.sourceKey == sourceKey && it.vodId == (vodInfo.id ?: "") }
        saveCollects(collects)
    }

    fun deleteAllVodCollects() { collectsFile.delete() }

    fun isVodCollect(sourceKey: String, vodId: String): Boolean {
        return loadCollects().any { it.sourceKey == sourceKey && it.vodId == vodId }
    }

    fun getAllVodCollect(): List<VodCollect> = loadCollects()

    private fun loadRecords(): List<VodRecord> {
        if (!recordsFile.exists()) return emptyList()
        return try { gson.fromJson(recordsFile.readText(), object : TypeToken<List<VodRecord>>() {}.type) ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    private fun saveRecords(records: List<VodRecord>) { recordsFile.writeText(gson.toJson(records)) }

    private fun loadCollects(): List<VodCollect> {
        if (!collectsFile.exists()) return emptyList()
        return try { gson.fromJson(collectsFile.readText(), object : TypeToken<List<VodCollect>>() {}.type) ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    private fun saveCollects(collects: List<VodCollect>) { collectsFile.writeText(gson.toJson(collects)) }
}
