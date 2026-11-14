package com.quranrecite.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Copies the read-only SQLite assets to app storage (once per app install/update) and opens them read-only.
 *  - qpc-hafs.db      canonical vocalized verse text (never modified)
 *  - quran-index.db   derived: verse->word-id ranges, typed page lines, surahs, adjustments (built by tools/build_quran_index.py)
 * qpc-v1-15-lines.db is the source for quran-index.db and is not opened at runtime.
 */
internal class QuranDatabases(context: Context) {
    val hafs: SQLiteDatabase
    val index: SQLiteDatabase

    init {
        val app = context.applicationContext
        val stamp = try {
            app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
        } catch (e: Exception) { 0L }
        hafs = open(app, HAFS_NAME, stamp)
        index = open(app, INDEX_NAME, stamp)
    }

    private fun open(context: Context, name: String, stamp: Long): SQLiteDatabase {
        val dir = File(context.filesDir, "quran").apply { mkdirs() }
        val out = File(dir, name)
        val stampFile = File(dir, "$name.stamp")
        val assetLen = context.assets.openFd(name).use { it.length }
        val expected = "$assetLen:$stamp"
        val current = if (stampFile.exists()) stampFile.readText() else ""
        if (!out.exists() || out.length() != assetLen || current != expected) {
            val tmp = File(dir, "$name.tmp")
            context.assets.open(name).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
            stampFile.writeText(expected)
        }
        return SQLiteDatabase.openDatabase(out.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    companion object {
        const val HAFS_NAME = "qpc-hafs.db"
        const val INDEX_NAME = "quran-index.db"
    }
}
