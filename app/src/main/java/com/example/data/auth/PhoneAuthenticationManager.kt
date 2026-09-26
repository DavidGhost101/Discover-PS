package com.example.data.auth

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

fun normalizeSouthAfricanPhone(phone: String): String {
    val cleaned = phone
        .replace(" ", "")
        .replace("-", "")
        .replace("(", "")
        .replace(")", "")

    return when {
        cleaned.startsWith("+27") -> cleaned
        cleaned.startsWith("27") -> "+$cleaned"
        cleaned.startsWith("0") -> "+27${cleaned.drop(1)}"
        else -> cleaned
    }
}

class PhoneAuthenticationManager(
    private val auth: FirebaseAuth
) {
    fun sendCode(
        activity: Activity,
        phoneNumber: String,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        val normalized = normalizeSouthAfricanPhone(phoneNumber)
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(normalized)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }
}
