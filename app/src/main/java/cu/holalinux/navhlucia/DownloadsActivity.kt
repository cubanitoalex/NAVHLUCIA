package cu.holalinux.navhlucia

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.downloader.PRDownloader
import com.downloader.Status
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import cu.holalinux.navhlucia.utils.DownloadManager
import cu.holalinux.navhlucia.utils.DownloadInfo
import cu.holalinux.navhlucia.utils.Logger
import java.text.SimpleDateFormat
import java.util.*

class DownloadsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DownloadsAdapter
    private val downloads = mutableListOf<DownloadInfo>()
    private lateinit var emptyView: TextView
    private lateinit var clearHistoryButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.d("DownloadsActivity: onCreate")
        setContentView(R.layout.activity_downloads)

        // Configurar la toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(false)
        }

        // Inicializar vistas
        recyclerView = findViewById(R.id.downloadsRecyclerView)
        emptyView = findViewById(R.id.emptyView)
        
        // Configurar RecyclerView
        adapter = DownloadsAdapter(downloads) { id, action ->
            handleDownloadAction(id, action)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Actualizar lista inicial
        updateDownloadsList()

        // Verificar si hay descargas
        val downloadsList = DownloadManager.getAllDownloads()
        Logger.d("DownloadsActivity: Número de descargas: ${downloadsList.size}")

        // Inicializar el botón de limpiar historial
        clearHistoryButton = findViewById(R.id.clearHistoryButton)
        clearHistoryButton.setOnClickListener {
            showClearHistoryConfirmation()
        }
    }

    private fun updateDownloadsList() {
        Logger.d("DownloadsActivity: Actualizando lista de descargas")
        downloads.clear()
        downloads.addAll(DownloadManager.getAllDownloads())
        adapter.notifyDataSetChanged()

        // Mostrar mensaje si no hay descargas
        if (downloads.isEmpty()) {
            Logger.d("DownloadsActivity: No hay descargas")
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            Logger.d("DownloadsActivity: Hay ${downloads.size} descargas")
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun handleDownloadAction(downloadId: Int, action: DownloadAction) {
        when (action) {
            DownloadAction.PAUSE -> {
                PRDownloader.pause(downloadId)
                DownloadManager.updateStatus(downloadId, Status.PAUSED)
                Toast.makeText(this, "Descarga pausada", Toast.LENGTH_SHORT).show()
            }
            DownloadAction.RESUME -> {
                val download = DownloadManager.getDownloadById(downloadId)
                if (download != null) {
                    // Recrear la descarga con la URL original
                    PRDownloader.resume(downloadId)
                    DownloadManager.updateStatus(downloadId, Status.RUNNING)
                    Toast.makeText(this, "Descarga reanudada", Toast.LENGTH_SHORT).show()
                }
            }
            DownloadAction.CANCEL -> {
                PRDownloader.cancel(downloadId)
                DownloadManager.updateStatus(downloadId, Status.CANCELLED)
                Toast.makeText(this, "Descarga cancelada", Toast.LENGTH_SHORT).show()
            }
        }
        updateDownloadsList()
    }

    private fun showClearHistoryConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Limpiar Historial")
            .setMessage("¿Está seguro que desea eliminar todo el historial de descargas?")
            .setPositiveButton("Sí") { _, _ ->
                DownloadManager.clearHistory()
                updateDownloadsList()
                Toast.makeText(this, "Historial eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDownloadsList()
            updateHandler.postDelayed(this, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        updateHandler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        updateHandler.removeCallbacks(updateRunnable)
    }
}

enum class DownloadAction {
    PAUSE, RESUME, CANCEL
}

data class DownloadInfo(
    val id: Int,
    val fileName: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: Status
)

class DownloadsAdapter(
    private val downloads: List<DownloadInfo>,
    private val onAction: (Int, DownloadAction) -> Unit
) : RecyclerView.Adapter<DownloadsAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileTypeIcon: ImageView = view.findViewById(R.id.fileTypeIcon)
        val fileNameText: TextView = view.findViewById(R.id.fileNameText)
        val dateText: TextView = view.findViewById(R.id.dateText)
        val statusText: TextView = view.findViewById(R.id.statusText)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val progressText: TextView = view.findViewById(R.id.progressText)
        val actionButtons: LinearLayout = view.findViewById(R.id.actionButtons)
        val cancelButton: MaterialButton = view.findViewById(R.id.cancelButton)
        val pauseButton: MaterialButton = view.findViewById(R.id.pauseButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val download = downloads[position]
        
        holder.fileNameText.text = download.fileName
        holder.dateText.text = dateFormat.format(Date(download.timestamp))
        
        val progress = if (download.totalBytes > 0) {
            ((download.downloadedBytes * 100) / download.totalBytes).toInt()
        } else 0
        
        holder.progressBar.progress = progress
        holder.progressText.text = "${formatBytes(download.downloadedBytes)}/${formatBytes(download.totalBytes)}"

        // Configurar el estado y los botones según el status
        when (download.status) {
            Status.RUNNING -> {
                holder.statusText.text = "EN PROGRESO"
                holder.statusText.setBackgroundResource(R.color.download_running)
                holder.actionButtons.visibility = View.VISIBLE
                holder.pauseButton.text = "PAUSAR"
                holder.pauseButton.isEnabled = true
                holder.cancelButton.isEnabled = true
            }
            Status.PAUSED -> {
                holder.statusText.text = "PAUSADO"
                holder.statusText.setBackgroundResource(R.color.download_paused)
                holder.actionButtons.visibility = View.VISIBLE
                holder.pauseButton.text = "REANUDAR"
                holder.pauseButton.isEnabled = true
                holder.cancelButton.isEnabled = true
            }
            Status.COMPLETED -> {
                holder.statusText.text = "COMPLETADO"
                holder.statusText.setBackgroundResource(R.color.download_completed)
                holder.actionButtons.visibility = View.GONE
            }
            Status.CANCELLED -> {
                holder.statusText.text = "CANCELADO"
                holder.statusText.setBackgroundResource(R.color.download_cancelled)
                holder.actionButtons.visibility = View.GONE
            }
            else -> {
                holder.statusText.text = "ERROR"
                holder.statusText.setBackgroundResource(R.color.download_error)
                holder.actionButtons.visibility = View.GONE
            }
        }

        // Configurar los botones de acción
        holder.pauseButton.setOnClickListener {
            if (download.status == Status.RUNNING) {
                onAction(download.id, DownloadAction.PAUSE)
            } else if (download.status == Status.PAUSED) {
                onAction(download.id, DownloadAction.RESUME)
            }
        }

        holder.cancelButton.setOnClickListener {
            onAction(download.id, DownloadAction.CANCEL)
        }

        // Configurar el icono según el tipo de archivo
        holder.fileTypeIcon.setImageResource(getFileTypeIcon(download.fileName))
    }

    private fun getFileTypeIcon(fileName: String): Int {
        return when {
            fileName.endsWith(".pdf", true) -> R.drawable.ic_pdf
            fileName.endsWith(".doc", true) || fileName.endsWith(".docx", true) -> R.drawable.ic_doc
            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) || 
            fileName.endsWith(".png", true) -> R.drawable.ic_image
            fileName.endsWith(".mp3", true) -> R.drawable.ic_audio
            fileName.endsWith(".mp4", true) -> R.drawable.ic_video
            else -> R.drawable.ic_file
        }
    }

    override fun getItemCount() = downloads.size

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
} 