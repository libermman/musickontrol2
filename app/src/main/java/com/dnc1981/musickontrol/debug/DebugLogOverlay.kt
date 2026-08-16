package com.dnc1981.musickontrol.debug

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object DebugLogOverlay {

    private val logs = mutableStateListOf<String>()

    fun addLog(tag: String, message: String, isError: Boolean = false) {
        // ✅ COMENTADO: Descomenta para activar debug
        /*
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val logMessage = "[$timestamp] $tag: $message"
        logs.add(0, logMessage)

        // Mantener solo los últimos 50 logs
        if (logs.size > 50) {
            logs.removeAt(logs.size - 1)
        }

        // También enviar a Logcat
        if (isError) {
            Log.e(tag, message)
        } else {
            Log.d(tag, message)
        }
        */
    }

    fun getLogs(): List<String> = logs.toList()

    fun clearLogs() {
        logs.clear()
    }

    @Composable
    fun DebugPanel(modifier: Modifier = Modifier) {
        // ✅ COMENTADO: Descomenta para ver panel en pantalla
        /*
        if (logs.isEmpty()) return

        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.9f))
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                logs.forEach { log ->
                    val color = when {
                        log.contains("❌") -> Color.Red
                        log.contains("✅") -> Color(0xFF00FF00)
                        log.contains("🔍") -> Color.Cyan
                        log.contains("📁") -> Color.Yellow
                        log.contains("🎵") -> Color(0xFFFF69B4)
                        else -> Color.White
                    }

                    Text(
                        text = log,
                        color = color,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
        */
    }

}