package com.vunbo.watchtogether.core.util

import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AES {
    fun decode(key: String, iv: String, data: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(iv.toByteArray())
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            String(cipher.doFinal(hexToBytes(data)))
        } catch (e: Exception) {
            ""
        }
    }

    fun decodeECB(key: String, data: String): String {
        return ECB(data, key) ?: ""
    }

    fun isJson(str: String): Boolean {
        val trimmed = str.trim()
        return try {
            when {
                trimmed.startsWith("{") -> {
                    JSONObject(trimmed)
                    true
                }
                trimmed.startsWith("[") -> {
                    JSONArray(trimmed)
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun rightPadding(key: String, replace: String, length: Int): String {
        val value = key.trim()
        return when {
            value.length > length -> value.substring(0, length)
            value.length == length -> value
            else -> {
                val builder = StringBuilder(value)
                while (builder.length < length) {
                    builder.append(replace)
                }
                builder.substring(0, length)
            }
        }
    }

    fun ECB(data: String, key: String): String? {
        return try {
            val paddedKey = rightPadding(key, "0", 16)
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(paddedKey.toByteArray(), "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            String(cipher.doFinal(toBytes(data)))
        } catch (e: Exception) {
            null
        }
    }

    fun CBC(data: String, key: String, iv: String): String? {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(iv.toByteArray())
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            String(cipher.doFinal(toBytes(data)))
        } catch (e: Exception) {
            null
        }
    }

    fun toBytes(src: String): ByteArray {
        val clean = src.trim()
        val len = clean.length / 2
        val data = ByteArray(len)
        for (i in 0 until len) {
            data[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return data
    }

    private fun hexToBytes(hex: String): ByteArray = toBytes(hex)
}
