package com.shilapi.xcertplay

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex

/**
 * KitKat (API 19) needs legacy multidex — the app exceeds 64K method references with
 * BouncyCastle and jmdns on board. MultiDex.install must run before any second-dex class loads.
 */
class DiPlayApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
