package com.dnc1981.musickontrol.database

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.dnc1981.musickontrol.debug.DebugLogOverlay

class MusicScanner(

    private val context: Context,

    private val database: MusicDatabaseHelper

) {

    private var scanOrder = 0

    fun scan(root: DocumentFile) {

        Thread {

            scanOrder = 0

            val db = database.writableDatabase

            try {

                // Limpiamos la BD en una transacción corta.
                // El escaneo posterior se hace fuera de esta transacción para que
                // las canciones y carpetas puedan estar disponibles mientras continúa.
                db.beginTransaction()
                try {
                    db.execSQL("DELETE FROM songs")
                    db.execSQL("DELETE FROM folders")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }

                // DebugLogOverlay.addLog("MusicScanner", "🧹 BD limpiada")

                // IMPORTANTE: no envolver todo el escaneo en una única transacción.
                // Cada INSERT se confirma individualmente, permitiendo que otras partes
                // de la aplicación puedan consultar la música mientras se indexa.
                scanFolder(root)

                // DebugLogOverlay.addLog("MusicScanner", "✅ Escaneo completado. Total carpetas: $scanOrder")

            } catch (e: Exception) {

                // DebugLogOverlay.addLog("MusicScanner", "❌ ERROR EN ESCANEO: ${e.message}", isError = true)
                e.printStackTrace()

            } finally {

                // No cerramos aquí database.writableDatabase: el MusicDatabaseHelper
                // es quien gestiona la conexión compartida de la aplicación.

            }

        }.start()

    }

    private fun scanFolder(
        folder: DocumentFile
    ): Boolean {

        if (!folder.isDirectory) {
            // DebugLogOverlay.addLog("MusicScanner", "⚠️  No es directorio: ${folder.name}")
            return false
        }

        var containsMusic = false

        try {

            val files = folder.listFiles().sortedBy {
                it.name?.lowercase() ?: ""
            }

            // DebugLogOverlay.addLog("MusicScanner", "📂 Escaneando: ${folder.name} (${files.size} elementos)")

            files.forEach { file ->

                if (file.isDirectory) {

                    if (scanFolder(file)) {
                        containsMusic = true
                    }

                } else {

                    if (isAudio(file.name)) {

                        containsMusic = true
                        saveSong(folder, file)

                    }

                }

            }

        } catch (e: Exception) {

            // DebugLogOverlay.addLog("MusicScanner", "❌ Error en carpeta ${folder.name}: ${e.message}", isError = true)
            e.printStackTrace()

        }

        if (containsMusic) {
            saveFolder(folder)
        }

        return containsMusic

    }

    private fun saveFolder(
        folder: DocumentFile
    ) {

        scanOrder++

        try {

            val db = database.writableDatabase

            db.execSQL(

                """
                INSERT OR REPLACE INTO folders(
                    parent,
                    path,
                    name,
                    scanOrder
                )
                VALUES(?,?,?,?)
                """.trimIndent(),

                arrayOf(

                    folder.parentFile?.uri?.toString() ?: "",

                    folder.uri.toString(),

                    folder.name ?: "",

                    scanOrder

                )

            )

            // DebugLogOverlay.addLog("MusicScanner", "📁 Guardada: ${folder.name} (#$scanOrder)")

        } catch (e: Exception) {

            // DebugLogOverlay.addLog("MusicScanner", "❌ Error guardando carpeta: ${e.message}", isError = true)
            e.printStackTrace()

        }

    }

    private fun saveSong(

        folder: DocumentFile,

        file: DocumentFile

    ) {

        try {

            val db = database.writableDatabase

            db.execSQL(

                """
                INSERT INTO songs(
                    folderPath,
                    fileName,
                    title,
                    track,
                    duration,
                    uri
                )
                VALUES(?,?,?,?,?,?)
                """.trimIndent(),

                arrayOf(

                    folder.uri.toString(),

                    file.name ?: "",

                    file.name
                        ?.substringBeforeLast(".")
                        ?: "",

                    extractTrack(file.name),

                    0,

                    file.uri.toString()

                )

            )

            // DebugLogOverlay.addLog("MusicScanner", "🎵 ${file.name}")

        } catch (e: Exception) {

            // DebugLogOverlay.addLog("MusicScanner", "❌ Error canción: ${e.message}", isError = true)
            e.printStackTrace()

        }

    }

    private fun extractTrack(
        name: String?
    ): Int {

        if (name == null)
            return 0

        val number = name.takeWhile {
            it.isDigit()
        }

        return number.toIntOrNull() ?: 0

    }

    private fun isAudio(
        name: String?
    ): Boolean {

        if (name == null)
            return false

        val n = name.lowercase()

        return n.endsWith(".mp3") ||
                n.endsWith(".flac") ||
                n.endsWith(".wav") ||
                n.endsWith(".aac") ||
                n.endsWith(".ogg") ||
                n.endsWith(".m4a") ||
                n.endsWith(".opus")

    }

}