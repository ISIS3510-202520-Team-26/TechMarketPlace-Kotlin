package com.techmarketplace.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

// Context holder global (getter y setter públicos)
object AppHolder {
    lateinit var appContext: Context
}

// Application: configura Coil + OkHttp cache y reescritura de host para emulador
class TMApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        AppHolder.appContext = applicationContext
    }

    override fun newImageLoader(): ImageLoader {
        // Caché HTTP (OkHttp) para respuestas remotas
        val httpCacheDir = File(cacheDir, "img_http_cache")
        val ok = OkHttpClient.Builder()
            // Reescritura simple para emulador: localhost → 10.0.2.2
            .addInterceptor { chain ->
                val req = chain.request()
                val newUrl = req.url.toString()
                    .replace("http://localhost", "http://10.0.2.2")
                    .replace("http://127.0.0.1", "http://10.0.2.2")
                chain.proceed(req.newBuilder().url(newUrl).build())
            }
            // Cache HTTP (100 MB)
            .cache(Cache(httpCacheDir, 100L * 1024 * 1024))
            // Forzar headers de caching largos en endpoints de imagen
            .addNetworkInterceptor { chain ->
                val res = chain.proceed(chain.request())
                val u = chain.request().url.toString()
                val isImage = u.endsWith(".png", ignoreCase = true) ||
                        u.endsWith(".jpg", ignoreCase = true) ||
                        u.endsWith(".jpeg", ignoreCase = true) ||
                        u.contains("/market-images/") ||
                        u.contains("/images/preview")
                if (isImage) {
                    res.newBuilder()
                        .header("Cache-Control", "public, max-age=604800") // 7 días
                        .build()
                } else res
            }
            .build()

        // Caché de disco de Coil (bitmaps transformados)
        val coilDisk = DiskCache.Builder()
            .directory(File(cacheDir, "coil_disk_cache"))
            .maxSizeBytes(150L * 1024 * 1024) // 150 MB
            .build()

        // Caché de memoria de Coil
        val coilMem = MemoryCache.Builder(this)
            .maxSizePercent(0.25) // ~25% de la memoria disponible
            .build()

        // ImageLoader global de Coil
        return ImageLoader.Builder(this)
            .okHttpClient(ok)
            .diskCache(coilDisk)
            .memoryCache(coilMem)
            .respectCacheHeaders(true)
            .allowHardware(false)
            .crossfade(true)
            .build()
    }
}
