package com.alpha.spendtracker.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages Firebase Authentication state and operations.
 */
class AuthManager {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _user.value = firebaseAuth.currentUser
        }
    }

    fun signOut() {
        auth.signOut()
    }

    val currentUserId: String
        get() = auth.currentUser?.uid ?: "anonymous"

    val isAuthenticated: Boolean
        get() = auth.currentUser != null
}
