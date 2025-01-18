package cu.holalinux.navhlucia

import android.app.Activity
import android.content.Context

import android.graphics.Bitmap

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.google.android.material.textfield.TextInputEditText
import cu.holalinux.navhlucia.utils.Logger
import java.net.Authenticator
import java.net.PasswordAuthentication
import android.app.DownloadManager
import android.media.MediaScannerConnection

import android.app.NotificationChannel
import android.app.NotificationManager

import androidx.core.app.NotificationCompat
import com.downloader.OnDownloadListener
import com.downloader.PRDownloader
import com.downloader.PRDownloaderConfig
import java.io.File
import android.content.pm.PackageManager
import android.net.http.SslError

import android.widget.TextView
import cu.holalinux.navhlucia.utils.DownloadManager as AppDownloadManager
import cu.holalinux.navhlucia.utils.DownloadInfo
import com.downloader.Status

import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.view.WindowManager

import android.net.Uri
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import com.google.gson.Gson
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.withContext
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 2000L // 2 segundos

    private val DEFAULT_URL = "http://www.hcqho.sld.cu/informativo-digital/"
    private val PREFS_NAME = "ProxyPrefs"
    private val KEY_PROXY_IP = "proxy_ip"
    private val KEY_PROXY_PORT = "proxy_port"
    private val KEY_PROXY_USER = "proxy_user"
    private val KEY_PROXY_PASS = "proxy_pass"
    private val KEY_FIRST_TIME = "first_time"
    private var currentUrl: String = DEFAULT_URL
    private var lastAuthHandler: HttpAuthHandler? = null
    private lateinit var backMenuItem: MenuItem

    private val speedDialUrls = mapOf(
        R.id.action_google to "https://www.google.com",
        R.id.action_facebook to "https://m.facebook.com",
        R.id.action_twitter to "https://mobile.twitter.com"
    )
    private lateinit var urlEditText: TextInputEditText

    private var doubleBackToExitPressedOnce = false

    private lateinit var downloadManager: DownloadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.i("MainActivity onCreate iniciado")

        // Verificar si es la primera vez que se ejecuta la app
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstTime = prefs.getBoolean(KEY_FIRST_TIME, true)

        if (isFirstTime || !ProxyConfigActivity.isProxyConfigured(this)) {
            Logger.i("Primera ejecución o proxy no configurado, mostrando configuración")
            // Guardar que ya no es la primera vez
            prefs.edit().putBoolean(KEY_FIRST_TIME, false).apply()
            
            val intent = Intent(this, ProxyConfigActivity::class.java)
            intent.putExtra("isFirstTime", true)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Quitar el título de la barra de acción
        supportActionBar?.apply {
            title = ""
            setDisplayShowTitleEnabled(false)
        }

        // Initialize views
        initializeViews()
        
        // Setup WebView
        setupWebView()

        // Apply saved proxy settings
        applyProxySettings()

        // Inicializar PRDownloader con la configuración del proxy
        initPRDownloader()

        // Inicializar el gestor de descargas
        cu.holalinux.navhlucia.utils.DownloadManager.init(applicationContext)

        // Load initial URL
        loadUrl(DEFAULT_URL)
        Logger.i("MainActivity onCreate completado")

        // Solicitar permisos al inicio
        checkAndRequestPermissions()

        setupAppUpdater()
    }

    private fun initializeViews() {
        Logger.d("Inicializando vistas")
        webView = findViewById(R.id.webView)
        urlEditText = findViewById(R.id.urlEditText)
        
        // Configurar FAB de búsqueda de Google
        findViewById<FloatingActionButton>(R.id.fabGoogleSearch).setOnClickListener {
            showGoogleSearchDialog()
        }
        
        // Configurar el manejo de la entrada de URL
        urlEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val url = v.text.toString()
                loadUrlWithHttpCheck(url)
                hideKeyboard()
                true
            } else {
                false
            }
        }
    }

    private fun setupWebView() {
        Logger.d("Configurando WebView")
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            
            // Habilitar guardado de formularios y contraseñas
            saveFormData = true
            savePassword = true
            databaseEnabled = true
            
            // User-Agent de Firefox
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
        }

        // Habilitar el guardado de contraseñas
        WebView.setWebContentsDebuggingEnabled(true)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val proxyUser = prefs.getString(KEY_PROXY_USER, "") ?: ""
        val proxyPass = prefs.getString(KEY_PROXY_PASS, "") ?: ""

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { 
                    currentUrl = it
                    urlEditText.setText(it)
                    Logger.i("Iniciando carga de página: $url")
                    updateBackButton()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Logger.i("Página cargada completamente: $url")
                retryCount = 0
                updateBackButton()
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler?,
                host: String?,
                realm: String?
            ) {
                Logger.d("Recibida solicitud de autenticación HTTP para host: $host")
                lastAuthHandler = handler
                handler?.proceed(proxyUser, proxyPass)
            }

            override fun onReceivedLoginRequest(
                view: WebView?,
                realm: String?,
                account: String?,
                args: String?
            ) {
                Logger.d("Recibida solicitud de login para realm: $realm")
                super.onReceivedLoginRequest(view, realm, account, args)
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                val errorMsg = "Error cargando página: ${error?.description} (código: ${error?.errorCode})"
                Logger.e(errorMsg)

                when {
                    error?.description?.contains("ERR_TUNNEL_CONNECTION_FAILED") == true -> {

                    }
                    error?.description?.contains("ERR_PROXY_CONNECTION_FAILED") == true -> {
                        handleProxyConnectionError()
                    }
                    else -> {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                val errorMsg = "Error HTTP: ${errorResponse?.statusCode} - ${errorResponse?.reasonPhrase}"
                Logger.e(errorMsg)
                
                // Si recibimos error 407, intentamos reautenticar usando el último handler
                if (errorResponse?.statusCode == 407 && lastAuthHandler != null) {
                    Logger.d("Recibido error 407, reintentando con autenticación")
                    lastAuthHandler?.proceed(proxyUser, proxyPass)
                }
                
                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                
                // Verificar si es un archivo descargable
                if (isDownloadableFile(url)) {
                    startDownload(url)
                    return true
                }
                return false
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                Logger.w("SSL Error recibido: ${error?.primaryError}")
                
                // Crear un diálogo personalizado
                val dialogView = layoutInflater.inflate(R.layout.dialog_ssl_warning, null)
                
                val dialog = AlertDialog.Builder(this@MainActivity, R.style.AlertDialogCustom)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create()

                // Configurar los botones
                dialogView.findViewById<Button>(R.id.btnContinuar).setOnClickListener {
                    handler?.proceed()
                    dialog.dismiss()
                }

                dialogView.findViewById<Button>(R.id.btnCancelar).setOnClickListener {
                    handler?.cancel()
                    dialog.dismiss()
                }

                // Personalizar el mensaje según el tipo de error SSL
                val mensajeError = when (error?.primaryError) {
                    SslError.SSL_DATE_INVALID -> "El certificado ha expirado o aún no es válido"
                    SslError.SSL_EXPIRED -> "El certificado ha expirado"
                    SslError.SSL_IDMISMATCH -> "El nombre del host no coincide con el certificado"
                    SslError.SSL_NOTYETVALID -> "El certificado aún no es válido"
                    SslError.SSL_UNTRUSTED -> "El certificado no es de confianza"
                    else -> "Error de certificado SSL desconocido"
                }
                
                dialogView.findViewById<TextView>(R.id.tvMensajeError).text = mensajeError

                dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
                dialog.show()
            }

            // Configuración para permitir certificados personalizados
            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                // Permitir todos los certificados
                trustAllCertificates()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Logger.d("Console [${it.messageLevel()}]: ${it.message()} - ${it.sourceId()}:${it.lineNumber()}")
                }
                return true
            }
        }

        // Configurar manejo de descargas
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            startDownload(url)
        }
    }

    private fun trustAllCertificates() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)

            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Logger.e("Error configurando certificados SSL: ${e.message}", e)
        }
    }

    private fun handleProxyConnectionError() {
        runOnUiThread {
            Toast.makeText(this, "Error de conexión con el proxy. Verificando configuración...", Toast.LENGTH_LONG).show()
        }
        // Reaplica la configuración del proxy
        applyProxySettings()
    }

    private fun applyProxySettings() {
        Logger.i("Aplicando configuración del proxy")
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val proxyHost = prefs.getString(KEY_PROXY_IP, "") ?: ""
        val proxyPort = prefs.getString(KEY_PROXY_PORT, "") ?: ""
        val proxyUser = prefs.getString(KEY_PROXY_USER, "") ?: ""
        val proxyPass = prefs.getString(KEY_PROXY_PASS, "") ?: ""

        Logger.d("Configuración proxy - Host: $proxyHost, Port: $proxyPort, User: ${if (proxyUser.isNotEmpty()) "configurado" else "no configurado"}")

        try {
            // Configurar autenticador global
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(proxyUser, proxyPass.toCharArray())
                }
            })

            // Configurar propiedades del sistema
            System.setProperty("http.proxyHost", proxyHost)
            System.setProperty("http.proxyPort", proxyPort)
            System.setProperty("https.proxyHost", proxyHost)
            System.setProperty("https.proxyPort", proxyPort)
            System.setProperty("http.proxyUser", proxyUser)
            System.setProperty("http.proxyPassword", proxyPass)
            System.setProperty("https.proxyUser", proxyUser)
            System.setProperty("https.proxyPassword", proxyPass)

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                Logger.d("ProxyOverride es soportado")

                // Crear configuración de proxy para ProxyController
                val proxyConfig = ProxyConfig.Builder()
                    .addProxyRule("$proxyHost:$proxyPort")
                    .build()

                // Aplicar configuración usando ProxyController
                ProxyController.getInstance().setProxyOverride(proxyConfig, {
                    // Success callback
                    Logger.i("Proxy configurado exitosamente")
                    runOnUiThread {
                        Toast.makeText(this, "Proxy configurado exitosamente", Toast.LENGTH_SHORT).show()
                        webView.reload()
                    }
                }, {
                    // Error callback
                    val errorMsg = "Error al configurar el proxy"
                    Logger.e(errorMsg)
                    runOnUiThread {
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                    }
                })
            } else {
                val errorMsg = "Proxy override no soportado en este dispositivo"
                Logger.e(errorMsg)
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            val errorMsg = "Error aplicando configuración del proxy: ${e.message}"
            Logger.e(errorMsg, e)
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshWebView() {
        try {
            Logger.i("Iniciando refresh del WebView")
            webView.stopLoading()
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
            loadUrl(currentUrl)
            Logger.i("Refresh completado")
        } catch (e: Exception) {
            val errorMsg = "Error al refrescar WebView: ${e.message}"
            Logger.e(errorMsg, e)
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUrl(url: String) {
        try {
            Logger.i("Cargando URL: $url")
            webView.loadUrl(url)
        } catch (e: Exception) {
            val errorMsg = "Error al cargar URL: ${e.message}"
            Logger.e(errorMsg, e)
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        when {
            webView.canGoBack() -> {
                webView.goBack()
            }
            currentUrl != DEFAULT_URL -> {
                // Si no estamos en la página principal, volver a ella
                loadUrl(DEFAULT_URL)
                Toast.makeText(this, "Volviendo a la página principal", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // Ya estamos en la página principal, manejar la salida
                if (doubleBackToExitPressedOnce) {
                    showExitConfirmationDialog()
                } else {
                    doubleBackToExitPressedOnce = true
                    Toast.makeText(this, "Presione ATRÁS otra vez para salir", Toast.LENGTH_SHORT).show()

                    Handler(Looper.getMainLooper()).postDelayed({
                        doubleBackToExitPressedOnce = false
                    }, 2000)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Solo guardar estado si webView está inicializada
        if (::webView.isInitialized) {
            Logger.d("Guardando estado del WebView")
            webView.saveState(outState)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Solo restaurar estado si webView está inicializada
        if (::webView.isInitialized) {
            Logger.d("Restaurando estado del WebView")
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onDestroy() {
        Logger.i("MainActivity onDestroy")
        handler.removeCallbacksAndMessages(null) // Limpia cualquier reintento pendiente
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        backMenuItem = menu.findItem(R.id.action_back)
        updateBackButton()
        return true
    }

    private fun updateBackButton() {
        if (::backMenuItem.isInitialized) {
            backMenuItem.isEnabled = webView.canGoBack()
            backMenuItem.icon?.alpha = if (webView.canGoBack()) 255 else 130
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_back -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    Toast.makeText(this, "No hay páginas anteriores", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_home -> {
                loadUrl(DEFAULT_URL)
                Toast.makeText(this, "Volviendo a la página principal", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_refresh -> {
                refreshWebView()
                true
            }
            R.id.action_sites -> {
                showSitesMenu()
                true
            }
            R.id.action_settings -> {
                val intent = Intent(this, ProxyConfigActivity::class.java)
                intent.putExtra("isFirstTime", false)
                startActivityForResult(intent, PROXY_CONFIG_REQUEST)
                true
            }
            R.id.action_downloads -> {
                startActivity(Intent(this, DownloadsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val PROXY_CONFIG_REQUEST = 1001
        private const val PERMISSION_REQUEST_CODE = 1002
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROXY_CONFIG_REQUEST && resultCode == Activity.RESULT_OK) {
            // Marcar que ya no es la primera vez
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                putBoolean(KEY_FIRST_TIME, false)
                apply()
            }
            // Recargar la configuración del proxy y la página
            applyProxySettings()
            refreshWebView()
        }
    }

    private fun showSitesMenu() {
        val popup = PopupMenu(this, findViewById(R.id.action_sites))
        popup.menuInflater.inflate(R.menu.speed_dial_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_google -> {
                    loadUrl("https://www.google.com")
                    true
                }
                R.id.action_facebook -> {
                    loadUrl("https://m.facebook.com")
                    true
                }
                R.id.action_twitter -> {
                    loadUrl("https://mobile.twitter.com")
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }

    private fun loadUrlWithHttpCheck(url: String) {
        var processedUrl = url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            processedUrl = "http://$url"
        }
        loadUrl(processedUrl)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlEditText.windowToken, 0)
    }

    private fun showExitConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_exit_confirmation, null)
        
        val dialog = AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnConfirmExit).setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialogView.findViewById<Button>(R.id.btnCancelExit).setOnClickListener {
            dialog.dismiss()
            doubleBackToExitPressedOnce = false
        }

        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
        dialog.show()
    }

    private fun initPRDownloader() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val proxyHost = prefs.getString(KEY_PROXY_IP, "") ?: ""
        val proxyPort = prefs.getString(KEY_PROXY_PORT, "") ?: ""
        val proxyUser = prefs.getString(KEY_PROXY_USER, "") ?: ""
        val proxyPass = prefs.getString(KEY_PROXY_PASS, "") ?: ""

        // Configurar proxy a nivel de sistema
        System.setProperty("http.proxyHost", proxyHost)
        System.setProperty("http.proxyPort", proxyPort)
        System.setProperty("https.proxyHost", proxyHost)
        System.setProperty("https.proxyPort", proxyPort)

        if (proxyUser.isNotEmpty() && proxyPass.isNotEmpty()) {
            System.setProperty("http.proxyUser", proxyUser)
            System.setProperty("http.proxyPassword", proxyPass)
            System.setProperty("https.proxyUser", proxyUser)
            System.setProperty("https.proxyPassword", proxyPass)

            // Configurar autenticador para el proxy
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(proxyUser, proxyPass.toCharArray())
                }
            })
        }

        // Log para verificar la configuración del proxy
        Logger.d("PRDownloader - Proxy configurado: $proxyHost:$proxyPort")

        val config = PRDownloaderConfig.newBuilder()
            .setConnectTimeout(30000)
            .setReadTimeout(30000)
            .setDatabaseEnabled(true)
            .build()
        
        PRDownloader.initialize(applicationContext, config)
    }

    private fun isDownloadableFile(url: String): Boolean {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return when (extension.toLowerCase()) {
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv",  
            "zip", "rar", "7z", "tar", "gz", "apk","exe",
            "mp3", "mp4", "avi", "mkv",
            "jpg", "jpeg", "png", "gif" -> true
            else -> false
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14
            val permissions = mutableListOf<String>()
            
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
            
            if (permissions.isNotEmpty()) {
                requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13
            val permissions = mutableListOf<String>()
            
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
            
            if (permissions.isNotEmpty()) {
                requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Android 6-12
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                    ),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permisos de almacenamiento concedidos", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Se requieren permisos de almacenamiento para las descargas", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startDownload(url: String) {
        // Verificar permisos según la versión de Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                checkAndRequestPermissions()
                Toast.makeText(this, "Se requieren permisos para acceder a los archivos", Toast.LENGTH_LONG).show()
                return
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestPermissions()
            Toast.makeText(this, "Se requieren permisos de almacenamiento", Toast.LENGTH_LONG).show()
            return
        }

        val fileName = URLUtil.guessFileName(url, null, null)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        // Crear la descarga y obtener el ID
        val downloadRequest = PRDownloader.download(url, downloadDir.path, fileName)
            .build()
        
        val downloadId = downloadRequest.downloadId

        // Registrar la descarga antes de iniciarla
        AppDownloadManager.addDownload(
            DownloadInfo(
                id = downloadId,
                fileName = fileName,
                totalBytes = 0,
                downloadedBytes = 0,
                status = Status.RUNNING,
                url = url
            )
        )

        downloadRequest
            .setOnStartOrResumeListener {
                AppDownloadManager.updateStatus(downloadId, Status.RUNNING)
                Toast.makeText(this, "Iniciando descarga: $fileName", Toast.LENGTH_SHORT).show()
            }
            .setOnProgressListener { progress ->
                AppDownloadManager.updateProgress(
                    downloadId,
                    progress.currentBytes,
                    progress.totalBytes
                )
                updateNotificationProgress(fileName, progress.currentBytes, progress.totalBytes)
            }
            .setOnPauseListener {
                AppDownloadManager.updateStatus(downloadId, Status.PAUSED)
            }
            .setOnCancelListener {
                AppDownloadManager.updateStatus(downloadId, Status.CANCELLED)
                Toast.makeText(this, "Descarga cancelada: $fileName", Toast.LENGTH_SHORT).show()
            }
            .start(object : OnDownloadListener {
                override fun onDownloadComplete() {
                    AppDownloadManager.updateStatus(downloadId, Status.COMPLETED)
                    Toast.makeText(this@MainActivity, 
                        "Descarga completada: $fileName\nGuardado en: Descargas", 
                        Toast.LENGTH_LONG).show()
                    
                    MediaScannerConnection.scanFile(
                        this@MainActivity,
                        arrayOf(File(downloadDir, fileName).toString()),
                        null
                    ) { path, uri -> 
                        Logger.d("Archivo escaneado: $path") 
                    }
                }

                override fun onError(error: com.downloader.Error?) {
                    AppDownloadManager.updateStatus(downloadId, Status.FAILED)
                    Toast.makeText(this@MainActivity,
                        "Error al descargar $fileName: ${error?.connectionException?.message ?: "Error desconocido"}",
                        Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun updateNotificationProgress(fileName: String, downloadedBytes: Long, totalBytes: Long) {
        val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
        
        val builder = NotificationCompat.Builder(this, "download_channel")
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Descargando $fileName")
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Crear canal de notificación para Android 8.0 y superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "download_channel",
                "Descargas",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(fileName.hashCode(), builder.build())
    }

    private fun showGoogleSearchDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_google_search, null)
        val searchEditText = dialogView.findViewById<TextInputEditText>(R.id.searchEditText)

        val dialog = AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setView(dialogView)
            .create()

        // Configurar el botón de búsqueda
        dialogView.findViewById<Button>(R.id.btnSearch).setOnClickListener {
            val searchQuery = searchEditText.text.toString()
            if (searchQuery.isNotEmpty()) {
                val searchUrl = "https://www.google.com/search?q=${searchQuery.replace(" ", "+")}"
                loadUrl(searchUrl)
                dialog.dismiss()
            }
        }

        // Configurar el botón de cancelar
        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        // Mostrar el teclado automáticamente
        searchEditText.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
        dialog.show()
    }

    private fun setupAppUpdater() {
        Logger.d("Iniciando verificación de actualizaciones")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val updateUrl = "http://www.hcqho.sld.cu/pub/apk/nav_hlucia_app/nav_hlucia_app_update.json"
                val connection = URL(updateUrl).openConnection() as HttpURLConnection
                connection.apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Usar TypeToken para preservar la información de tipo
                val updateInfo = Gson().fromJson<UpdateInfo>(
                    response,
                    object : TypeToken<UpdateInfo>() {}.type
                )
                
                val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode
                
                withContext(Dispatchers.Main) {
                    if (updateInfo.latestVersionCode > currentVersion) {
                        showUpdateDialog(updateInfo)
                    }
                }
            } catch (e: Exception) {
                Logger.e("Error verificando actualizaciones", e)
            }
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_update, null)
        
        dialogView.findViewById<TextView>(R.id.tvVersion).text = 
            "Nueva versión ${updateInfo.latestVersion} disponible"
        
        val releaseNotesText = buildString {
            append("Novedades:\n")
            updateInfo.releaseNotes.forEach { note ->
                append("• $note\n")
            }
        }
        dialogView.findViewById<TextView>(R.id.tvReleaseNotes).text = releaseNotesText

        val dialog = AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.btnUpdate).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.url)))
                dialog.dismiss()
            } catch (e: Exception) {
                Toast.makeText(this, 
                    "Error al abrir la actualización: ${e.message}", 
                    Toast.LENGTH_LONG).show()
            }
        }

        dialogView.findViewById<Button>(R.id.btnLater).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    data class UpdateInfo(
        val latestVersion: String,
        val latestVersionCode: Int,
        val releaseNotes: List<String>,
        val url: String
    )
}
