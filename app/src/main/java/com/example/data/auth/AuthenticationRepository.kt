package com.example.data.auth

import com.example.data.model.User
import com.example.data.model.UserRole
import com.google.firebase.auth.PhoneAuthCredential
import kotlinx.coroutines.flow.StateFlow

/** Where the signed-in person currently stands. Derived from Firebase Auth + /users/{uid}. */
sealed interface SessionState {
    data object Initializing : SessionState
    data object NotConfigured : SessionState
    data object SignedOut : SessionState
    data class VerifyEmail(val email: String) : SessionState
    data class NoProfile(val uid: String) : SessionState
    data class Ready(val user: User) : SessionState
    data class Failed(val message: String) : SessionState
}

interface AuthenticationRepository {
    val session: StateFlow<SessionState>

    suspend fun signInWithEmail(email: String, password: String): Result<Unit>

    /** Self-registration: always creates a PARENT account awaiting administrator approval. */
    suspend fun registerParent(displayName: String, email: String, password: String): Result<Unit>

    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): Result<Unit>

    suspend fun sendPasswordReset(email: String): Result<Unit>

    suspend fun resendEmailVerification(): Result<Unit>

    /** Reloads the Firebase user; returns true when the email address is now verified. */
    suspend fun refreshEmailVerification(): Result<Boolean>

    /**
     * Administrator only: creates a login for someone else and emails them a link to set
     * their password. The role is enforced by the Firestore rules, not by this call.
     */
    suspend fun provisionAccount(email: String, displayName: String, role: UserRole, phoneNumber: String): Result<String>

    fun signOut()

    fun close()
}
