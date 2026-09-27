package com.example.data.auth

import android.content.Context
import com.example.data.firebase.FirebaseProvider
import com.example.data.friendlyError
import com.example.data.model.SCHOOL_ID
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.domain.FormValidation
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class FirebaseAuthenticationRepository(
    private val context: Context
) : AuthenticationRepository {

    private val configured = FirebaseProvider.isConfigured
    private val auth: FirebaseAuth? = if (configured) FirebaseProvider.auth else null
    private val users by lazy { FirebaseProvider.firestore.collection(USERS) }

    private val _session = MutableStateFlow<SessionState>(
        if (configured) SessionState.Initializing else SessionState.NotConfigured
    )
    override val session: StateFlow<SessionState> = _session.asStateFlow()

    private var profileListener: ListenerRegistration? = null
    private val authListener = FirebaseAuth.AuthStateListener { evaluate(it.currentUser) }

    init {
        auth?.addAuthStateListener(authListener)
    }

    /** Recomputes the session for [firebaseUser]; the profile document is watched live so
     *  role changes and deactivation by an administrator apply immediately. */
    private fun evaluate(firebaseUser: FirebaseUser?) {
        profileListener?.remove()
        profileListener = null
        if (firebaseUser == null) {
            _session.value = SessionState.SignedOut
            return
        }
        if (requiresEmailVerification(firebaseUser)) {
            _session.value = SessionState.VerifyEmail(firebaseUser.email.orEmpty())
            return
        }
        val uid = firebaseUser.uid
        profileListener = users.document(uid).addSnapshotListener { snapshot, error ->
            if (auth?.currentUser?.uid != uid) return@addSnapshotListener
            _session.value = when {
                error != null -> SessionState.Failed(friendlyError(error))
                snapshot == null || !snapshot.exists() -> SessionState.NoProfile(uid)
                else -> snapshot.toObject(User::class.java)?.copy(uid = uid)
                    ?.let { SessionState.Ready(it) }
                    ?: SessionState.Failed("This account's profile is invalid. Contact the school administrator.")
            }
        }
    }

    private fun requiresEmailVerification(user: FirebaseUser): Boolean =
        !user.isEmailVerified && user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }

    private fun requireAuth(): FirebaseAuth =
        auth ?: throw IllegalStateException(NOT_CONFIGURED_MESSAGE)

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        FormValidation.email(email)?.let { throw IllegalArgumentException(it) }
        if (password.isEmpty()) throw IllegalArgumentException("Password is required.")
        requireAuth().signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }.mapError()

    override suspend fun registerParent(displayName: String, email: String, password: String): Result<Unit> = runCatching {
        FormValidation.required(displayName, "Full name")?.let { throw IllegalArgumentException(it) }
        FormValidation.email(email)?.let { throw IllegalArgumentException(it) }
        FormValidation.password(password)?.let { throw IllegalArgumentException(it) }

        val result = requireAuth().createUserWithEmailAndPassword(email.trim(), password).await()
        val firebaseUser = result.user ?: throw IllegalStateException("Unable to create the account.")
        try {
            firebaseUser.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(displayName.trim()).build()
            ).await()
            val now = Timestamp.now()
            users.document(firebaseUser.uid).set(
                User(
                    uid = firebaseUser.uid,
                    email = email.trim(),
                    displayName = displayName.trim(),
                    role = FormValidation.selfRegistrationRole.name,
                    schoolId = SCHOOL_ID,
                    active = false,
                    createdAt = now,
                    updatedAt = now
                )
            ).await()
        } catch (e: Exception) {
            // Do not leave a login behind without a profile.
            runCatching { firebaseUser.delete().await() }
            throw e
        }
        firebaseUser.sendEmailVerification().await()
        Unit
    }.mapError()

    override suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): Result<Unit> = runCatching {
        val result = requireAuth().signInWithCredential(credential).await()
        val firebaseUser = result.user ?: throw IllegalStateException("Phone sign-in failed.")
        val profile = users.document(firebaseUser.uid)
        if (!profile.get().await().exists()) {
            val now = Timestamp.now()
            profile.set(
                User(
                    uid = firebaseUser.uid,
                    phoneNumber = firebaseUser.phoneNumber.orEmpty(),
                    displayName = firebaseUser.displayName.orEmpty().ifBlank { firebaseUser.phoneNumber.orEmpty() },
                    role = FormValidation.selfRegistrationRole.name,
                    schoolId = SCHOOL_ID,
                    active = false,
                    createdAt = now,
                    updatedAt = now
                )
            ).await()
        }
        Unit
    }.mapError()

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        FormValidation.email(email)?.let { throw IllegalArgumentException(it) }
        requireAuth().sendPasswordResetEmail(email.trim()).await()
        Unit
    }.mapError()

    override suspend fun resendEmailVerification(): Result<Unit> = runCatching {
        val user = requireAuth().currentUser ?: throw IllegalStateException("Please sign in again.")
        user.sendEmailVerification().await()
        Unit
    }.mapError()

    override suspend fun refreshEmailVerification(): Result<Boolean> = runCatching {
        val firebaseAuth = requireAuth()
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("Please sign in again.")
        user.reload().await()
        val refreshed = firebaseAuth.currentUser
        evaluate(refreshed)
        refreshed?.isEmailVerified == true
    }.mapError()

    override suspend fun provisionAccount(
        email: String,
        displayName: String,
        role: UserRole,
        phoneNumber: String
    ): Result<String> = runCatching {
        FormValidation.email(email)?.let { throw IllegalArgumentException(it) }
        FormValidation.required(displayName, "Name")?.let { throw IllegalArgumentException(it) }
        FormValidation.phone(phoneNumber)?.let { throw IllegalArgumentException(it) }
        val primary = requireAuth()
        if (primary.currentUser == null) throw IllegalStateException("Please sign in again.")

        val provisioning = FirebaseProvider.provisioningAuth(context)
        val created = provisioning.createUserWithEmailAndPassword(email.trim(), randomPassword()).await()
        val newUser = created.user ?: throw IllegalStateException("Unable to create the account.")
        try {
            val now = Timestamp.now()
            // Written with the administrator's credentials; firestore.rules reject this
            // unless the caller is an active ADMIN.
            users.document(newUser.uid).set(
                User(
                    uid = newUser.uid,
                    email = email.trim(),
                    phoneNumber = phoneNumber.trim(),
                    displayName = displayName.trim(),
                    role = role.name,
                    schoolId = SCHOOL_ID,
                    active = true,
                    createdAt = now,
                    updatedAt = now
                )
            ).await()
        } catch (e: Exception) {
            runCatching { newUser.delete().await() }
            throw e
        } finally {
            provisioning.signOut()
        }
        // The new user chooses their own password through this link (also proves the email).
        primary.sendPasswordResetEmail(email.trim()).await()
        newUser.uid
    }.mapError()

    override fun signOut() {
        profileListener?.remove()
        profileListener = null
        auth?.signOut()
        _session.value = if (configured) SessionState.SignedOut else SessionState.NotConfigured
    }

    override fun close() {
        profileListener?.remove()
        profileListener = null
        auth?.removeAuthStateListener(authListener)
    }

    private fun randomPassword(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%"
        val random = SecureRandom()
        return (1..32).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private fun <T> Result<T>.mapError(): Result<T> =
        fold({ Result.success(it) }, { Result.failure(Exception(friendlyError(it), it)) })

    companion object {
        const val USERS = "users"
        const val NOT_CONFIGURED_MESSAGE =
            "Firebase is not configured for this build. Add app/google-services.json and rebuild."
    }
}
