package com.example.data.auth

import com.example.data.model.User

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
    data object Loading : AuthResult()
    data object SignedOut : AuthResult()
}
