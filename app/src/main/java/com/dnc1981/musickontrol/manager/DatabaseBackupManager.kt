package com.dnc1981.musickontrol.manager

import android.content.Context
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

object DatabaseBackupManager {

    private const val TAG = "DatabaseBackup"
    private const val DATABASE_NAME = "musiclibrary.db"

    fun exportDatabase(context: Context, destinationUri: Uri): Boolean {
        return try {
            val databaseFile = context.getDatabasePath(DATABASE_NAME)

            if (!databaseFile.exists()) {
                Log.e(TAG, "❌ La base de datos no existe")
                return false
            }

            // Aseguramos que todo lo pendiente del WAL se haya pasado al archivo principal.
            try {
                val db = SQLiteDatabase.openDatabase(
                    databaseFile.path,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                db.close()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ No se pudo hacer checkpoint WAL: ${e.message}")
            }

            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                databaseFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return false

            Log.d(TAG, "✅ BD exportada correctamente")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error exportando BD", e)
            false
        }
    }

    fun importDatabase(context: Context, sourceUri: Uri): Boolean {
        return try {
            val databaseFile = context.getDatabasePath(DATABASE_NAME)
            val tempFile = File(context.cacheDir, "musiclibrary_import.tmp")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return false

            // Comprobamos que el archivo recibido sea realmente una BD SQLite válida.
            val importedDb = try {
                SQLiteDatabase.openDatabase(
                    tempFile.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ El archivo seleccionado no es una BD SQLite válida", e)
                tempFile.delete()
                return false
            }

            val valid = importedDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('folders','songs')",
                null
            ).use { cursor ->
                var folders = false
                var songs = false
                while (cursor.moveToNext()) {
                    when (cursor.getString(0)) {
                        "folders" -> folders = true
                        "songs" -> songs = true
                    }
                }
                folders && songs
            }
            importedDb.close()

            if (!valid) {
                Log.e(TAG, "❌ La BD no contiene las tablas de MusicKontrol")
                tempFile.delete()
                return false
            }

            // Eliminamos posibles archivos WAL/SHM de la BD actual.
            File(databaseFile.path + "-wal").delete()
            File(databaseFile.path + "-shm").delete()

            databaseFile.parentFile?.mkdirs()
            tempFile.copyTo(databaseFile, overwrite = true)
            tempFile.delete()

            Log.d(TAG, "✅ BD importada correctamente")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error importando BD", e)
            false
        }
    }
}