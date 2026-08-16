package com.dnc1981.musickontrol.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class MusicDatabaseHelper(context: Context) :
    SQLiteOpenHelper(
        context,
        "musiclibrary.db",
        null,
        1
    ) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE folders(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                parent TEXT,
                path TEXT UNIQUE,
                name TEXT,
                scanOrder INTEGER
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX idx_folders_scanOrder ON folders(scanOrder);
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE songs(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                folderPath TEXT,
                fileName TEXT,
                title TEXT,
                track INTEGER,
                duration INTEGER,
                uri TEXT
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX idx_songs_folderPath ON songs(folderPath);
            """.trimIndent()
        )
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            db.enableWriteAheadLogging()
        }
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
    }

    fun getFolders(): List<String> {
        val folders = mutableListOf<String>()
        try {
            val cursor = readableDatabase.query(
                "folders",
                arrayOf("path"),
                null,
                null,
                null,
                null,
                "scanOrder ASC"
            )

            while (cursor.moveToNext()) {
                folders.add(cursor.getString(0))
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error en getFolders: ${e.message}")
        }
        return folders
    }

    fun getSongs(folderPath: String): List<String> {
        val songs = mutableListOf<String>()
        try {
            val cursor = readableDatabase.query(
                "songs",
                arrayOf("uri"),
                "folderPath=?",
                arrayOf(folderPath),
                null,
                null,
                "fileName COLLATE NOCASE ASC"
            )

            while (cursor.moveToNext()) {
                songs.add(cursor.getString(0))
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error en getSongs: ${e.message}")
        }
        return songs
    }

    fun getAllSongs(): List<String> {
        val songs = mutableListOf<String>()
        try {
            val cursor = readableDatabase.query(
                "songs",
                arrayOf("uri"),
                null,
                null,
                null,
                null,
                "folderPath ASC, fileName COLLATE NOCASE ASC"
            )

            while (cursor.moveToNext()) {
                songs.add(cursor.getString(0))
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error en getAllSongs: ${e.message}")
        }
        return songs
    }

    // ✅ OPTIMIZADO: SIN SUBCONSULTAS
    fun getNextDiskFolder(currentFolderPath: String): String? {
        var result: String? = null
        try {
            // 1️⃣ OBTENER scanOrder ACTUAL
            val currentCursor = readableDatabase.query(
                "folders",
                arrayOf("scanOrder"),
                "path=?",
                arrayOf(currentFolderPath),
                null,
                null,
                null
            )

            val currentScanOrder = if (currentCursor.moveToFirst()) {
                currentCursor.getInt(0)
            } else {
                currentCursor.close()
                return null
            }
            currentCursor.close()

            // 2️⃣ OBTENER SIGUIENTE CARPETA CON CANCIONES
            val nextCursor = readableDatabase.rawQuery(
                """
                SELECT f.path
                FROM folders f
                WHERE f.scanOrder > ?
                AND (SELECT COUNT(*) FROM songs s WHERE s.folderPath = f.path) > 0
                ORDER BY f.scanOrder ASC
                LIMIT 1
                """.trimIndent(),
                arrayOf(currentScanOrder.toString())
            )

            if (nextCursor.moveToFirst()) {
                result = nextCursor.getString(0)
            }
            nextCursor.close()

            Log.d("MusicDB", "🔍 getNextDiskFolder: $currentFolderPath → $result")
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error en getNextDiskFolder: ${e.message}")
        }
        return result
    }

    // ✅ OPTIMIZADO: SIN SUBCONSULTAS
    fun getPreviousDiskFolder(currentFolderPath: String): String? {
        var result: String? = null
        try {
            // 1️⃣ OBTENER scanOrder ACTUAL
            val currentCursor = readableDatabase.query(
                "folders",
                arrayOf("scanOrder"),
                "path=?",
                arrayOf(currentFolderPath),
                null,
                null,
                null
            )

            val currentScanOrder = if (currentCursor.moveToFirst()) {
                currentCursor.getInt(0)
            } else {
                currentCursor.close()
                return null
            }
            currentCursor.close()

            // 2️⃣ OBTENER CARPETA ANTERIOR CON CANCIONES
            val prevCursor = readableDatabase.rawQuery(
                """
                SELECT f.path
                FROM folders f
                WHERE f.scanOrder < ?
                AND (SELECT COUNT(*) FROM songs s WHERE s.folderPath = f.path) > 0
                ORDER BY f.scanOrder DESC
                LIMIT 1
                """.trimIndent(),
                arrayOf(currentScanOrder.toString())
            )

            if (prevCursor.moveToFirst()) {
                result = prevCursor.getString(0)
            }
            prevCursor.close()

            Log.d("MusicDB", "🔍 getPreviousDiskFolder: $currentFolderPath → $result")
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error en getPreviousDiskFolder: ${e.message}")
        }
        return result
    }

    fun getFolderPathBySongUri(songUri: String): String? {
        var result: String? = null
        try {
            val cursor = readableDatabase.query(
                "songs",
                arrayOf("folderPath"),
                "uri=?",
                arrayOf(songUri),
                null,
                null,
                null,
                "1"
            )

            if (cursor.moveToFirst()) {
                result = cursor.getString(0)
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error en getFolderPathBySongUri: ${e.message}")
        }
        return result
    }

    fun getFirstFolder(): String? {
        var result: String? = null
        try {
            val cursor = readableDatabase.query(
                "folders",
                arrayOf("path"),
                null,
                null,
                null,
                null,
                "scanOrder ASC",
                "1"
            )

            if (cursor.moveToFirst()) {
                result = cursor.getString(0)
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error en getFirstFolder: ${e.message}")
        }
        return result
    }

    fun getLastFolder(): String? {
        var result: String? = null
        try {
            val cursor = readableDatabase.query(
                "folders",
                arrayOf("path"),
                null,
                null,
                null,
                null,
                "scanOrder DESC",
                "1"
            )

            if (cursor.moveToFirst()) {
                result = cursor.getString(0)
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error en getLastFolder: ${e.message}")
        }
        return result
    }

    fun getFirstPlayableFolder(): String? {
        var result: String? = null
        try {
            val cursor = readableDatabase.rawQuery(
                """
                SELECT f.path
                FROM folders f
                WHERE (SELECT COUNT(*) FROM songs s WHERE s.folderPath = f.path) > 0
                ORDER BY f.scanOrder ASC
                LIMIT 1
                """.trimIndent(),
                null
            )

            if (cursor.moveToFirst()) {
                result = cursor.getString(0)
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error en getFirstPlayableFolder: ${e.message}")
        }
        return result
    }

    fun getLastPlayableFolder(): String? {
        var result: String? = null
        try {
            val cursor = readableDatabase.rawQuery(
                """
                SELECT f.path
                FROM folders f
                WHERE (SELECT COUNT(*) FROM songs s WHERE s.folderPath = f.path) > 0
                ORDER BY f.scanOrder DESC
                LIMIT 1
                """.trimIndent(),
                null
            )

            if (cursor.moveToFirst()) {
                result = cursor.getString(0)
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error en getLastPlayableFolder: ${e.message}")
        }
        return result
    }

    fun insertFolder(parent: String?, path: String, name: String, scanOrder: Int) {
        try {
            val db = writableDatabase
            val values = android.content.ContentValues().apply {
                put("parent", parent ?: "")
                put("path", path)
                put("name", name)
                put("scanOrder", scanOrder)
            }
            db.insertWithOnConflict("folders", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error en insertFolder: ${e.message}")
        }
    }

    fun insertSong(folderPath: String, fileName: String, title: String, uri: String) {
        try {
            val db = writableDatabase
            val values = android.content.ContentValues().apply {
                put("folderPath", folderPath)
                put("fileName", fileName)
                put("title", title)
                put("track", 0)
                put("duration", 0)
                put("uri", uri)
            }
            db.insert("songs", null, values)
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error en insertSong: ${e.message}")
        }
    }

    fun clearDatabase() {
        try {
            val db = writableDatabase
            db.delete("songs", null, null)
            db.delete("folders", null, null)
            Log.d("MusicDB", "✅ BD limpiada")
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error limpiando BD: ${e.message}")
        }
    }

    fun deleteDatabase() {
        try {
            clearDatabase()
            close()
            Log.d("MusicDB", "✅ BD limpiada")
        } catch (e: Exception) {
            Log.e("MusicDB", "❌ Error: ${e.message}")
        }
    }
}
