package cu.holalinux.navhlucia

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

import cu.holalinux.navhlucia.utils.Logger
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.PasswordAuthentication
import java.net.URL
import android.text.method.PasswordTransformationMethod


class ProxyConfigActivity : AppCompatActivity() {
    private lateinit var proxyUserEdit: TextInputEditText
    private lateinit var proxyPassEdit: TextInputEditText
    private lateinit var proxyPassLayout: TextInputLayout
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var saveProxyButton: MaterialButton
    private lateinit var testProxyButton: MaterialButton
    private val TEST_URL = "http://www.msftncsi.com/ncsi.txt"
    private val EXPECTED_RESPONSE = "Microsoft NCSI"

    private val PREFS_NAME = "ProxyPrefs"
    private val KEY_PROXY_USER = "proxy_user"
    private val KEY_PROXY_PASS = "proxy_pass"
    private val KEY_PROXY_CONFIGURED = "proxy_configured"

    // Valores constantes del proxy
    companion object {
        private const val PROXY_HOST = "192.168.1.254"
        private const val PROXY_PORT = "3128"
        
        fun isProxyConfigured(context: Context): Boolean {
            return context.getSharedPreferences("ProxyPrefs", Context.MODE_PRIVATE)
                .getBoolean("proxy_configured", false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_proxy_config)

        initializeViews()
        setupBiometric()
        loadSavedProxySettings()
        setupButtons()

        // Configurar el comportamiento del ícono de visibilidad
        proxyPassLayout.apply {
            // Deshabilitar el toggle por defecto
            endIconMode = TextInputLayout.END_ICON_CUSTOM
            setEndIconDrawable(R.drawable.ic_visibility_off)
            
            // Configurar el click del ícono
            setEndIconOnClickListener {
                if (!proxyPassEdit.text.isNullOrEmpty()) {
                    showBiometricPrompt()
                }
            }
        }

        // Mostrar el mensaje de bienvenida/advertencia al iniciar
        if (intent.getBooleanExtra("isFirstTime", true)) {
            showAlertDialog()
        }
    }

    private fun initializeViews() {
        proxyUserEdit = findViewById(R.id.proxyUserEdit)
        proxyPassEdit = findViewById(R.id.proxyPassEdit)
        proxyPassLayout = findViewById(R.id.proxyPassLayout)
        saveProxyButton = findViewById(R.id.saveProxyButton)
        testProxyButton = findViewById(R.id.testProxyButton)
    }

    private fun loadSavedProxySettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        proxyUserEdit.setText(prefs.getString(KEY_PROXY_USER, ""))
        proxyPassEdit.setText(prefs.getString(KEY_PROXY_PASS, ""))
    }

    private fun setupButtons() {
        saveProxyButton.setOnClickListener {
            saveProxySettings()
        }

        testProxyButton.setOnClickListener {
            testProxyAuthentication()
        }
    }

