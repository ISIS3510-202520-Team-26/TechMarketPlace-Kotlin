package com.techmarketplace

import android.app.Application
import android.content.Context

class TMApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppHolder.appContext = applicationContext
    }
}

object AppHolder {
    lateinit var appContext: Context
        internal set
}
