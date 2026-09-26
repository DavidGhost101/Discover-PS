package com.example

import android.app.Application
import com.example.data.firebase.FirebaseProvider

class DiscoveryPrimaryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseProvider.init(this)
    }
}
