// app/src/main/java/com/techmarketplace/AppHolder.kt
package com.techmarketplace

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.techmarketplace.net.HostRewriteInterceptor
import okhttp3.OkHttpClient

class TMApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        AppHolder.appContext = applicationContext
    }

    // Coil usará este ImageLoader por defecto en toda la app
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor(HostRewriteInterceptor("10.0.2.2", 9000))
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}

object AppHolder {
    lateinit var appContext: Context
        internal set
}
