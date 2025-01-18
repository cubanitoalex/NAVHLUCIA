package cu.holalinux.navhlucia.utils

import android.content.Context
import android.content.SharedPreferences
import com.downloader.Status
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

object DownloadManager {
    private val activeDownloads = mutableMapOf<Int, DownloadInfo>()
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private const val KEY_DOWNLOAD_HISTORY = "downloads"

    fun init(context: Context) {
        prefs = context.getSharedPreferences("downloads_history", Context.MODE_PRIVATE)
        // Restaurar descargas activas al iniciar
        getDownloadHistory().filter { it.status == Status.RUNNING || it.status == Status.PAUSED }
            .forEach { activeDownloads[it.id] = it }
    }

    fun addDownload(info: DownloadInfo) {
        activeDownloads[info.id] = info
        // Agregar al historial sin eliminar las entradas anteriores
        val history = getDownloadHistory().toMutableList()
        // Actualizar si ya existe, agregar si es nuevo
        val index = history.indexOfFirst { it.id == info.id }
        if (index != -1) {
            history[index] = info
        } else {
            history.add(info)
        }
        saveHistory(history)
    }

    fun removeDownload(id: Int) {
        activeDownloads.remove(id)
        updateHistoryStatus(id, Status.CANCELLED)
    }

    fun updateProgress(id: Int, downloadedBytes: Long, totalBytes: Long) {
        activeDownloads[id]?.let { download ->
            val updated = download.copy(
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                status = Status.RUNNING
            )
            activeDownloads[id] = updated
            updateHistoryProgress(updated)
        }
    }

    fun updateStatus(id: Int, status: Status) {
        activeDownloads[id]?.let { download ->
            val updated = download.copy(status = status)
            activeDownloads[id] = updated
            updateHistoryStatus(id, status)
            if (status == Status.COMPLETED || status == Status.CANCELLED) {
                activeDownloads.remove(id)
            }
        }
    }

    fun getAllDownloads(): List<DownloadInfo> {
        val history = getDownloadHistory()
        // Combinar descargas activas con historial, manteniendo las más recientes
        return (history + activeDownloads.values)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
    }

    fun getDownloadById(id: Int): DownloadInfo? {
        return activeDownloads[id] ?: getDownloadHistory().find { it.id == id }
    }

    private fun saveToHistory(info: DownloadInfo) {
        val history = getDownloadHistory().toMutableList()
        // Actualizar si ya existe, agregar si es nuevo
        val index = history.indexOfFirst { it.id == info.id }
        if (index != -1) {
            history[index] = info
        } else {
            history.add(info)
        }
        saveHistory(history)
    }

    private fun updateHistoryStatus(id: Int, status: Status) {
        val history = getDownloadHistory().toMutableList()
        val index = history.indexOfFirst { it.id == id }
        if (index != -1) {
            history[index] = history[index].copy(status = status)
            saveHistory(history)
        }
    }

    private fun updateHistoryProgress(info: DownloadInfo) {
        val history = getDownloadHistory().toMutableList()
        val index = history.indexOfFirst { it.id == info.id }
        if (index != -1) {
            history[index] = info
            saveHistory(history)
        } else {
            // Si no existe en el historial, agregarlo
            history.add(info)
            saveHistory(history)
        }
    }

    private fun saveHistory(history: List<DownloadInfo>) {
        prefs.edit().putString(KEY_DOWNLOAD_HISTORY, gson.toJson(history)).apply()
    }

    private fun getDownloadHistory(): List<DownloadInfo> {
        val json = prefs.getString(KEY_DOWNLOAD_HISTORY, "[]")
        val type = object : TypeToken<List<DownloadInfo>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun clearHistory() {
        // Limpiar el historial guardado
        prefs.edit().putString(KEY_DOWNLOAD_HISTORY, "[]").apply()
        // Mantener solo las descargas activas
        val activeOnes = activeDownloads.values.filter { 
            it.status == Status.RUNNING || it.status == Status.PAUSED 
        }
        activeDownloads.clear()
        activeOnes.forEach { activeDownloads[it.id] = it }
    }
}

data class DownloadInfo(
    val id: Int,
    val fileName: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: Status,
    val timestamp: Long = System.currentTimeMillis(),
    val url: String = ""
) 