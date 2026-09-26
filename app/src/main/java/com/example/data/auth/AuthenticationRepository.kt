package com.example.data.auth

import com.example.data.model.User
import kotlinx.coroutines.flow.Flow

interface AuthenticationRepository {
    val authState: Flow<User?>

    suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<User>

    suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String,
        role: String = "STUDENT",
        schoolId: String = "discovery-primary"
    ): Result<User>

    suspend fun sendPhoneVerification(
        phoneNumber: String
    ): Result<Unit>

    suspend fun verifyPhoneCode(
        verificationId: String,
        code: String
    ): Result<User>

    suspend fun getCurrentUser(): Result<User?>

    suspend fun getUserRole(): Result<String?>

    suspend fun signOut()

    fun getFirebaseUid(): String?
}
