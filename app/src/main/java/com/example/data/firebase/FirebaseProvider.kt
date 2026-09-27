package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Single place where Firebase is configured.
 *
 * Production configuration comes from app/google-services.json (processed by the
 * google-services Gradle plugin). When that file is missing the app does NOT fall back
 * to a fake project: [isConfigured] stays false and the UI explains what to set up.
 */
object FirebaseProvider {
    private const val TAG = "FirebaseProvider"
    private const val PROVISIONING_APP = "account-provisioning"

    private var initialized = false
    private var emulatorHost: String? = null

    var isConfigured: Boolean = false
        private set

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            isConfigured = FirebaseApp.getApps(context).isNotEmpty()
            if (isConfigured) configureFirestore()
            else Log.w(TAG, "Firebase is not configured: add app/google-services.json from the Firebase console.")
        } catch (e: Exception) {
            isConfigured = false
            Log.e(TAG, "Firebase initialization failed: ${e.message}")
        }
    }

    /**
     * Test-only entry point: connects the app to the local Firebase Emulator Suite
     * (Auth on 9099, Firestore on 8080). Must run before any Firebase call.
     */
    @Synchronized
    fun initForEmulator(context: Context, host: String, projectId: String = "demo-discovery-primary") {
        if (emulatorHost == host) return
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setProjectId(projectId)
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("emulator-only-api-key")
                .build()
            FirebaseApp.initializeApp(context, options)
        }
        emulatorHost = host
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
        FirebaseFirestore.getInstance().useEmulator(host, 8080)
        FirebaseAuth.getInstance().useEmulator(host, 9099)
        FirebaseFunctions.getInstance().useEmulator(host, 5001)
        isConfigured = true
        initialized = true
    }

    private fun configureFirestore() {
        try {
            FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
        } catch (e: IllegalStateException) {
            // Settings can only be applied before first use; keep the existing ones.
            Log.w(TAG, "Firestore settings already applied: ${e.message}")
        }
    }

    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /**
     * A second FirebaseAuth instance used by administrators to create accounts for other
     * people without signing the administrator out of the primary instance.
     */
    fun provisioningAuth(context: Context): FirebaseAuth {
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == PROVISIONING_APP }
            ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, PROVISIONING_APP)
        val provisioning = FirebaseAuth.getInstance(app)
        emulatorHost?.let { host ->
            try {
                provisioning.useEmulator(host, 9099)
            } catch (_: IllegalStateException) {
                // Already connected to the emulator.
            }
        }
        return provisioning
    }
}