    private fun saveProxySettings() {
        val proxyUser = proxyUserEdit.text.toString()
        val proxyPass = proxyPassEdit.text.toString()

        if (proxyUser.isEmpty() || proxyPass.isEmpty()) {
            showAlertDialog()
            return
        }

        try {
            // Guardar configuración
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                putString("proxy_ip", PROXY_HOST)
                putString("proxy_port", PROXY_PORT)
                putString(KEY_PROXY_USER, proxyUser)
                putString(KEY_PROXY_PASS, proxyPass)
                putBoolean(KEY_PROXY_CONFIGURED, true)
                apply()
            }

            // Mostrar diálogo de reglas antes de continuar
            showRulesDialog()

        } catch (e: Exception) {
            Toast.makeText(this, "Error al guardar la configuración: ${e.message}", 
                         Toast.LENGTH_LONG).show()
        }
    }

    private fun showAlertDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_hospital_alert, null)
        
        val dialog = AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Entendido", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setTextColor(getColor(R.color.purple_500))
        }
        
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
        dialog.show()
    }

    private fun showExitDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_exit_warning, null)
        
        val dialog = AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Salir") { _, _ -> super.onBackPressed() }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            positiveButton.setTextColor(getColor(R.color.red_500))
            negativeButton.setTextColor(getColor(R.color.purple_500))
        }
        
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
        dialog.show()
    }

    private fun showRulesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_hospital_rules, null)
        
        val dialog = AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Acepto") { _, _ ->
                proceedToMainActivity()
            }
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setTextColor(getColor(R.color.purple_500))
        }
        
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
        dialog.show()
    }

    private fun proceedToMainActivity() {
        val isFirstTime = intent.getBooleanExtra("isFirstTime", true)
        if (isFirstTime) {
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                              Intent.FLAG_ACTIVITY_NEW_TASK or 
                              Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(mainIntent)
        } else {
            setResult(Activity.RESULT_OK)
        }
        finish()
    }

    override fun onBackPressed() {
        val isFirstTime = intent.getBooleanExtra("isFirstTime", true)
        if (isFirstTime) {
            showExitDialog()
        } else {
            super.onBackPressed()
        }
    }

    private fun testProxyAuthentication() {
        val proxyUser = proxyUserEdit.text.toString()
        val proxyPass = proxyPassEdit.text.toString()

        if (proxyUser.isEmpty() || proxyPass.isEmpty()) {
            Toast.makeText(this, "Por favor ingrese sus credenciales primero", Toast.LENGTH_SHORT).show()
            return
        }

        // Mostrar progreso
        val progressDialog = AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setView(R.layout.dialog_testing_proxy)
            .setCancelable(false)
            .create()
        progressDialog.show()

        // Realizar prueba en un hilo secundario
        Thread {
            try {
                System.setProperty("http.proxyHost", PROXY_HOST)
                System.setProperty("http.proxyPort", PROXY_PORT)
                
                val authenticator = object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(proxyUser, proxyPass.toCharArray())
                    }
                }
                Authenticator.setDefault(authenticator)

                val url = URL(TEST_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val response = connection.inputStream.bufferedReader().use { it.readText() }

                runOnUiThread {
                    progressDialog.dismiss()
                    if (response.trim() == EXPECTED_RESPONSE) {
                        showSuccessDialog()
                    } else {
                        showErrorDialog("La respuesta del servidor no es la esperada")
                    }
                }
            } catch (e: Exception) {
                Logger.e("Error en prueba de proxy: ${e.message}", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    showErrorDialog("Error de autenticación: ${e.message}")
                }
            }
        }.start()
    }

    private fun showSuccessDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_test_success, null)
        
        AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setView(dialogView)
            .setPositiveButton("Aceptar", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.purple_500))
                }
                window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
                show()
            }
    }

    private fun showErrorDialog(message: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_test_error, null)
        dialogView.findViewById<TextView>(R.id.errorMessage).text = message
        
        AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setView(dialogView)
            .setPositiveButton("Entendido", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.red_500))
                }
                window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
                show()
            }
    }

    private fun setupBiometric() {
        val executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    // Mostrar la contraseña temporalmente
                    proxyPassEdit.transformationMethod = null
                    // Cambiar el ícono
                    proxyPassLayout.setEndIconDrawable(R.drawable.ic_visibility)
                    
                    // Ocultar después de 5 segundos
                    Handler(Looper.getMainLooper()).postDelayed({
                        proxyPassEdit.transformationMethod = PasswordTransformationMethod.getInstance()
                        proxyPassLayout.setEndIconDrawable(R.drawable.ic_visibility_off)
                    }, 5000)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(
                        this@ProxyConfigActivity,
                        "Error de autenticación: $errString",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación requerida")
            .setSubtitle("Confirme su identidad para ver la contraseña")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
    }

    private fun showBiometricPrompt() {
        biometricPrompt.authenticate(promptInfo)
    }
}
