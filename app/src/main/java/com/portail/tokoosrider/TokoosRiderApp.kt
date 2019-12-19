package com.portail.tokoosrider

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex

class TokoosRiderApp : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

}