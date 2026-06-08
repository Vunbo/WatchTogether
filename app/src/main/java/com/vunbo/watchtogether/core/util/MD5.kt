package com.vunbo.watchtogether.core.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object MD5 {
    fun encode(s: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getFileMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var len: Int
            while (fis.read(buffer).also { len = it } != -1) {
                digest.update(buffer, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
