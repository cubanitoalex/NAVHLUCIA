package cu.holalinux.navhlucia.utils

import android.webkit.WebView
import java.net.InetSocketAddress
import java.net.Proxy

object ProxyHelper {
    fun applyProxyToWebView(webView: WebView, host: String, port: String, username: String? = null, password: String? = null) {
        // Configurar proxy a nivel de sistema
        System.setProperty("http.proxyHost", host)
        System.setProperty("http.proxyPort", port)
        System.setProperty("https.proxyHost", host)
        System.setProperty("https.proxyPort", port)
        
        if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            System.setProperty("http.proxyUser", username)
            System.setProperty("http.proxyPassword", password)
            System.setProperty("https.proxyUser", username)
            System.setProperty("https.proxyPassword", password)
            
            // Configurar autenticador
            java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                override fun getPasswordAuthentication(): java.net.PasswordAuthentication {
                    return java.net.PasswordAuthentication(username, password.toCharArray())
                }
            })
        }

        // Crear proxy
        val proxyAddress = InetSocketAddress(host, port.toInt())
        val proxy = Proxy(Proxy.Type.HTTP, proxyAddress)

        // Aplicar configuración
        System.setProperty("proxySet", "true")
    }
}
