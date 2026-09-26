package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

object FirebaseProvider {
    private const val TAG = "FirebaseProvider"
    private var initialized = false

    var isRealFirebaseConfigured: Boolean = false
        private set

    fun init(context: Context) {
        if (initialized) return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                isRealFirebaseConfigured = false
                val options = FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.discoveryprimary.kdpmsx")
                    .setApiKey("AIzaSyDiscoveryPrimaryDemoKeyForLocalSandbox123")
                    .setProjectId("discovery-primary-lms")
                    .build()
                FirebaseApp.initializeApp(context, options)
                Log.d(TAG, "Firebase initialized with local fallback options (google-services.json not found).")
            } else {
                val app = FirebaseApp.getInstance()
                val key = app.options.apiKey
                isRealFirebaseConfigured = !key.contains("DemoKey") && !key.contains("FakeKey") && key.isNotBlank()
                Log.d(TAG, "Firebase initialized automatically by Google Services plugin. Real config: $isRealFirebaseConfigured")
            }

            // Enable offline persistence settings
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
                firestore.firestoreSettings = settings
            } catch (e: Exception) {
                Log.w(TAG, "Firestore settings notice: ${e.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase initialization notice: ${e.message}")
        } finally {
            initialized = true
        }
    }

    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()
}
