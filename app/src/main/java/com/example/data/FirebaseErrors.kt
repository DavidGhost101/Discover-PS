package com.example.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

/** Turns Firebase exceptions into messages that are safe and useful to show users. */
fun friendlyError(e: Throwable?): String = when (e) {
    null -> "Something went wrong. Please try again."
    is FirebaseAuthWeakPasswordException -> e.reason ?: "The password is too weak."
    is FirebaseAuthUserCollisionException -> "An account already exists with this email address."
    is FirebaseAuthInvalidUserException -> "This account does not exist or has been disabled."
    is FirebaseAuthInvalidCredentialsException -> "Incorrect email, password or verification code."
    is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a moment and try again."
    is FirebaseNetworkException -> "Network error. Check your connection and try again."
    is FirebaseAuthException -> e.message ?: "Authentication failed."
    is FirebaseFirestoreException -> when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Access denied: your account is not permitted to do this."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "The school database is unreachable. Check your connection."
        FirebaseFirestoreException.Code.NOT_FOUND -> "The record no longer exists."
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Your session has expired. Please sign in again."
        else -> "Database error: ${e.message}"
    }
    is IllegalArgumentException -> e.message ?: "Invalid input."
    is IllegalStateException -> e.message ?: "Something went wrong. Please try again."
    else -> e.message ?: "Something went wrong. Please try again."
}
