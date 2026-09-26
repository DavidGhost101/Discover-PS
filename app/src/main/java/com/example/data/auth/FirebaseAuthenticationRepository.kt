package com.example.data.auth

import android.util.Log
import com.example.data.firebase.FirebaseProvider
import com.example.data.model.User
import com.example.data.model.UserRole
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseAuthenticationRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthenticationRepository {

    companion object {
        private const val TAG = "FirebaseAuthRepo"
    }

    private val usersCollection = firestore.collection("users")

    override val authState: Flow<User?> = callbackFlow {
        if (!FirebaseProvider.isRealFirebaseConfigured) {
            // When real cloud config is pending, do not block or reset local user
            awaitClose { }
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser == null) {
                trySend(null)
            } else {
                trySend(
                    User(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email.orEmpty(),
                        phoneNumber = firebaseUser.phoneNumber.orEmpty(),
                        displayName = firebaseUser.displayName.orEmpty()
                    )
                )
            }
        }

        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<User> {
        val trimmedEmail = email.trim()
        val trimmedPass = password.trim()

        if (trimmedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Email address is required."))
        }
        if (trimmedPass.isBlank()) {
            return Result.failure(IllegalArgumentException("Password is required."))
        }

        // If real Firebase project credentials are provided via google-services.json, use live Firebase Auth
        if (FirebaseProvider.isRealFirebaseConfigured) {
            return try {
                val result = auth.signInWithEmailAndPassword(trimmedEmail, trimmedPass).await()
                val firebaseUser = result.user
                    ?: return Result.failure(IllegalStateException("Authentication failed."))

                getUser(firebaseUser.uid)
            } catch (e: Exception) {
                Log.w(TAG, "Live Firebase sign-in notice: ${e.message}")
                Result.failure(Exception(e.message ?: "Authentication failed."))
            }
        }

        // Offline / Sandbox Mode: authenticate instantly without invoking invalid Recaptcha network request
        val roleStr = when {
            trimmedEmail.contains("admin", ignoreCase = true) -> UserRole.ADMIN.name
            trimmedEmail.contains("teacher", ignoreCase = true) -> UserRole.TEACHER.name
            trimmedEmail.contains("parent", ignoreCase = true) -> UserRole.PARENT.name
            trimmedEmail.contains("staff", ignoreCase = true) -> UserRole.STAFF.name
            else -> UserRole.STUDENT.name
        }

        val displayName = when (roleStr) {
            UserRole.ADMIN.name -> "Principal Raymond Peters"
            UserRole.TEACHER.name -> "Mrs. Nomvula Khumalo"
            UserRole.PARENT.name -> "Mr. Bongani Dlamini"
            UserRole.STAFF.name -> "Sister Martha (Admin Clinic)"
            else -> "Siyabonga Dlamini"
        }

        val fallbackUid = "demo_uid_${roleStr.lowercase()}"
        val user = User(
            uid = fallbackUid,
            email = trimmedEmail,
            displayName = displayName,
            role = roleStr,
            schoolId = "discovery-primary",
            active = true,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        try {
            usersCollection.document(fallbackUid).set(user)
        } catch (_: Exception) {}

        return Result.success(user)
    }

    override suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String,
        role: String,
        schoolId: String
    ): Result<User> {
        val trimmedEmail = email.trim()
        val trimmedPass = password.trim()
        val trimmedName = displayName.trim()

        if (trimmedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Email address is required."))
        }
        if (trimmedPass.length < 6) {
            return Result.failure(IllegalArgumentException("Password must contain at least 6 characters."))
        }
        if (schoolId.isBlank()) {
            return Result.failure(IllegalArgumentException("School ID is required."))
        }

        // Prevent self-registering as ADMIN
        val validatedRole = if (role.equals("ADMIN", ignoreCase = true)) {
            UserRole.STUDENT.name
        } else {
            role.uppercase()
        }

        if (FirebaseProvider.isRealFirebaseConfigured) {
            return try {
                val result = auth.createUserWithEmailAndPassword(trimmedEmail, trimmedPass).await()
                val firebaseUser = result.user
                    ?: return Result.failure(IllegalStateException("Unable to create account."))

                val user = User(
                    uid = firebaseUser.uid,
                    email = trimmedEmail,
                    displayName = trimmedName,
                    role = validatedRole,
                    schoolId = schoolId,
                    active = true,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )

                usersCollection.document(firebaseUser.uid).set(user).await()
                Result.success(user)
            } catch (e: Exception) {
                Log.w(TAG, "Live createUser error: ${e.message}")
                Result.failure(Exception(e.message ?: "Unable to create account."))
            }
        }

        // Local registration mode
        val newUid = UUID.randomUUID().toString()
        val newUser = User(
            uid = newUid,
            email = trimmedEmail,
            displayName = trimmedName,
            role = validatedRole,
            schoolId = schoolId,
            active = true,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        try {
            usersCollection.document(newUid).set(newUser)
        } catch (_: Exception) {}

        return Result.success(newUser)
    }

    override suspend fun sendPhoneVerification(
        phoneNumber: String
    ): Result<Unit> {
        return if (phoneNumber.isBlank()) {
            Result.failure(IllegalArgumentException("Phone number is required."))
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun verifyPhoneCode(
        verificationId: String,
        code: String
    ): Result<User> {
        if (verificationId.isBlank()) {
            return Result.failure(IllegalArgumentException("Verification ID is missing."))
        }
        if (code.length != 6) {
            return Result.failure(IllegalArgumentException("Enter the 6-digit verification code."))
        }

        if (FirebaseProvider.isRealFirebaseConfigured) {
            return try {
                val credential = PhoneAuthProvider.getCredential(verificationId, code)
                val result = auth.signInWithCredential(credential).await()
                val firebaseUser = result.user
                    ?: return Result.failure(IllegalStateException("Phone authentication failed."))

                val existingDoc = usersCollection.document(firebaseUser.uid).get().await()
                if (existingDoc.exists()) {
                    val user = existingDoc.toObject(User::class.java)
                    if (user != null) {
                        return Result.success(user)
                    }
                }

                val newUser = User(
                    uid = firebaseUser.uid,
                    phoneNumber = firebaseUser.phoneNumber.orEmpty(),
                    displayName = firebaseUser.displayName.orEmpty().ifBlank { "Mobile User" },
                    role = UserRole.PARENT.name,
                    schoolId = "discovery-primary",
                    active = true,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )

                usersCollection.document(firebaseUser.uid).set(newUser).await()
                Result.success(newUser)
            } catch (e: Exception) {
                Log.w(TAG, "Live phone verification failed: ${e.message}")
                Result.failure(Exception(e.message ?: "Phone verification failed."))
            }
        }

        // Local demo phone user
        val demoPhoneUser = User(
            uid = "phone_user_demo",
            phoneNumber = "+27821234567",
            displayName = "Mobile Guardian",
            role = UserRole.PARENT.name,
            schoolId = "discovery-primary",
            active = true,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        return Result.success(demoPhoneUser)
    }

    override suspend fun getCurrentUser(): Result<User?> {
        return try {
            val firebaseUser = auth.currentUser ?: return Result.success(null)
            getUser(firebaseUser.uid)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to retrieve user."))
        }
    }

    override suspend fun getUserRole(): Result<String?> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.success(null)
            val snapshot = usersCollection.document(uid).get().await()
            if (!snapshot.exists()) {
                return Result.success(null)
            }
            Result.success(snapshot.getString("role")?.uppercase())
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to retrieve role."))
        }
    }

    override suspend fun signOut() {
        try {
            if (FirebaseProvider.isRealFirebaseConfigured) {
                auth.signOut()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error: ${e.message}")
        }
    }

    override fun getFirebaseUid(): String? {
        return auth.currentUser?.uid
    }

    private suspend fun getUser(uid: String): Result<User> {
        val snapshot = usersCollection.document(uid).get().await()
        if (!snapshot.exists()) {
            return Result.failure(IllegalStateException("User profile does not exist."))
        }

        val user = snapshot.toObject(User::class.java)
        return if (user != null) {
            Result.success(user)
        } else {
            Result.failure(IllegalStateException("Invalid Firestore user profile."))
        }
    }
}
