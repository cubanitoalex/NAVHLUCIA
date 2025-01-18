# Mantener las clases de la aplicación
-keep class cu.holalinux.navhlucia.** { *; }

# Reglas para mantener las clases de AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Reglas para mantener las clases de Material Design
-keep class com.google.android.material.** { *; }

# Mantener los atributos necesarios para reflection
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Mantener las clases utilizadas para el proxy
-keepclassmembers class java.net.Authenticator { *; }
-keepclassmembers class java.net.PasswordAuthentication { *; }

# Reglas para Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Mantener las clases de modelo que uses con Gson
-keep class cu.holalinux.navhlucia.utils.** { *; }
-keepclassmembers class cu.holalinux.navhlucia.utils.** { *; }

# Mantener los genéricos
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Reglas específicas para TypeToken
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation class com.google.gson.reflect.TypeToken

# Reglas para PRDownloader
-keepclassmembers class com.downloader.** { *; }
-keep class com.downloader.** { *; }

# Deshabilitar completamente la ofuscación para DownloadManager
-keep class cu.holalinux.navhlucia.utils.DownloadManager { *; }
-keepclassmembers class cu.holalinux.navhlucia.utils.DownloadManager { *; } 